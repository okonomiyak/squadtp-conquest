package uk.iwaservice.squadtpconquest.conquest;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import uk.iwaservice.squadtp.squad.ReviveSystem;
import uk.iwaservice.squadtp.squad.Squad;
import uk.iwaservice.squadtp.squad.SquadManager;
import uk.iwaservice.squadtp.squad.TeleportHelper;
import uk.iwaservice.squadtpconquest.Config;
import uk.iwaservice.squadtpconquest.network.ConquestScoreboardPacket;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;
import uk.iwaservice.squadtpconquest.network.NetworkHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative conquest state, persisted with the overworld.
 * Mirrors squadtp's SquadManager pattern: all mutation enters through
 * commands or the once-per-second tick, so there is no packet to spoof.
 */
public class ConquestManager extends SavedData {
    private static final String DATA_NAME = "squadtpconquest_state";

    /** Capture points, keyed by name; insertion order kept for stable HUD/GUI ordering. */
    private final LinkedHashMap<String, CapturePoint> points = new LinkedHashMap<>();
    /** Player UUID -> assigned team (players absent from the map are NEUTRAL). */
    private final Map<UUID, Team> playerTeams = new HashMap<>();
    private int ticketsA;
    private int ticketsB;
    /** CONQUEST (capture points, tickets drain) or TDM (no points, tickets count kills up). */
    private GameMode mode = GameMode.CONQUEST;

    private RoundState state = RoundState.WAITING;
    /** Seconds since /conquest start, ticked only while IN_PROGRESS. */
    private int roundElapsedSeconds;
    /** Seconds since the round ended, ticked only while ENDED. */
    private int resultElapsedSeconds;
    /** Winner of the last round; null means a draw or no round has ended yet. */
    @Nullable
    private Team lastWinner;

    @Nullable
    private ResourceKey<Level> spawnADim;
    @Nullable
    private BlockPos spawnAPos;
    @Nullable
    private ResourceKey<Level> spawnBDim;
    @Nullable
    private BlockPos spawnBPos;

    /** Transient: seconds since the last ticket bleed. */
    private int bleedCounter;
    /** Seconds left in the pre-round countdown, ticked only while STARTING. Not persisted. */
    private int countdownSecondsRemaining;
    /** Round-scoped kill/death/assist/revive counters, reset on /conquest start. */
    private final Map<UUID, PlayerScore> scores = new HashMap<>();
    /** Cumulative kill/death/assist/revive counters across all rounds; never cleared. */
    private final Map<UUID, PlayerScore> lifetimeScores = new HashMap<>();
    /** Transient: players currently known to be downed, for revive-transition detection. */
    private final Set<UUID> trackedDowned = new HashSet<>();

    public static ConquestManager get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ConquestManager::load, ConquestManager::new, DATA_NAME);
    }

    public ConquestManager() {
    }

    // --- accessors ---

    public Collection<CapturePoint> getPoints() {
        return points.values();
    }

    public boolean hasPoints() {
        return !points.isEmpty();
    }

    @Nullable
    public CapturePoint getPoint(String name) {
        return points.get(name);
    }

    public RoundState getState() {
        return state;
    }

    /** True exactly while capture/ticket logic is running. */
    public boolean isActive() {
        return state == RoundState.IN_PROGRESS;
    }

    @Nullable
    public Team getLastWinner() {
        return lastWinner;
    }

    /** Seconds left in the pre-round countdown; -1 if not currently STARTING. */
    public int remainingStartCountdown() {
        return state == RoundState.STARTING ? countdownSecondsRemaining : -1;
    }

    /** Seconds left before the round time limit ends it; -1 if unlimited or not running. */
    public int remainingRoundSeconds() {
        int limit = Config.ROUND_TIME_LIMIT_SECONDS.get();
        if (state != RoundState.IN_PROGRESS || limit <= 0) {
            return -1;
        }
        return Math.max(0, limit - roundElapsedSeconds);
    }

    /** Seconds left before the result auto-resets to WAITING; -1 if not applicable. */
    public int remainingResultSeconds() {
        if (state != RoundState.ENDED || !Config.AUTO_RESET_AFTER_RESULT.get()) {
            return -1;
        }
        return Math.max(0, Config.RESULT_DISPLAY_SECONDS.get() - resultElapsedSeconds);
    }

    public Team teamOf(UUID player) {
        return playerTeams.getOrDefault(player, Team.NEUTRAL);
    }

    public GameMode getMode() {
        return mode;
    }

    /** Changes the ruleset for the next round. Rejected while a round is running or showing a result. */
    public boolean setMode(GameMode newMode) {
        if (state != RoundState.WAITING) {
            return false;
        }
        mode = newMode;
        setDirty();
        return true;
    }

    public int tickets(Team team) {
        return team == Team.A ? ticketsA : team == Team.B ? ticketsB : 0;
    }

    private int onlineCount(MinecraftServer server, Team team) {
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (teamOf(player.getUUID()) == team) {
                count++;
            }
        }
        return count;
    }

    private int countOwned(Team team) {
        int count = 0;
        for (CapturePoint point : points.values()) {
            if (point.getOwner() == team) {
                count++;
            }
        }
        return count;
    }

    // --- scoring ---

    /**
     * Records the kill and, in TDM, credits the killer's team a point toward
     * the kill limit (reusing the ticket counters as an up-counting score
     * since the HUD/GUI/scoreboard already render them generically).
     */
    public void recordKill(MinecraftServer server, UUID player) {
        scoreOf(player).kills++;
        lifetimeScoreOf(player).kills++;
        setDirty();
        if (mode == GameMode.TDM && state == RoundState.IN_PROGRESS) {
            Team team = teamOf(player);
            if (team == Team.A) {
                ticketsA++;
            } else if (team == Team.B) {
                ticketsB++;
            } else {
                return;
            }
            int limit = Config.TDM_KILL_LIMIT.get();
            if (limit > 0 && tickets(team) >= limit) {
                endRound(server, team);
            }
        }
    }

    public void recordDeath(UUID player) {
        scoreOf(player).deaths++;
        lifetimeScoreOf(player).deaths++;
        setDirty();
    }

    public void recordAssist(UUID player) {
        scoreOf(player).assists++;
        lifetimeScoreOf(player).assists++;
        setDirty();
    }

    public void recordRevive(UUID player) {
        scoreOf(player).revives++;
        lifetimeScoreOf(player).revives++;
        setDirty();
    }

    private PlayerScore scoreOf(UUID player) {
        return scores.computeIfAbsent(player, k -> new PlayerScore());
    }

    private PlayerScore lifetimeScoreOf(UUID player) {
        return lifetimeScores.computeIfAbsent(player, k -> new PlayerScore());
    }

    /** Weighted total: kills/assists/revives per the scoreboard config, deaths don't subtract. */
    public int totalScore(UUID player) {
        return weightedScore(scores.get(player));
    }

    /** Same weighting as {@link #totalScore}, but over the cross-round lifetime counters. */
    public int totalLifetimeScore(UUID player) {
        return weightedScore(lifetimeScores.get(player));
    }

    private static int weightedScore(@Nullable PlayerScore s) {
        if (s == null) {
            return 0;
        }
        return s.kills * Config.SCORE_PER_KILL.get()
                + s.assists * Config.SCORE_PER_ASSIST.get()
                + s.revives * Config.SCORE_PER_REVIVE.get();
    }

    /**
     * Detects downed-to-alive transitions (squadtp exposes no revive-completion
     * event) and credits whichever player was last seen reviving that target,
     * per {@link ReviveAttribution}.
     */
    private void checkRevives(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            boolean downedNow = ReviveSystem.isDowned(uuid);
            if (downedNow) {
                trackedDowned.add(uuid);
                continue;
            }
            if (!trackedDowned.remove(uuid)) {
                continue;
            }
            UUID reviver = ReviveAttribution.take(uuid);
            if (reviver != null && player.isAlive() && teamOf(uuid).isCombatant()
                    && teamOf(reviver) == teamOf(uuid)) {
                recordRevive(reviver);
            }
        }
    }

    // --- setup operations (commands) ---

    /**
     * Assigns the conquest team and mirrors it onto a dedicated vanilla
     * scoreboard team ("conquest_a"/"conquest_b": friendly fire off, colored
     * to match) so squadtp's requireSameTeam keeps squads confined to one
     * conquest side, and so team-colored nameplates/glow come for free.
     */
    public void joinTeam(ServerPlayer player, Team team) {
        playerTeams.put(player.getUUID(), team);
        setDirty();
        syncVanillaTeam(player, team);
    }

    /**
     * Randomly splits every online player who isn't on the admin team into
     * Team A / Team B as evenly as possible. Since a shuffle can freely split
     * up existing squads across the two new teams, every squad touched by it
     * is disbanded first and fresh same-team squads are formed afterward
     * (chunked to squadtp's maxSquadSize) so nobody needs to manually reform.
     * Returns the number of players reassigned.
     */
    public int shuffleTeams(MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (teamOf(player.getUUID()) != Team.ADMIN) {
                players.add(player);
            }
        }
        Collections.shuffle(players);

        SquadManager squadManager = SquadManager.get(server);
        disbandSquadsOf(server, squadManager, players);

        List<ServerPlayer> teamA = new ArrayList<>();
        List<ServerPlayer> teamB = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            Team team = i % 2 == 0 ? Team.A : Team.B;
            joinTeam(player, team);
            (team == Team.A ? teamA : teamB).add(player);
        }

        formSquads(server, squadManager, teamA);
        formSquads(server, squadManager, teamB);
        return players.size();
    }

    private static void disbandSquadsOf(MinecraftServer server, SquadManager squadManager, List<ServerPlayer> players) {
        Set<Squad> squads = new HashSet<>();
        for (ServerPlayer player : players) {
            Squad squad = squadManager.getSquadOf(player.getUUID());
            if (squad != null) {
                squads.add(squad);
            }
        }
        for (Squad squad : squads) {
            squadManager.disband(server, squad);
        }
    }

    /** Groups same-team players into new squads of at most squadtp's maxSquadSize; lone leftovers stay squadless. */
    private static void formSquads(MinecraftServer server, SquadManager squadManager, List<ServerPlayer> teamPlayers) {
        int maxSize = uk.iwaservice.squadtp.Config.MAX_SQUAD_SIZE.get();
        for (int i = 0; i < teamPlayers.size(); i += maxSize) {
            List<ServerPlayer> chunk = teamPlayers.subList(i, Math.min(i + maxSize, teamPlayers.size()));
            if (chunk.size() < 2) {
                continue;
            }
            Squad squad = squadManager.create(chunk.get(0));
            for (int j = 1; j < chunk.size(); j++) {
                squadManager.join(server, squad, chunk.get(j));
            }
        }
    }

    private static void syncVanillaTeam(ServerPlayer player, Team team) {
        Scoreboard scoreboard = player.server.getScoreboard();
        String playerName = player.getGameProfile().getName();

        PlayerTeam current = scoreboard.getPlayersTeam(playerName);
        if (current != null && isConquestTeam(current) && current != vanillaTeam(scoreboard, team)) {
            scoreboard.removePlayerFromTeam(playerName, current);
        }

        PlayerTeam target = getOrCreateVanillaTeam(scoreboard, team);
        scoreboard.addPlayerToTeam(playerName, target);
    }

    private static PlayerTeam vanillaTeam(Scoreboard scoreboard, Team team) {
        return scoreboard.getPlayerTeam(vanillaTeamName(team));
    }

    private static PlayerTeam getOrCreateVanillaTeam(Scoreboard scoreboard, Team team) {
        String name = vanillaTeamName(team);
        PlayerTeam existing = scoreboard.getPlayerTeam(name);
        if (existing != null) {
            return existing;
        }
        PlayerTeam created = scoreboard.addPlayerTeam(name);
        created.setColor(team.color());
        created.setAllowFriendlyFire(false);
        return created;
    }

    private static boolean isConquestTeam(PlayerTeam team) {
        return team.getName().equals(vanillaTeamName(Team.A)) || team.getName().equals(vanillaTeamName(Team.B))
                || team.getName().equals(vanillaTeamName(Team.ADMIN));
    }

    private static String vanillaTeamName(Team team) {
        return "conquest_" + team.key();
    }

    /** Adds a new point, or relocates/resizes an existing one with the same name. */
    public void setPoint(ServerLevel level, String name, BlockPos pos, int radius) {
        CapturePoint existing = points.get(name);
        if (existing != null) {
            ServerLevel oldLevel = level.getServer().getLevel(existing.getDimension());
            if (oldLevel != null) {
                FlagPole.remove(oldLevel, existing);
            }
        }
        CapturePoint point = new CapturePoint(name, level.dimension(), pos, radius);
        points.put(name, point);
        FlagPole.build(level, point);
        setDirty();
    }

    /** Removes a point and clears its flag blocks. False if no point has that name. */
    public boolean removePoint(MinecraftServer server, String name) {
        CapturePoint point = points.remove(name);
        if (point == null) {
            return false;
        }
        ServerLevel level = server.getLevel(point.getDimension());
        if (level != null) {
            FlagPole.remove(level, point);
        }
        setDirty();
        return true;
    }

    public void setSpawn(ServerLevel level, Team team, BlockPos pos) {
        if (team == Team.A) {
            spawnADim = level.dimension();
            spawnAPos = pos.immutable();
        } else if (team == Team.B) {
            spawnBDim = level.dimension();
            spawnBPos = pos.immutable();
        }
        setDirty();
    }

    /** Outcome of a /conquest start attempt, used to pick the right failure message. */
    public enum StartResult { OK, ALREADY_RUNNING, RESULT_PENDING, NO_POINT, TEAM_A_EMPTY, TEAM_B_EMPTY }

    /**
     * Validates, resets all points/tickets, teleports teams, then either goes
     * straight IN_PROGRESS (countdown disabled) or STARTING for a few seconds
     * of "Get Ready" title countdown before capture/ticket logic begins.
     */
    public StartResult start(MinecraftServer server) {
        if (state == RoundState.STARTING || state == RoundState.IN_PROGRESS) {
            return StartResult.ALREADY_RUNNING;
        }
        if (state == RoundState.ENDED) {
            return StartResult.RESULT_PENDING;
        }
        if (mode == GameMode.CONQUEST && points.isEmpty()) {
            return StartResult.NO_POINT;
        }
        if (onlineCount(server, Team.A) == 0) {
            return StartResult.TEAM_A_EMPTY;
        }
        if (onlineCount(server, Team.B) == 0) {
            return StartResult.TEAM_B_EMPTY;
        }

        if (mode == GameMode.CONQUEST) {
            for (CapturePoint point : points.values()) {
                point.reset();
                ServerLevel level = server.getLevel(point.getDimension());
                if (level != null) {
                    FlagPole.build(level, point);
                }
            }
        }
        ticketsA = mode == GameMode.CONQUEST ? Config.STARTING_TICKETS.get() : 0;
        ticketsB = mode == GameMode.CONQUEST ? Config.STARTING_TICKETS.get() : 0;
        bleedCounter = 0;
        roundElapsedSeconds = 0;
        resultElapsedSeconds = 0;
        lastWinner = null;
        scores.clear();
        trackedDowned.clear();
        teleportToSpawns(server);

        int countdown = Config.START_COUNTDOWN_SECONDS.get();
        if (countdown <= 0) {
            state = RoundState.IN_PROGRESS;
            setDirty();
            announceStarted(server);
        } else {
            state = RoundState.STARTING;
            countdownSecondsRemaining = countdown;
            setDirty();
            broadcastTitle(server,
                    Component.literal(String.valueOf(countdown)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.translatable("conquest.title.get_ready"));
        }
        return StartResult.OK;
    }

    private void announceStarted(MinecraftServer server) {
        if (mode == GameMode.TDM) {
            int limit = Config.TDM_KILL_LIMIT.get();
            String limitText = limit > 0 ? String.valueOf(limit) : "∞";
            broadcast(server, Component.translatable("conquest.msg.started_tdm", limitText).withStyle(ChatFormatting.GOLD));
            broadcastTitle(server, Component.translatable("conquest.title.started_tdm").withStyle(ChatFormatting.GOLD),
                    Component.translatable("conquest.title.started_tdm_sub", limitText));
            return;
        }
        broadcast(server, Component.translatable("conquest.msg.started", Config.STARTING_TICKETS.get())
                .withStyle(ChatFormatting.GOLD));
        broadcastTitle(server, Component.translatable("conquest.title.started").withStyle(ChatFormatting.GOLD),
                Component.translatable("conquest.title.started_sub", Config.STARTING_TICKETS.get()));
    }

    private void teleportToSpawns(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Team team = teamOf(player.getUUID());
            if (!team.isCombatant()) {
                continue;
            }
            ResourceKey<Level> dim = team == Team.A ? spawnADim : spawnBDim;
            BlockPos pos = team == Team.A ? spawnAPos : spawnBPos;
            ServerLevel targetLevel;
            if (dim == null || pos == null) {
                targetLevel = server.overworld();
                pos = targetLevel.getSharedSpawnPos();
            } else {
                targetLevel = server.getLevel(dim);
                if (targetLevel == null) {
                    continue;
                }
            }
            BlockPos safe = TeleportHelper.findSafeSpot(targetLevel, pos);
            player.teleportTo(targetLevel, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                    Set.of(), player.getYRot(), player.getXRot());
        }
    }

    /** Forced end with no winner, or cancels a pending countdown: valid from STARTING or IN_PROGRESS. */
    public boolean stop(MinecraftServer server) {
        if (state != RoundState.STARTING && state != RoundState.IN_PROGRESS) {
            return false;
        }
        boolean wasStarting = state == RoundState.STARTING;
        state = RoundState.WAITING;
        setDirty();
        broadcast(server, Component.translatable(wasStarting ? "conquest.msg.start_cancelled" : "conquest.msg.stopped")
                .withStyle(ChatFormatting.YELLOW));
        return true;
    }

    /** Manual ENDED -> WAITING transition, skipping the resultDisplaySeconds wait. */
    public boolean reset() {
        if (state != RoundState.ENDED) {
            return false;
        }
        state = RoundState.WAITING;
        setDirty();
        return true;
    }

    // --- game loop (called once per second from ServerEvents) ---

    /** Alive, non-spectator, team-assigned players inside one point's capture radius. */
    private record PointOccupancy(int countA, int countB, Set<UUID> inZone) {
        boolean contested() {
            return countA > 0 && countB > 0;
        }
    }

    private static final PointOccupancy EMPTY_OCCUPANCY = new PointOccupancy(0, 0, Set.of());

    private PointOccupancy computeOccupancy(MinecraftServer server, CapturePoint point) {
        int countA = 0;
        int countB = 0;
        Set<UUID> inZone = new HashSet<>();
        double radiusSq = (double) point.getRadius() * point.getRadius();
        BlockPos pos = point.getPos();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension() != point.getDimension()
                    || player.isSpectator() || !player.isAlive()
                    || ReviveSystem.isDowned(player.getUUID())) {
                continue;
            }
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > radiusSq) {
                continue;
            }
            inZone.add(player.getUUID());
            Team team = teamOf(player.getUUID());
            if (team == Team.A) {
                countA++;
            } else if (team == Team.B) {
                countB++;
            }
        }
        return new PointOccupancy(countA, countB, inZone);
    }

    /**
     * Runs capture/ticket/win-condition logic for every point while IN_PROGRESS,
     * advances the result auto-reset countdown while ENDED, then always
     * broadcasts a fresh snapshot to every player so the GUI/HUD show live
     * data at any time.
     */
    public void tickSecond(MinecraftServer server) {
        Map<String, PointOccupancy> occupancyByPoint = new HashMap<>();

        if (state == RoundState.STARTING) {
            countdownSecondsRemaining--;
            if (countdownSecondsRemaining <= 0) {
                state = RoundState.IN_PROGRESS;
                setDirty();
                announceStarted(server);
            } else {
                setDirty();
                broadcastTitle(server,
                        Component.literal(String.valueOf(countdownSecondsRemaining))
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        Component.translatable("conquest.title.get_ready"));
            }
        } else if (state == RoundState.IN_PROGRESS) {
            roundElapsedSeconds++;

            if (mode == GameMode.CONQUEST && !points.isEmpty()) {
                for (CapturePoint point : points.values()) {
                    ServerLevel level = server.getLevel(point.getDimension());
                    if (level == null) {
                        continue;
                    }
                    // Downed players (squadtp revive system) cannot capture.
                    PointOccupancy occ = computeOccupancy(server, point);
                    occupancyByPoint.put(point.getName(), occ);

                    // One team alone advances; both present = contested; empty = hold.
                    if (occ.countA() > 0 ^ occ.countB() > 0) {
                        Team holder = occ.countA() > 0 ? Team.A : Team.B;
                        CapturePoint.CaptureEvent event = point.advance(holder, Config.CAPTURE_RATE_PER_SECOND.get());
                        setDirty();
                        if (event == CapturePoint.CaptureEvent.CAPTURED) {
                            broadcast(server, Component.translatable("conquest.msg.captured",
                                    holder.display(), point.getName()).withStyle(ChatFormatting.GOLD));
                        } else if (event == CapturePoint.CaptureEvent.NEUTRALIZED) {
                            broadcast(server, Component.translatable("conquest.msg.neutralized", point.getName())
                                    .withStyle(ChatFormatting.YELLOW));
                        }
                    }
                    FlagPole.update(level, point);
                }

                // Ticket bleed: scales with how many more points the leading team owns.
                // Equal ownership (including 0-0) is a stalemate — nobody bleeds.
                if (++bleedCounter >= Config.TICKET_BLEED_INTERVAL.get()) {
                    bleedCounter = 0;
                    int diff = countOwned(Team.A) - countOwned(Team.B);
                    if (diff > 0) {
                        drainTickets(server, Team.B, Config.TICKET_BLEED_AMOUNT.get() * diff);
                    } else if (diff < 0) {
                        drainTickets(server, Team.A, Config.TICKET_BLEED_AMOUNT.get() * -diff);
                    }
                }
            }

            checkRevives(server);

            // Team-empty check (only if the round is still running after the checks above).
            if (state == RoundState.IN_PROGRESS && Config.END_ON_TEAM_EMPTY.get()) {
                if (onlineCount(server, Team.A) == 0) {
                    endRound(server, Team.B);
                } else if (onlineCount(server, Team.B) == 0) {
                    endRound(server, Team.A);
                }
            }

            // Time limit: higher tickets win, equal tickets draw.
            int limit = Config.ROUND_TIME_LIMIT_SECONDS.get();
            if (state == RoundState.IN_PROGRESS && limit > 0 && roundElapsedSeconds >= limit) {
                Team winner = ticketsA > ticketsB ? Team.A : ticketsB > ticketsA ? Team.B : null;
                endRound(server, winner);
            }
        } else if (state == RoundState.ENDED) {
            resultElapsedSeconds++;
            if (Config.AUTO_RESET_AFTER_RESULT.get() && resultElapsedSeconds >= Config.RESULT_DISPLAY_SECONDS.get()) {
                state = RoundState.WAITING;
                setDirty();
            }
        }

        ConquestScoreboardPacket scoreboard = buildScoreboardPacket(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NetworkHandler.send(player, buildSyncPacket(player, occupancyByPoint, false));
            NetworkHandler.send(player, scoreboard);
        }
    }

    /** Full online-player roster with kills/deaths/assists/score, for the scoreboard screen. */
    private ConquestScoreboardPacket buildScoreboardPacket(MinecraftServer server) {
        List<ConquestScoreboardPacket.Entry> entries = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Team team = teamOf(player.getUUID());
            if (team == Team.NEUTRAL) {
                continue;
            }
            PlayerScore s = scores.get(player.getUUID());
            int kills = s == null ? 0 : s.kills;
            int deaths = s == null ? 0 : s.deaths;
            int assists = s == null ? 0 : s.assists;
            PlayerScore lifetime = lifetimeScores.get(player.getUUID());
            int lifetimeKills = lifetime == null ? 0 : lifetime.kills;
            int lifetimeDeaths = lifetime == null ? 0 : lifetime.deaths;
            int lifetimeAssists = lifetime == null ? 0 : lifetime.assists;
            entries.add(new ConquestScoreboardPacket.Entry(player.getUUID(), player.getGameProfile().getName(),
                    team, kills, deaths, assists, totalScore(player.getUUID()),
                    lifetimeKills, lifetimeDeaths, lifetimeAssists, totalLifetimeScore(player.getUUID())));
        }
        return new ConquestScoreboardPacket(roundElapsedSeconds, entries);
    }

    /** Transitions IN_PROGRESS -> ENDED, announces the result and clears cross-round player state. */
    private void endRound(MinecraftServer server, @Nullable Team winner) {
        state = RoundState.ENDED;
        lastWinner = winner;
        resultElapsedSeconds = 0;
        setDirty();

        Component title;
        Component subtitle = Component.translatable("conquest.title.result_tickets", ticketsA, ticketsB);
        if (winner == null) {
            title = Component.translatable("conquest.title.draw").withStyle(ChatFormatting.YELLOW);
            broadcast(server, Component.translatable("conquest.msg.draw").withStyle(ChatFormatting.YELLOW));
        } else {
            title = Component.translatable("conquest.title.victory", winner.display())
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            broadcast(server, Component.translatable("conquest.msg.victory", winner.display())
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
        broadcastTitle(server, title, subtitle);

        // No per-participant filtering is exposed by squadtp's public API, so
        // this clears downed/revive state server-wide rather than just for
        // this round's players.
        ReviveSystem.clear();
    }

    /**
     * Charges the respawning player's own team a ticket (Battlefield-style:
     * deaths cost reinforcements). Not applicable in TDM, where the ticket
     * counters instead count kills upward toward the kill limit.
     */
    public void onRespawn(ServerPlayer player) {
        if (state != RoundState.IN_PROGRESS || mode != GameMode.CONQUEST) {
            return;
        }
        Team team = teamOf(player.getUUID());
        if (!team.isCombatant()) {
            return;
        }
        int cost = Config.TICKET_COST_PER_RESPAWN.get();
        if (cost > 0) {
            drainTickets(player.server, team, cost);
        }
    }

    private void drainTickets(MinecraftServer server, Team victim, int amount) {
        if (victim == Team.A) {
            ticketsA = Math.max(0, ticketsA - amount);
        } else if (victim == Team.B) {
            ticketsB = Math.max(0, ticketsB - amount);
        }
        setDirty();
        if (tickets(victim) <= 0) {
            endRound(server, victim.opponent());
        }
    }

    // --- GUI sync ---

    /** Snapshot sent to a player, optionally telling their client to pop the GUI. */
    public ConquestSyncPacket buildSyncPacket(ServerPlayer viewer, Map<String, PointOccupancy> occupancyByPoint,
                                               boolean openScreen) {
        List<ConquestSyncPacket.PointStatus> statuses = new ArrayList<>();
        for (CapturePoint point : points.values()) {
            PointOccupancy occ = occupancyByPoint.getOrDefault(point.getName(), EMPTY_OCCUPANCY);
            statuses.add(new ConquestSyncPacket.PointStatus(point.getName(), point.getRadius(), point.getOwner(),
                    point.getCapturingTeam(), point.getFlagLevel(), occ.contested(),
                    occ.inZone().contains(viewer.getUUID())));
        }
        return new ConquestSyncPacket(statuses, ticketsA, ticketsB, isActive(), state, mode,
                teamOf(viewer.getUUID()), viewer.hasPermissions(2), openScreen);
    }

    /** Used by the flag block's right-click handler: a fresh snapshot that opens the GUI. */
    public ConquestSyncPacket buildSyncPacketForOpen(MinecraftServer server, ServerPlayer viewer) {
        Map<String, PointOccupancy> occupancyByPoint = new HashMap<>();
        for (CapturePoint point : points.values()) {
            occupancyByPoint.put(point.getName(), computeOccupancy(server, point));
        }
        return buildSyncPacket(viewer, occupancyByPoint, true);
    }

    private static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    /** Vanilla title+subtitle shown to every online player. */
    private static void broadcastTitle(MinecraftServer server, Component title, Component subtitle) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
    }

    // --- persistence ---

    public static ConquestManager load(CompoundTag tag) {
        ConquestManager manager = new ConquestManager();
        ListTag pointList = tag.getList("Points", Tag.TAG_COMPOUND);
        for (int i = 0; i < pointList.size(); i++) {
            CapturePoint point = CapturePoint.load(pointList.getCompound(i));
            manager.points.put(point.getName(), point);
        }
        ListTag teamList = tag.getList("Teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < teamList.size(); i++) {
            CompoundTag t = teamList.getCompound(i);
            manager.playerTeams.put(t.getUUID("Uuid"), Team.valueOf(t.getString("Team")));
        }
        manager.ticketsA = tag.getInt("TicketsA");
        manager.ticketsB = tag.getInt("TicketsB");
        manager.mode = tag.contains("Mode") ? GameMode.valueOf(tag.getString("Mode")) : GameMode.CONQUEST;
        manager.state = tag.contains("State") ? RoundState.valueOf(tag.getString("State")) : RoundState.WAITING;
        // The countdown itself isn't persisted, so a restart mid-countdown would
        // otherwise get stuck in STARTING forever; fall back to WAITING instead.
        if (manager.state == RoundState.STARTING) {
            manager.state = RoundState.WAITING;
        }
        manager.roundElapsedSeconds = tag.getInt("RoundElapsedSeconds");
        manager.resultElapsedSeconds = tag.getInt("ResultElapsedSeconds");
        if (tag.contains("LastWinner")) {
            manager.lastWinner = Team.valueOf(tag.getString("LastWinner"));
        }
        if (tag.contains("SpawnADim")) {
            manager.spawnADim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("SpawnADim")));
            manager.spawnAPos = NbtUtils.readBlockPos(tag.getCompound("SpawnAPos"));
        }
        if (tag.contains("SpawnBDim")) {
            manager.spawnBDim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("SpawnBDim")));
            manager.spawnBPos = NbtUtils.readBlockPos(tag.getCompound("SpawnBPos"));
        }
        ListTag scoreList = tag.getList("Scores", Tag.TAG_COMPOUND);
        for (int i = 0; i < scoreList.size(); i++) {
            CompoundTag s = scoreList.getCompound(i);
            PlayerScore score = new PlayerScore();
            score.kills = s.getInt("Kills");
            score.deaths = s.getInt("Deaths");
            score.assists = s.getInt("Assists");
            score.revives = s.getInt("Revives");
            manager.scores.put(s.getUUID("Uuid"), score);
        }
        ListTag lifetimeScoreList = tag.getList("LifetimeScores", Tag.TAG_COMPOUND);
        for (int i = 0; i < lifetimeScoreList.size(); i++) {
            CompoundTag s = lifetimeScoreList.getCompound(i);
            PlayerScore score = new PlayerScore();
            score.kills = s.getInt("Kills");
            score.deaths = s.getInt("Deaths");
            score.assists = s.getInt("Assists");
            score.revives = s.getInt("Revives");
            manager.lifetimeScores.put(s.getUUID("Uuid"), score);
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag pointList = new ListTag();
        for (CapturePoint point : points.values()) {
            pointList.add(point.save());
        }
        tag.put("Points", pointList);
        ListTag teamList = new ListTag();
        for (Map.Entry<UUID, Team> e : playerTeams.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Uuid", e.getKey());
            t.putString("Team", e.getValue().name());
            teamList.add(t);
        }
        tag.put("Teams", teamList);
        tag.putInt("TicketsA", ticketsA);
        tag.putInt("TicketsB", ticketsB);
        tag.putString("Mode", mode.name());
        tag.putString("State", state.name());
        tag.putInt("RoundElapsedSeconds", roundElapsedSeconds);
        tag.putInt("ResultElapsedSeconds", resultElapsedSeconds);
        if (lastWinner != null) {
            tag.putString("LastWinner", lastWinner.name());
        }
        if (spawnADim != null && spawnAPos != null) {
            tag.putString("SpawnADim", spawnADim.location().toString());
            tag.put("SpawnAPos", NbtUtils.writeBlockPos(spawnAPos));
        }
        if (spawnBDim != null && spawnBPos != null) {
            tag.putString("SpawnBDim", spawnBDim.location().toString());
            tag.put("SpawnBPos", NbtUtils.writeBlockPos(spawnBPos));
        }
        ListTag scoreList = new ListTag();
        for (Map.Entry<UUID, PlayerScore> e : scores.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putUUID("Uuid", e.getKey());
            s.putInt("Kills", e.getValue().kills);
            s.putInt("Deaths", e.getValue().deaths);
            s.putInt("Assists", e.getValue().assists);
            s.putInt("Revives", e.getValue().revives);
            scoreList.add(s);
        }
        tag.put("Scores", scoreList);
        ListTag lifetimeScoreList = new ListTag();
        for (Map.Entry<UUID, PlayerScore> e : lifetimeScores.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putUUID("Uuid", e.getKey());
            s.putInt("Kills", e.getValue().kills);
            s.putInt("Deaths", e.getValue().deaths);
            s.putInt("Assists", e.getValue().assists);
            s.putInt("Revives", e.getValue().revives);
            lifetimeScoreList.add(s);
        }
        tag.put("LifetimeScores", lifetimeScoreList);
        return tag;
    }
}
