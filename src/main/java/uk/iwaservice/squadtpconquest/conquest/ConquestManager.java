package uk.iwaservice.squadtpconquest.conquest;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.registries.ForgeRegistries;
import uk.iwaservice.squadtp.squad.ReviveSystem;
import uk.iwaservice.squadtp.squad.Squad;
import uk.iwaservice.squadtp.squad.SquadFeature;
import uk.iwaservice.squadtp.squad.SquadManager;
import uk.iwaservice.squadtp.squad.TeleportHelper;
import uk.iwaservice.squadtpconquest.Config;
import uk.iwaservice.squadtpconquest.network.ConquestScoreboardPacket;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;
import uk.iwaservice.squadtpconquest.network.NetworkHandler;
import uk.iwaservice.squadtpconquest.network.SpotPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
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
    /** Named, reusable map layouts (points/spawns/mode), keyed by name. */
    private final LinkedHashMap<String, MapPreset> presets = new LinkedHashMap<>();
    /** Named boxes in which terrain destruction never modifies blocks; any number may exist. */
    private final LinkedHashMap<String, ProtectZone> protectZones = new LinkedHashMap<>();
    /** Block registry names terrain destruction never destroys, in addition to Config.INDESTRUCTIBLE_BLOCKS. */
    private final LinkedHashSet<String> protectedBlocks = new LinkedHashSet<>();
    /** Named scorestreak-style rewards (score cost -> item), keyed by name. */
    private final LinkedHashMap<String, CallIn> callIns = new LinkedHashMap<>();
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

    /** Where every combatant is teleported to when a round ends (see {@link #endRound}). Optional. */
    @Nullable
    private ResourceKey<Level> gatherDim;
    @Nullable
    private BlockPos gatherPos;

    /**
     * Team A's home zone: an axis-aligned box between two corners; enemies lingering inside are
     * executed. Both corners must be set (and share zoneADim) for the zone to be active.
     */
    @Nullable
    private ResourceKey<Level> zoneADim;
    @Nullable
    private BlockPos zoneAPos1;
    @Nullable
    private BlockPos zoneAPos2;
    @Nullable
    private ResourceKey<Level> zoneBDim;
    @Nullable
    private BlockPos zoneBPos1;
    @Nullable
    private BlockPos zoneBPos2;
    /** Transient: continuous seconds each intruder has spent inside the enemy's home zone. */
    private final Map<UUID, Integer> zoneIntrusionSeconds = new HashMap<>();

    /**
     * The battlefield boundary: a single axis-aligned box (not per-team, unlike the home zones)
     * outside which any combatant player is executed after lingering too long — BF's
     * out-of-bounds. Both corners must be set (and share boundaryDim) to be active.
     */
    @Nullable
    private ResourceKey<Level> boundaryDim;
    @Nullable
    private BlockPos boundaryPos1;
    @Nullable
    private BlockPos boundaryPos2;
    /** Transient: continuous seconds each player has spent outside the boundary. */
    private final Map<UUID, Integer> boundaryOutsideSeconds = new HashMap<>();
    /** Transient: tick count each player's spot key is next usable at (see {@link #spotPlayer}). */
    private final Map<UUID, Integer> spotCooldownUntilTick = new HashMap<>();

    /**
     * Transient: a whole-region snapshot of the battlefield boundary (see {@link #getBoundaryMin})
     * taken at round start and pasted back at round end, so terrain always resets regardless of
     * what damaged it (not saved to NBT — a round-scoped snapshot has no meaning across restarts).
     * Null whenever no boundary is set, or no round has captured one yet.
     */
    @Nullable
    private StructureTemplate terrainSnapshot;
    @Nullable
    private ResourceKey<Level> terrainSnapshotDim;
    @Nullable
    private BlockPos terrainSnapshotOrigin;

    /**
     * The training range: a single axis-aligned box, independent of the match (round state,
     * game mode, tickets). Anyone can join {@link Team#RANGE} to be teleported into it; both
     * corners must be set (and share rangeDim) to be active.
     */
    @Nullable
    private ResourceKey<Level> rangeDim;
    @Nullable
    private BlockPos rangePos1;
    @Nullable
    private BlockPos rangePos2;
    /**
     * Transient: the range's clean-state snapshot, taken by {@link #setRange} and pasted back
     * every {@code rangeResetIntervalSeconds} (see {@link #tickRange}) — not saved to NBT, so a
     * restart re-captures the (by-then already-in-use) area fresh on the next tick instead of
     * silently never resetting again.
     */
    @Nullable
    private StructureTemplate rangeSnapshot;
    @Nullable
    private ResourceKey<Level> rangeSnapshotDim;
    @Nullable
    private BlockPos rangeSnapshotOrigin;
    /** Transient: seconds until the next automatic range reset. Not persisted. */
    private int rangeResetSecondsRemaining = Config.RANGE_RESET_INTERVAL_SECONDS.get();

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

    // --- breakthrough mode ---

    /** Sectors keyed by their fixed number; ascending key order is the attack sequence. */
    private final NavigableMap<Integer, Sector> sectors = new TreeMap<>();
    /** Which of Team A/B plays attacker in breakthrough. Defender is always its opponent(). */
    private Team attackerTeam = Team.A;
    /** Number of the currently active sector; 0 means no round has activated one yet. */
    private int activeSectorNumber;
    /** Attacker respawn tickets remaining this round; a death consumes one if any are left. */
    private int attackerTickets;
    /** Seconds until the active sector's time limit expires (defenders win on 0). */
    private int sectorSecondsRemaining;
    /**
     * Seconds left in the post-capture grace period before the new active sector's combat area
     * (if it has one) starts being enforced as an out-of-bounds boundary. Transient, not
     * persisted; 0 outside this window.
     */
    private int sectorAreaGraceSecondsRemaining;
    /** Seconds until the next attacker respawn wave releases everyone waiting. */
    private int respawnWaveSecondsRemaining;
    /** Attackers who have died and are waiting (as spectators) for the next respawn wave. */
    private final Set<UUID> pendingAttackerRespawns = new HashSet<>();

    public static ConquestManager get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ConquestManager::load, ConquestManager::new, DATA_NAME);
    }

    /** Fresh world only (see {@link #get}): seeds a built-in blank "Normal" preset to reset to. */
    public ConquestManager() {
        presets.put("Normal", new MapPreset("Normal", GameMode.CONQUEST, List.of(),
                null, null, null, null, null, null, null, List.of(), List.of()));
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

    // --- breakthrough: roles and sectors ---

    public Team attackerTeam() {
        return attackerTeam;
    }

    public Team defenderTeam() {
        return attackerTeam.opponent();
    }

    /** Assigns which of Team A/B plays attacker. Rejected while a round is running or showing a result. */
    public boolean setAttackerTeam(Team team) {
        if (state != RoundState.WAITING || !team.isCombatant()) {
            return false;
        }
        attackerTeam = team;
        setDirty();
        return true;
    }

    public Collection<Sector> getSectors() {
        return sectors.values();
    }

    public boolean hasSectors() {
        return !sectors.isEmpty();
    }

    @Nullable
    public Sector getSector(int number) {
        return sectors.get(number);
    }

    /** The sector currently being fought over, or null if breakthrough hasn't started a round yet. */
    @Nullable
    public Sector currentSector() {
        return activeSectorNumber == 0 ? null : sectors.get(activeSectorNumber);
    }

    /** 1-based position of the active sector among all sectors, for "Sector X/Y" display; 0 if none active. */
    public int sectorIndex() {
        if (activeSectorNumber == 0) {
            return 0;
        }
        int idx = 0;
        for (int key : sectors.keySet()) {
            idx++;
            if (key == activeSectorNumber) {
                break;
            }
        }
        return idx;
    }

    public int sectorCount() {
        return sectors.size();
    }

    public int attackerTickets() {
        return attackerTickets;
    }

    public int respawnWaveSecondsRemaining() {
        return respawnWaveSecondsRemaining;
    }

    /** Adds a capture point at the given position and assigns it to the named sector (creating it if new). */
    public void addSectorPoint(ServerLevel level, int sectorNumber, String pointName, BlockPos pos, int radius) {
        setPoint(level, pointName, pos, radius);
        Sector sector = sectors.computeIfAbsent(sectorNumber, Sector::new);
        if (!sector.getPointNames().contains(pointName)) {
            sector.getPointNames().add(pointName);
        }
        setDirty();
    }

    /** Removes a sector and every capture point assigned to it. False if no sector has that number. */
    public boolean removeSector(MinecraftServer server, int number) {
        Sector sector = sectors.remove(number);
        if (sector == null) {
            return false;
        }
        for (String name : List.copyOf(sector.getPointNames())) {
            removePoint(server, name);
        }
        setDirty();
        return true;
    }

    /** Sets a sector's attacker or defender spawn. False if no sector has that number. */
    public boolean setSectorSpawn(ServerLevel level, int number, boolean attackerRole, BlockPos pos) {
        Sector sector = sectors.get(number);
        if (sector == null) {
            return false;
        }
        if (attackerRole) {
            sector.setAttackerSpawn(level.dimension(), pos);
        } else {
            sector.setDefenderSpawn(level.dimension(), pos);
        }
        setDirty();
        return true;
    }

    /** Overrides a sector's time limit (0 = fall back to the global default). False if no sector has that number. */
    public boolean setSectorTimeLimit(int number, int seconds) {
        Sector sector = sectors.get(number);
        if (sector == null) {
            return false;
        }
        sector.setTimeLimitSecondsOverride(seconds);
        setDirty();
        return true;
    }

    /** Sets a sector's combat area (out-of-bounds while it's active). False if no sector has that number. */
    public boolean setSectorArea(ServerLevel level, int number, BlockPos pos1, BlockPos pos2) {
        Sector sector = sectors.get(number);
        if (sector == null) {
            return false;
        }
        sector.setCombatArea(level.dimension(), pos1, pos2);
        setDirty();
        return true;
    }

    /** Clears a sector's combat area. False if no sector has that number, or it had none set. */
    public boolean removeSectorArea(int number) {
        Sector sector = sectors.get(number);
        if (sector == null || !sector.removeCombatArea()) {
            return false;
        }
        setDirty();
        return true;
    }

    /** The sector number a point belongs to, or 0 if it isn't assigned to any sector. */
    public int sectorNumberOf(String pointName) {
        for (Sector sector : sectors.values()) {
            if (sector.getPointNames().contains(pointName)) {
                return sector.getNumber();
            }
        }
        return 0;
    }

    private int currentSectorTimeLimit() {
        Sector sector = currentSector();
        int override = sector != null ? sector.getTimeLimitSecondsOverride() : 0;
        return override > 0 ? override : Config.BT_SECTOR_TIME_LIMIT_SECONDS.get();
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

    /**
     * Marks {@code target}'s current position for {@code spotter}'s team (a one-time snapshot,
     * not a live tracker — see {@link SpotPacket}), gated by a per-spotter cooldown. False if
     * still on cooldown (no-op); the caller has already validated eligibility (round state, teams,
     * alive, etc.) before calling this.
     */
    public boolean spotPlayer(MinecraftServer server, ServerPlayer spotter, ServerPlayer target) {
        int now = server.getTickCount();
        Integer readyAt = spotCooldownUntilTick.get(spotter.getUUID());
        if (readyAt != null && now < readyAt) {
            return false;
        }
        spotCooldownUntilTick.put(spotter.getUUID(), now + Config.SPOT_COOLDOWN_SECONDS.get() * 20);

        SpotPacket packet = new SpotPacket(target.getUUID(), target.getGameProfile().getName(),
                target.level().dimension().location(), target.blockPosition(),
                Config.SPOT_DURATION_SECONDS.get() * 20);
        Team spotterTeam = teamOf(spotter.getUUID());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (teamOf(player.getUUID()) == spotterTeam) {
                NetworkHandler.send(player, packet);
            }
        }
        return true;
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

    /** This round's score minus whatever the player has already spent on call-ins this round. */
    public int availableScore(UUID player) {
        return totalScore(player) - scoreOf(player).spent;
    }

    // --- call-ins (scorestreak-style rewards: spend available score for an item) ---

    public Collection<CallIn> getCallIns() {
        return callIns.values();
    }

    @Nullable
    public CallIn getCallIn(String name) {
        return callIns.get(name);
    }

    /** Adds/replaces a named call-in. */
    public void addCallIn(String name, int scoreCost, ResourceLocation itemId, int count) {
        callIns.put(name, new CallIn(name, scoreCost, itemId, count));
        setDirty();
    }

    /** Removes a call-in. False if no call-in has that name. */
    public boolean removeCallIn(String name) {
        if (callIns.remove(name) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    /** Outcome of a /conquest callin use attempt. */
    public enum UseCallInResult { OK, NOT_FOUND, NOT_ACTIVE, INSUFFICIENT_SCORE, UNKNOWN_ITEM }

    /**
     * Spends {@link CallIn#getScoreCost()} from the player's {@link #availableScore} and gives
     * them the item (dropped at their feet if the inventory is full), if they have enough and a
     * round is running.
     */
    public UseCallInResult useCallIn(ServerPlayer player, String name) {
        if (state != RoundState.IN_PROGRESS) {
            return UseCallInResult.NOT_ACTIVE;
        }
        CallIn callIn = callIns.get(name);
        if (callIn == null) {
            return UseCallInResult.NOT_FOUND;
        }
        if (availableScore(player.getUUID()) < callIn.getScoreCost()) {
            return UseCallInResult.INSUFFICIENT_SCORE;
        }
        Item item = ForgeRegistries.ITEMS.getValue(callIn.getItemId());
        if (item == null) {
            return UseCallInResult.UNKNOWN_ITEM;
        }
        scoreOf(player.getUUID()).spent += callIn.getScoreCost();
        setDirty();
        ItemStack stack = new ItemStack(item, callIn.getCount());
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
        return UseCallInResult.OK;
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
        Team previous = playerTeams.put(player.getUUID(), team);
        setDirty();
        syncVanillaTeam(player, team);
        if (previous != team) {
            applyMaxHealth(player, team);
            leaveSquadIfAny(player);
            if (team == Team.RANGE) {
                teleportIntoRange(player);
            }
            if (team == Team.SPECTATOR) {
                player.setGameMode(GameType.SPECTATOR);
            } else if (previous == Team.SPECTATOR) {
                player.setGameMode(GameType.SURVIVAL);
            }
        }
    }

    /**
     * Removes the player from their current squadtp squad, if any. squadtp's
     * {@code requireSameTeam} only gates squad join/creation, not later conquest
     * team switches, so without this a player who solo-switches conquest teams
     * (e.g. {@code /conquest team join}, outside of {@link #shuffleTeams}) stays
     * squadmates with their old team — letting AED revives and other squad
     * features (teleport, etc.) cross the team boundary.
     */
    private static void leaveSquadIfAny(ServerPlayer player) {
        MinecraftServer server = player.server;
        SquadManager squadManager = SquadManager.get(server);
        Squad squad = squadManager.getSquadOf(player.getUUID());
        if (squad != null) {
            squadManager.removeMember(server, squad, player.getUUID());
        }
    }

    /**
     * Sets max health to {@code maxHealth} (config) for combatants, or back to vanilla default
     * otherwise, and heals to the new max. Called on team join (only when the team actually
     * changes, so repeatedly rejoining the same team can't be used to spam-heal), again for
     * every combatant at round start (so it always reflects the current config, even for players
     * who joined their team before the config was last changed), and on every respawn (since a
     * respawned player entity otherwise resets to vanilla's default max health).
     */
    private static void applyMaxHealth(ServerPlayer player, Team team) {
        AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        double target = team.isCombatant() ? Config.MAX_HEALTH.get() : Attributes.MAX_HEALTH.getDefaultValue();
        attribute.setBaseValue(target);
        player.setHealth((float) target);
    }

    /**
     * Randomly splits every online player who isn't on the admin, training-range or spectator
     * team into Team A / Team B as evenly as possible. Since a shuffle can freely split
     * up existing squads across the two new teams, every squad touched by it
     * is disbanded first and fresh same-team squads are formed afterward
     * (chunked to squadtp's maxSquadSize) so nobody needs to manually reform.
     * Returns the number of players reassigned.
     */
    public int shuffleTeams(MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Team current = teamOf(player.getUUID());
            if (current != Team.ADMIN && current != Team.RANGE && current != Team.SPECTATOR) {
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
                || team.getName().equals(vanillaTeamName(Team.ADMIN)) || team.getName().equals(vanillaTeamName(Team.RANGE))
                || team.getName().equals(vanillaTeamName(Team.SPECTATOR));
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
        for (Sector sector : sectors.values()) {
            sector.getPointNames().remove(name);
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

    public void setGatherPoint(ServerLevel level, BlockPos pos) {
        gatherDim = level.dimension();
        gatherPos = pos.immutable();
        setDirty();
    }

    /** Clears the round-end gather point. False if none was set. */
    public boolean removeGatherPoint() {
        if (gatherDim == null && gatherPos == null) {
            return false;
        }
        gatherDim = null;
        gatherPos = null;
        setDirty();
        return true;
    }

    @Nullable
    public ResourceKey<Level> getGatherDim() {
        return gatherDim;
    }

    @Nullable
    public BlockPos getGatherPos() {
        return gatherPos;
    }

    // --- home zones (per-team territory; the opposing team is executed after lingering too long) ---

    /** Defines/relocates {@code team}'s home zone as the box between two corners. */
    public void setZone(ServerLevel level, Team team, BlockPos pos1, BlockPos pos2) {
        if (team == Team.A) {
            zoneADim = level.dimension();
            zoneAPos1 = pos1.immutable();
            zoneAPos2 = pos2.immutable();
        } else if (team == Team.B) {
            zoneBDim = level.dimension();
            zoneBPos1 = pos1.immutable();
            zoneBPos2 = pos2.immutable();
        }
        setDirty();
    }

    /**
     * Sets one corner of {@code team}'s home zone to the given position, leaving the other
     * corner untouched (the zone only becomes active once both are set). If the zone already had
     * a corner in a different dimension, both corners are reset first — a box can't span
     * dimensions, so switching dimension starts the zone over rather than silently corrupting it.
     */
    public void setZoneCorner(ServerLevel level, Team team, boolean corner1, BlockPos pos) {
        ResourceKey<Level> dim = level.dimension();
        if (team == Team.A) {
            if (zoneADim != null && !zoneADim.equals(dim)) {
                zoneAPos1 = null;
                zoneAPos2 = null;
            }
            zoneADim = dim;
            if (corner1) {
                zoneAPos1 = pos.immutable();
            } else {
                zoneAPos2 = pos.immutable();
            }
        } else if (team == Team.B) {
            if (zoneBDim != null && !zoneBDim.equals(dim)) {
                zoneBPos1 = null;
                zoneBPos2 = null;
            }
            zoneBDim = dim;
            if (corner1) {
                zoneBPos1 = pos.immutable();
            } else {
                zoneBPos2 = pos.immutable();
            }
        }
        setDirty();
    }

    /** Clears a team's home zone. False if neither corner was set. */
    public boolean removeZone(Team team) {
        if (team == Team.A) {
            if (zoneAPos1 == null && zoneAPos2 == null) {
                return false;
            }
            zoneADim = null;
            zoneAPos1 = null;
            zoneAPos2 = null;
        } else if (team == Team.B) {
            if (zoneBPos1 == null && zoneBPos2 == null) {
                return false;
            }
            zoneBDim = null;
            zoneBPos1 = null;
            zoneBPos2 = null;
        } else {
            return false;
        }
        setDirty();
        return true;
    }

    @Nullable
    public ResourceKey<Level> getZoneDim(Team team) {
        return team == Team.A ? zoneADim : team == Team.B ? zoneBDim : null;
    }

    /** Lower corner of the zone's box (min of each axis across both corners); null unless both corners are set. */
    @Nullable
    public BlockPos getZoneMin(Team team) {
        BlockPos[] bounds = zoneBounds(team);
        return bounds == null ? null : bounds[0];
    }

    /** Upper corner of the zone's box; null unless both corners are set. */
    @Nullable
    public BlockPos getZoneMax(Team team) {
        BlockPos[] bounds = zoneBounds(team);
        return bounds == null ? null : bounds[1];
    }

    @Nullable
    private BlockPos[] zoneBounds(Team team) {
        BlockPos p1 = team == Team.A ? zoneAPos1 : team == Team.B ? zoneBPos1 : null;
        BlockPos p2 = team == Team.A ? zoneAPos2 : team == Team.B ? zoneBPos2 : null;
        if (p1 == null || p2 == null) {
            return null;
        }
        BlockPos min = new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
        BlockPos max = new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));
        return new BlockPos[]{min, max};
    }

    /**
     * Once per second while a round is running: any player from the opposing team standing
     * inside a home zone accrues intrusion time (reset to 0 the instant they step out) and is
     * executed once it reaches {@code homeZoneKillSeconds}, with an action-bar countdown warning
     * every second before that. Independent of game mode.
     */
    private void tickHomeZones(MinecraftServer server) {
        checkZoneIntrusion(server, Team.A, zoneADim, getZoneMin(Team.A), getZoneMax(Team.A));
        checkZoneIntrusion(server, Team.B, zoneBDim, getZoneMin(Team.B), getZoneMax(Team.B));
    }

    private void checkZoneIntrusion(MinecraftServer server, Team owner, @Nullable ResourceKey<Level> dim,
                                     @Nullable BlockPos min, @Nullable BlockPos max) {
        if (dim == null || min == null || max == null) {
            return;
        }
        Team intruderTeam = owner.opponent();
        int killSeconds = Config.HOME_ZONE_KILL_SECONDS.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            boolean inside = teamOf(uuid) == intruderTeam && player.isAlive()
                    && player.level().dimension() == dim
                    && player.getX() >= min.getX() && player.getX() < max.getX() + 1
                    && player.getY() >= min.getY() && player.getY() < max.getY() + 1
                    && player.getZ() >= min.getZ() && player.getZ() < max.getZ() + 1;
            if (!inside) {
                zoneIntrusionSeconds.remove(uuid);
                continue;
            }
            int seconds = zoneIntrusionSeconds.merge(uuid, 1, Integer::sum);
            int remaining = killSeconds - seconds;
            if (remaining <= 0) {
                zoneIntrusionSeconds.remove(uuid);
                player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
            } else {
                player.displayClientMessage(Component.translatable("conquest.msg.zone_warning", remaining)
                        .withStyle(ChatFormatting.RED), true);
            }
        }
    }

    // --- battlefield boundary (single global box; outside it too long = executed) ---

    /** Defines/relocates the battlefield boundary as the box between two corners. */
    public void setBoundary(ServerLevel level, BlockPos pos1, BlockPos pos2) {
        boundaryDim = level.dimension();
        boundaryPos1 = pos1.immutable();
        boundaryPos2 = pos2.immutable();
        setDirty();
    }

    /**
     * Sets one corner of the boundary to the given position, leaving the other corner untouched
     * (the boundary only becomes active once both are set). Switching dimension resets both
     * corners first, same rule as the home zone's corner1/corner2 set.
     */
    public void setBoundaryCorner(ServerLevel level, boolean corner1, BlockPos pos) {
        ResourceKey<Level> dim = level.dimension();
        if (boundaryDim != null && !boundaryDim.equals(dim)) {
            boundaryPos1 = null;
            boundaryPos2 = null;
        }
        boundaryDim = dim;
        if (corner1) {
            boundaryPos1 = pos.immutable();
        } else {
            boundaryPos2 = pos.immutable();
        }
        setDirty();
    }

    /** Clears the battlefield boundary. False if neither corner was set. */
    public boolean removeBoundary() {
        if (boundaryPos1 == null && boundaryPos2 == null) {
            return false;
        }
        boundaryDim = null;
        boundaryPos1 = null;
        boundaryPos2 = null;
        setDirty();
        return true;
    }

    @Nullable
    public ResourceKey<Level> getBoundaryDim() {
        return boundaryDim;
    }

    /** Lower corner of the boundary's box; null unless both corners are set. */
    @Nullable
    public BlockPos getBoundaryMin() {
        BlockPos[] bounds = boundaryBounds();
        return bounds == null ? null : bounds[0];
    }

    /** Upper corner of the boundary's box; null unless both corners are set. */
    @Nullable
    public BlockPos getBoundaryMax() {
        BlockPos[] bounds = boundaryBounds();
        return bounds == null ? null : bounds[1];
    }

    @Nullable
    private BlockPos[] boundaryBounds() {
        if (boundaryPos1 == null || boundaryPos2 == null) {
            return null;
        }
        BlockPos min = new BlockPos(Math.min(boundaryPos1.getX(), boundaryPos2.getX()), Math.min(boundaryPos1.getY(), boundaryPos2.getY()), Math.min(boundaryPos1.getZ(), boundaryPos2.getZ()));
        BlockPos max = new BlockPos(Math.max(boundaryPos1.getX(), boundaryPos2.getX()), Math.max(boundaryPos1.getY(), boundaryPos2.getY()), Math.max(boundaryPos1.getZ(), boundaryPos2.getZ()));
        return new BlockPos[]{min, max};
    }

    // --- training range (single global box; independent of round/match state) ---

    /**
     * Defines/relocates the training range as the box between two corners, and immediately
     * captures its current contents as the clean state every automatic reset pastes back (see
     * {@link #tickRange}).
     */
    public void setRange(ServerLevel level, BlockPos pos1, BlockPos pos2) {
        rangeDim = level.dimension();
        rangePos1 = pos1.immutable();
        rangePos2 = pos2.immutable();
        setDirty();
        captureRangeSnapshot(level.getServer());
    }

    /**
     * Sets one corner of the range to the given position, leaving the other corner untouched (the
     * range only becomes active, and gets a fresh snapshot, once both are set). Switching
     * dimension resets both corners first, same rule as the battlefield boundary's corner1/corner2.
     */
    public void setRangeCorner(ServerLevel level, boolean corner1, BlockPos pos) {
        ResourceKey<Level> dim = level.dimension();
        if (rangeDim != null && !rangeDim.equals(dim)) {
            rangePos1 = null;
            rangePos2 = null;
        }
        rangeDim = dim;
        if (corner1) {
            rangePos1 = pos.immutable();
        } else {
            rangePos2 = pos.immutable();
        }
        setDirty();
        if (rangePos1 != null && rangePos2 != null) {
            captureRangeSnapshot(level.getServer());
        }
    }

    /** Clears the training range and its snapshot. False if neither corner was set. */
    public boolean removeRange() {
        if (rangePos1 == null && rangePos2 == null) {
            return false;
        }
        rangeDim = null;
        rangePos1 = null;
        rangePos2 = null;
        rangeSnapshot = null;
        rangeSnapshotDim = null;
        rangeSnapshotOrigin = null;
        setDirty();
        return true;
    }

    @Nullable
    public ResourceKey<Level> getRangeDim() {
        return rangeDim;
    }

    /** Lower corner of the range's box; null unless both corners are set. */
    @Nullable
    public BlockPos getRangeMin() {
        BlockPos[] bounds = rangeBounds();
        return bounds == null ? null : bounds[0];
    }

    /** Upper corner of the range's box; null unless both corners are set. */
    @Nullable
    public BlockPos getRangeMax() {
        BlockPos[] bounds = rangeBounds();
        return bounds == null ? null : bounds[1];
    }

    public int getRangeResetSecondsRemaining() {
        return rangeResetSecondsRemaining;
    }

    @Nullable
    private BlockPos[] rangeBounds() {
        if (rangePos1 == null || rangePos2 == null) {
            return null;
        }
        BlockPos min = new BlockPos(Math.min(rangePos1.getX(), rangePos2.getX()), Math.min(rangePos1.getY(), rangePos2.getY()), Math.min(rangePos1.getZ(), rangePos2.getZ()));
        BlockPos max = new BlockPos(Math.max(rangePos1.getX(), rangePos2.getX()), Math.max(rangePos1.getY(), rangePos2.getY()), Math.max(rangePos1.getZ(), rangePos2.getZ()));
        return new BlockPos[]{min, max};
    }

    /** Takes a whole-region snapshot of the current range area, for {@link #restoreRangeSnapshot} to paste back. */
    private void captureRangeSnapshot(MinecraftServer server) {
        BlockPos min = getRangeMin();
        BlockPos max = getRangeMax();
        if (rangeDim == null || min == null || max == null) {
            return;
        }
        ServerLevel level = server.getLevel(rangeDim);
        if (level == null) {
            return;
        }
        Vec3i size = new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, min, size, false, null);
        rangeSnapshot = template;
        rangeSnapshotDim = rangeDim;
        rangeSnapshotOrigin = min.immutable();
    }

    /**
     * Pastes back the range's clean-state snapshot and teleports/heals every online range-team
     * player back into the area. False (no-op) if no snapshot is held — e.g. no range area set,
     * or the snapshot was lost to a server restart (it isn't persisted; {@link #tickRange}
     * re-captures one automatically once the area is set again).
     */
    private boolean restoreRangeSnapshot(MinecraftServer server) {
        if (rangeSnapshot == null || rangeSnapshotDim == null || rangeSnapshotOrigin == null) {
            return false;
        }
        ServerLevel level = server.getLevel(rangeSnapshotDim);
        if (level != null) {
            rangeSnapshot.placeInWorld(level, rangeSnapshotOrigin, rangeSnapshotOrigin,
                    new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true), level.getRandom(), 3);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (teamOf(player.getUUID()) == Team.RANGE) {
                teleportIntoRange(player);
                player.setHealth(player.getMaxHealth());
            }
        }
        return true;
    }

    /** OP escape hatch (`/conquest range reset`) to trigger the automatic reset immediately. */
    public boolean resetRangeNow(MinecraftServer server) {
        boolean reset = restoreRangeSnapshot(server);
        if (reset) {
            rangeResetSecondsRemaining = Config.RANGE_RESET_INTERVAL_SECONDS.get();
        }
        return reset;
    }

    /**
     * Teleports a player to a safe spot in the middle of the training range. No-op if no range
     * area is set.
     */
    void teleportIntoRange(ServerPlayer player) {
        BlockPos min = getRangeMin();
        BlockPos max = getRangeMax();
        if (rangeDim == null || min == null || max == null) {
            return;
        }
        ServerLevel level = player.server.getLevel(rangeDim);
        if (level == null) {
            return;
        }
        BlockPos center = new BlockPos((min.getX() + max.getX()) / 2, max.getY(), (min.getZ() + max.getZ()) / 2);
        BlockPos safe = TeleportHelper.findSafeSpot(level, center);
        player.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
    }

    /**
     * Once per second, unconditionally (independent of round state — see {@link #tickSecond}):
     * counts down to the next automatic range reset. Self-heals a missing snapshot (e.g. after a
     * server restart, since it isn't persisted) by capturing one from the area's current state
     * instead of restoring anything, so resets keep working without an admin re-running
     * {@code /conquest range set}. No-op if no range area is set.
     */
    private void tickRange(MinecraftServer server) {
        if (rangeDim == null || rangePos1 == null || rangePos2 == null) {
            return;
        }
        if (rangeSnapshot == null) {
            captureRangeSnapshot(server);
            rangeResetSecondsRemaining = Config.RANGE_RESET_INTERVAL_SECONDS.get();
            return;
        }
        if (--rangeResetSecondsRemaining <= 0) {
            restoreRangeSnapshot(server);
            rangeResetSecondsRemaining = Config.RANGE_RESET_INTERVAL_SECONDS.get();
        }
    }

    /**
     * Once per second while a round is running: any combatant player outside the boundary
     * accrues time (reset to 0 the instant they return inside) and is executed once it reaches
     * {@code boundaryKillSeconds}, with an action-bar countdown warning every second before
     * that. No-op if no boundary is set. Independent of game mode.
     */
    private void tickBoundary(MinecraftServer server) {
        if (sectorAreaGraceSecondsRemaining > 0) {
            sectorAreaGraceSecondsRemaining--;
            return;
        }

        ResourceKey<Level> dim = boundaryDim;
        BlockPos[] bounds = boundaryBounds();
        if (mode == GameMode.BREAKTHROUGH) {
            Sector sector = currentSector();
            if (sector != null && sector.getCombatAreaDim() != null
                    && sector.getCombatAreaMin() != null && sector.getCombatAreaMax() != null) {
                dim = sector.getCombatAreaDim();
                bounds = new BlockPos[]{sector.getCombatAreaMin(), sector.getCombatAreaMax()};
            }
        }
        if (dim == null || bounds == null) {
            return;
        }
        BlockPos min = bounds[0];
        BlockPos max = bounds[1];
        int killSeconds = Config.BOUNDARY_KILL_SECONDS.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            if (!teamOf(uuid).isCombatant() || !player.isAlive()) {
                boundaryOutsideSeconds.remove(uuid);
                continue;
            }
            boolean inside = player.level().dimension() == dim
                    && player.getX() >= min.getX() && player.getX() < max.getX() + 1
                    && player.getY() >= min.getY() && player.getY() < max.getY() + 1
                    && player.getZ() >= min.getZ() && player.getZ() < max.getZ() + 1;
            if (inside) {
                boundaryOutsideSeconds.remove(uuid);
                continue;
            }
            int seconds = boundaryOutsideSeconds.merge(uuid, 1, Integer::sum);
            int remaining = killSeconds - seconds;
            if (remaining <= 0) {
                boundaryOutsideSeconds.remove(uuid);
                player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
            } else {
                player.displayClientMessage(Component.translatable("conquest.msg.boundary_warning", remaining)
                        .withStyle(ChatFormatting.RED), true);
            }
        }
    }

    // --- terrain destruction: protect zones and per-round restoration ---

    public Collection<ProtectZone> getProtectZones() {
        return protectZones.values();
    }

    /** Adds/replaces a named protect zone. */
    public void addProtectZone(String name, ServerLevel level, BlockPos pos1, BlockPos pos2) {
        protectZones.put(name, new ProtectZone(name, level.dimension(), pos1, pos2));
        setDirty();
    }

    /** Removes a protect zone. False if no zone has that name. */
    public boolean removeProtectZone(String name) {
        if (protectZones.remove(name) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    /** True if any protect zone in {@code dim} contains {@code pos} — terrain destruction must skip it. */
    public boolean isProtected(ResourceKey<Level> dim, BlockPos pos) {
        for (ProtectZone zone : protectZones.values()) {
            if (zone.getDim().equals(dim) && containsPos(zone.getMin(), zone.getMax(), pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsPos(BlockPos min, BlockPos max, BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /**
     * True if terrain destruction must never destroy this block type, whether from the
     * {@code indestructibleBlocks} config default or added in-game via {@link #addProtectedBlock}.
     */
    public boolean isIndestructible(BlockState state) {
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key == null) {
            return false;
        }
        String id = key.toString();
        return Config.INDESTRUCTIBLE_BLOCKS.get().contains(id) || protectedBlocks.contains(id);
    }

    /** Adds a block type to the in-game indestructible list. False if already present (config or in-game). */
    public boolean addProtectedBlock(String blockId) {
        if (Config.INDESTRUCTIBLE_BLOCKS.get().contains(blockId) || !protectedBlocks.add(blockId)) {
            return false;
        }
        setDirty();
        return true;
    }

    /** Removes a block type from the in-game indestructible list. False if it wasn't there (config-only blocks can't be removed this way). */
    public boolean removeProtectedBlock(String blockId) {
        if (!protectedBlocks.remove(blockId)) {
            return false;
        }
        setDirty();
        return true;
    }

    public Collection<String> getProtectedBlocks() {
        return protectedBlocks;
    }

    /**
     * Takes a whole-region snapshot of the battlefield boundary for later restoration by
     * {@link #restoreTerrainSnapshot}. No-op (leaves {@link #terrainSnapshot} null) if terrain
     * destruction is disabled or no boundary is set — automatic terrain reset is opt-in via
     * {@code /conquest boundary set}.
     */
    private void captureTerrainSnapshot(MinecraftServer server) {
        terrainSnapshot = null;
        terrainSnapshotDim = null;
        terrainSnapshotOrigin = null;
        if (!Config.TERRAIN_DESTRUCTION_ENABLED.get() || boundaryDim == null) {
            return;
        }
        BlockPos min = getBoundaryMin();
        BlockPos max = getBoundaryMax();
        if (min == null || max == null) {
            return;
        }
        ServerLevel level = server.getLevel(boundaryDim);
        if (level == null) {
            return;
        }
        Vec3i size = new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, min, size, false, null);
        terrainSnapshot = template;
        terrainSnapshotDim = boundaryDim;
        terrainSnapshotOrigin = min.immutable();
    }

    /**
     * Manually pastes back the current terrain snapshot, e.g. after {@code /conquest stop} — which,
     * unlike {@link #endRound}, deliberately skips the automatic restore so admins can inspect the
     * damage first. Returns false if no snapshot is held (never captured this round, boundary unset,
     * or already consumed by a prior restore/round end).
     */
    public boolean restoreTerrain(MinecraftServer server) {
        if (terrainSnapshot == null) {
            return false;
        }
        restoreTerrainSnapshot(server);
        return true;
    }

    /** Pastes back the snapshot taken by {@link #captureTerrainSnapshot}, if any, undoing all terrain changes since. */
    private void restoreTerrainSnapshot(MinecraftServer server) {
        if (terrainSnapshot == null || terrainSnapshotDim == null || terrainSnapshotOrigin == null) {
            return;
        }
        ServerLevel level = server.getLevel(terrainSnapshotDim);
        if (level != null) {
            terrainSnapshot.placeInWorld(level, terrainSnapshotOrigin, terrainSnapshotOrigin,
                    new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true), level.getRandom(), 3);
        }
        terrainSnapshot = null;
        terrainSnapshotDim = null;
        terrainSnapshotOrigin = null;
    }

    // --- map presets (named, reusable point/spawn/mode layouts) ---

    public Collection<String> getPresetNames() {
        return presets.keySet();
    }

    @Nullable
    public MapPreset getPreset(String name) {
        return presets.get(name);
    }

    /**
     * Snapshots the current points/spawns/mode/zones/boundary/protect zones/protected block types
     * as a named preset, overwriting any existing one of that name.
     */
    public void savePreset(String name) {
        List<MapPreset.PointLayout> layout = new ArrayList<>();
        for (CapturePoint point : points.values()) {
            layout.add(new MapPreset.PointLayout(point.getName(), point.getDimension(), point.getPos(), point.getRadius()));
        }
        MapPreset.ZoneBox zoneABox = zoneADim != null && zoneAPos1 != null && zoneAPos2 != null
                ? new MapPreset.ZoneBox(zoneADim, zoneAPos1, zoneAPos2) : null;
        MapPreset.ZoneBox zoneBBox = zoneBDim != null && zoneBPos1 != null && zoneBPos2 != null
                ? new MapPreset.ZoneBox(zoneBDim, zoneBPos1, zoneBPos2) : null;
        MapPreset.ZoneBox boundaryBox = boundaryDim != null && boundaryPos1 != null && boundaryPos2 != null
                ? new MapPreset.ZoneBox(boundaryDim, boundaryPos1, boundaryPos2) : null;
        presets.put(name, new MapPreset(name, mode, layout, spawnADim, spawnAPos, spawnBDim, spawnBPos,
                zoneABox, zoneBBox, boundaryBox, new ArrayList<>(protectZones.values()),
                new ArrayList<>(protectedBlocks)));
        setDirty();
    }

    /** Outcome of a /conquest preset load attempt. */
    public enum LoadPresetResult { OK, NOT_FOUND, ROUND_ACTIVE }

    /**
     * Replaces the live points/spawns/mode/zones/boundary/protect zones with a saved preset.
     * Rejected while a round is running or showing a result, same rule as {@link #setMode}.
     * Rebuilds flag poles for every point (clearing the previous ones first) in whichever
     * dimensions are loaded.
     */
    public LoadPresetResult loadPreset(MinecraftServer server, String name) {
        MapPreset preset = presets.get(name);
        if (preset == null) {
            return LoadPresetResult.NOT_FOUND;
        }
        if (state != RoundState.WAITING) {
            return LoadPresetResult.ROUND_ACTIVE;
        }
        for (CapturePoint point : points.values()) {
            ServerLevel level = server.getLevel(point.getDimension());
            if (level != null) {
                FlagPole.remove(level, point);
            }
        }
        points.clear();
        for (MapPreset.PointLayout layout : preset.getPoints()) {
            CapturePoint point = new CapturePoint(layout.name(), layout.dimension(), layout.pos(), layout.radius());
            points.put(layout.name(), point);
            ServerLevel level = server.getLevel(layout.dimension());
            if (level != null) {
                FlagPole.build(level, point);
            }
        }
        spawnADim = preset.getSpawnADim();
        spawnAPos = preset.getSpawnAPos();
        spawnBDim = preset.getSpawnBDim();
        spawnBPos = preset.getSpawnBPos();
        mode = preset.getMode();

        MapPreset.ZoneBox zoneABox = preset.getZoneA();
        zoneADim = zoneABox != null ? zoneABox.dimension() : null;
        zoneAPos1 = zoneABox != null ? zoneABox.pos1() : null;
        zoneAPos2 = zoneABox != null ? zoneABox.pos2() : null;
        MapPreset.ZoneBox zoneBBox = preset.getZoneB();
        zoneBDim = zoneBBox != null ? zoneBBox.dimension() : null;
        zoneBPos1 = zoneBBox != null ? zoneBBox.pos1() : null;
        zoneBPos2 = zoneBBox != null ? zoneBBox.pos2() : null;
        MapPreset.ZoneBox boundaryBox = preset.getBoundary();
        boundaryDim = boundaryBox != null ? boundaryBox.dimension() : null;
        boundaryPos1 = boundaryBox != null ? boundaryBox.pos1() : null;
        boundaryPos2 = boundaryBox != null ? boundaryBox.pos2() : null;
        protectZones.clear();
        for (ProtectZone zone : preset.getProtectZones()) {
            protectZones.put(zone.getName(), zone);
        }
        protectedBlocks.clear();
        protectedBlocks.addAll(preset.getProtectedBlocks());

        setDirty();
        return LoadPresetResult.OK;
    }

    public boolean removePreset(String name) {
        if (presets.remove(name) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    /** Outcome of a /conquest start attempt, used to pick the right failure message. */
    public enum StartResult { OK, ALREADY_RUNNING, RESULT_PENDING, NO_POINT, NO_SECTOR, TEAM_A_EMPTY, TEAM_B_EMPTY }

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
        if (mode == GameMode.BREAKTHROUGH && sectors.isEmpty()) {
            return StartResult.NO_SECTOR;
        }
        if (onlineCount(server, Team.A) == 0) {
            return StartResult.TEAM_A_EMPTY;
        }
        if (onlineCount(server, Team.B) == 0) {
            return StartResult.TEAM_B_EMPTY;
        }

        captureTerrainSnapshot(server);

        if (mode == GameMode.CONQUEST || mode == GameMode.BREAKTHROUGH) {
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
        pendingAttackerRespawns.clear();
        zoneIntrusionSeconds.clear();
        boundaryOutsideSeconds.clear();
        spotCooldownUntilTick.clear();
        sectorAreaGraceSecondsRemaining = 0;
        teamBeacons.clear();
        if (mode == GameMode.BREAKTHROUGH) {
            activeSectorNumber = sectors.firstKey();
            attackerTickets = Config.BT_ATTACKER_TICKETS.get();
            respawnWaveSecondsRemaining = Config.BT_RESPAWN_WAVE_INTERVAL_SECONDS.get();
            sectorSecondsRemaining = currentSectorTimeLimit();
        } else {
            activeSectorNumber = 0;
        }
        teleportToSpawns(server);
        if (mode != GameMode.CONQUEST) {
            SquadManager.get(server).setFeatureEnabled(SquadFeature.REVIVE, false);
        }

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
        if (mode == GameMode.BREAKTHROUGH) {
            broadcast(server, Component.translatable("conquest.msg.started_breakthrough", attackerTickets)
                    .withStyle(ChatFormatting.GOLD));
            broadcastTitle(server, Component.translatable("conquest.title.started_breakthrough").withStyle(ChatFormatting.GOLD),
                    Component.translatable("conquest.title.started_breakthrough_sub", attackerTickets));
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
            applyMaxHealth(player, team);
            teleportToRoleSpawn(player, team);
        }
    }

    /** A team's configured role spawn, before the world-spawn fallback used at actual teleport time. */
    record RoleSpawn(ResourceKey<Level> dim, BlockPos pos) {
        boolean isSet() {
            return dim != null && pos != null;
        }
    }

    /** In breakthrough, the active sector's attacker/defender spawn if set; else the global spawnA/B. */
    RoleSpawn resolveRoleSpawn(Team team) {
        Sector sector = mode == GameMode.BREAKTHROUGH ? currentSector() : null;
        if (sector != null) {
            RoleSpawn sectorSpawn = team == attackerTeam
                    ? new RoleSpawn(sector.getAttackerSpawnDim(), sector.getAttackerSpawnPos())
                    : new RoleSpawn(sector.getDefenderSpawnDim(), sector.getDefenderSpawnPos());
            if (sectorSpawn.isSet()) {
                return sectorSpawn;
            }
        }
        return team == Team.A ? new RoleSpawn(spawnADim, spawnAPos) : new RoleSpawn(spawnBDim, spawnBPos);
    }

    /**
     * Teleports one player to their role's spawn ({@link #resolveRoleSpawn}), or the world spawn
     * if unset. Used both at round start and as the "team spawn" respawn choice offered via
     * {@link ConquestRespawnChoiceProvider}.
     */
    void teleportToRoleSpawn(ServerPlayer player, Team team) {
        MinecraftServer server = player.server;
        RoleSpawn roleSpawn = resolveRoleSpawn(team);
        ResourceKey<Level> dim = roleSpawn.dim();
        BlockPos pos = roleSpawn.pos();
        ServerLevel targetLevel;
        if (dim == null || pos == null) {
            targetLevel = server.overworld();
            pos = targetLevel.getSharedSpawnPos();
        } else {
            targetLevel = server.getLevel(dim);
            if (targetLevel == null) {
                targetLevel = server.overworld();
                pos = targetLevel.getSharedSpawnPos();
            }
        }
        BlockPos safe = TeleportHelper.findSafeSpot(targetLevel, pos);
        player.teleportTo(targetLevel, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
    }

    // --- team respawn beacon ---

    /** One-per-team active beacon: expires after teamBeaconLifetimeSeconds, not persisted across restarts. */
    private static final class TeamBeacon {
        final ResourceKey<Level> dim;
        final BlockPos pos;
        int secondsRemaining;

        TeamBeacon(ResourceKey<Level> dim, BlockPos pos, int secondsRemaining) {
            this.dim = dim;
            this.pos = pos;
            this.secondsRemaining = secondsRemaining;
        }
    }

    private final Map<Team, TeamBeacon> teamBeacons = new EnumMap<>(Team.class);

    /** Places (or replaces) {@code team}'s respawn beacon at {@code pos}, active for teamBeaconLifetimeSeconds. */
    public void placeTeamBeacon(Team team, ServerLevel level, BlockPos pos) {
        teamBeacons.put(team, new TeamBeacon(level.dimension(), pos.immutable(), Config.TEAM_BEACON_LIFETIME_SECONDS.get()));
    }

    @Nullable
    public ResourceKey<Level> getTeamBeaconDim(Team team) {
        TeamBeacon beacon = teamBeacons.get(team);
        return beacon == null ? null : beacon.dim;
    }

    @Nullable
    public BlockPos getTeamBeaconPos(Team team) {
        TeamBeacon beacon = teamBeacons.get(team);
        return beacon == null ? null : beacon.pos;
    }

    /**
     * Teleports the player to their team's active respawn beacon, if any. False (no-op) if none
     * is active. Called from {@link ConquestRespawnChoiceProvider#onChosen} when the player picks
     * the beacon option in squadtp's respawn chooser.
     */
    boolean teleportToTeamBeacon(ServerPlayer player, Team team) {
        TeamBeacon beacon = teamBeacons.get(team);
        if (beacon == null) {
            return false;
        }
        ServerLevel level = player.server.getLevel(beacon.dim);
        if (level == null) {
            return false;
        }
        BlockPos safe = TeleportHelper.findSafeSpot(level, beacon.pos);
        player.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
        return true;
    }

    /**
     * Teleports the player to a specific capture point. False (no-op) if its dimension isn't
     * loaded. Called from {@link ConquestRespawnChoiceProvider#onChosen} when the player picks
     * that point in squadtp's respawn chooser.
     */
    boolean teleportToPoint(ServerPlayer player, CapturePoint point) {
        ServerLevel level = player.server.getLevel(point.getDimension());
        if (level == null) {
            return false;
        }
        BlockPos safe = TeleportHelper.findSafeSpot(level, point.getPos());
        player.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
        return true;
    }

    /** Once per second while a round is running: counts down every active beacon, clearing expired ones. */
    private void tickTeamBeacons() {
        teamBeacons.values().removeIf(beacon -> --beacon.secondsRemaining <= 0);
    }

    /** Forced end with no winner, or cancels a pending countdown: valid from STARTING or IN_PROGRESS. */
    public boolean stop(MinecraftServer server) {
        if (state != RoundState.STARTING && state != RoundState.IN_PROGRESS) {
            return false;
        }
        boolean wasStarting = state == RoundState.STARTING;
        state = RoundState.WAITING;
        setDirty();
        if (mode != GameMode.CONQUEST) {
            SquadManager.get(server).setFeatureEnabled(SquadFeature.REVIVE, true);
        }
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
     * False if the opposing team currently has anyone alive inside {@code point}'s capture
     * radius — i.e. the point is contested (both teams present) or actively being torn down by
     * the enemy alone. Used to keep contested/threatened points off the respawn choice list (see
     * {@link ConquestRespawnChoiceProvider}); has nothing to do with capture progress itself.
     */
    public boolean isPointSpawnSafe(MinecraftServer server, CapturePoint point) {
        PointOccupancy occ = computeOccupancy(server, point);
        Team enemy = point.getOwner().opponent();
        int enemyCount = enemy == Team.A ? occ.countA() : occ.countB();
        return enemyCount == 0;
    }

    /**
     * Runs one point's capture tick: occupancy, flag advance/neutralize/capture, flag-pole
     * recolor. Shared by conquest (every point) and breakthrough (only the active sector's
     * points). Null if the point's dimension isn't loaded.
     */
    @Nullable
    private PointOccupancy tickOnePoint(MinecraftServer server, CapturePoint point) {
        ServerLevel level = server.getLevel(point.getDimension());
        if (level == null) {
            return null;
        }
        // Downed players (squadtp revive system) cannot capture.
        PointOccupancy occ = computeOccupancy(server, point);

        // One team alone advances; both present = contested; empty = hold.
        if (occ.countA() > 0 ^ occ.countB() > 0) {
            Team holder = occ.countA() > 0 ? Team.A : Team.B;
            CapturePoint.CaptureEvent event = point.advance(holder, Config.CAPTURE_RATE_PER_SECOND.get());
            setDirty();
            if (event == CapturePoint.CaptureEvent.CAPTURED) {
                broadcast(server, Component.translatable("conquest.msg.captured",
                        holder.display(), point.getName()).withStyle(ChatFormatting.GOLD));
                if (mode == GameMode.BREAKTHROUGH && holder == attackerTeam) {
                    sectorSecondsRemaining += Config.BT_SECTOR_TIME_EXTENSION_ON_CAPTURE.get();
                }
            } else if (event == CapturePoint.CaptureEvent.NEUTRALIZED) {
                broadcast(server, Component.translatable("conquest.msg.neutralized", point.getName())
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
        FlagPole.update(level, point);
        return occ;
    }

    /** Breakthrough per-second logic: active sector's capture points, sector clock, respawn waves, win checks. */
    private void tickBreakthrough(MinecraftServer server, Map<String, PointOccupancy> occupancyByPoint) {
        Sector sector = currentSector();
        if (sector != null) {
            for (String name : sector.getPointNames()) {
                CapturePoint point = points.get(name);
                if (point == null) {
                    continue;
                }
                PointOccupancy occ = tickOnePoint(server, point);
                if (occ != null) {
                    occupancyByPoint.put(name, occ);
                }
            }
            boolean cleared = sector.getPointNames().stream()
                    .map(points::get)
                    .filter(Objects::nonNull)
                    .allMatch(p -> p.getOwner() == attackerTeam);
            if (cleared) {
                advanceSector(server);
            }
        }

        if (state == RoundState.IN_PROGRESS && --sectorSecondsRemaining <= 0) {
            endRound(server, defenderTeam());
        }

        if (state == RoundState.IN_PROGRESS && --respawnWaveSecondsRemaining <= 0) {
            respawnWaveSecondsRemaining = Config.BT_RESPAWN_WAVE_INTERVAL_SECONDS.get();
            releaseAttackerWave(server);
        }

        if (state == RoundState.IN_PROGRESS && attackerTickets <= 0
                && pendingAttackerRespawns.isEmpty() && countAliveAttackers(server) == 0) {
            endRound(server, defenderTeam());
        }
        setDirty();
    }

    /** Clears the active sector and moves to the next one, or ends the round if that was the last. */
    private void advanceSector(MinecraftServer server) {
        Integer next = sectors.higherKey(activeSectorNumber);
        if (next == null) {
            endRound(server, attackerTeam);
            return;
        }
        int clearedNumber = activeSectorNumber;
        activeSectorNumber = next;
        sectorSecondsRemaining = currentSectorTimeLimit();
        sectorAreaGraceSecondsRemaining = Config.BT_SECTOR_AREA_TRANSITION_GRACE_SECONDS.get();
        int ticketBonus = Config.BT_TICKETS_PER_SECTOR_CAPTURE.get();
        attackerTickets += ticketBonus;
        setDirty();
        broadcast(server, Component.translatable("conquest.msg.sector_cleared", clearedNumber, sectorIndex(), sectorCount(), ticketBonus)
                .withStyle(ChatFormatting.GOLD));
    }

    /** Sends every attacker still waiting (as a spectator) since their last death back into the fight. */
    private void releaseAttackerWave(MinecraftServer server) {
        if (pendingAttackerRespawns.isEmpty()) {
            return;
        }
        for (UUID uuid : List.copyOf(pendingAttackerRespawns)) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null || !player.isSpectator()) {
                continue; // still on the death screen; stays queued for the next wave
            }
            player.setGameMode(GameType.SURVIVAL);
            teleportToRoleSpawn(player, attackerTeam);
            pendingAttackerRespawns.remove(uuid);
        }
        setDirty();
    }

    private int countAliveAttackers(MinecraftServer server) {
        int count = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (teamOf(player.getUUID()) == attackerTeam && player.isAlive() && !player.isSpectator()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Called from {@link uk.iwaservice.squadtpconquest.ScoreEvents#onDeath} for every death
     * while a breakthrough round is running. Attacker deaths consume one ticket and queue the
     * player for the next respawn wave; once tickets run out, deaths are permanent for the
     * round. Defender deaths are unlimited and not tracked here.
     */
    public void handleBreakthroughDeath(UUID victim) {
        if (state != RoundState.IN_PROGRESS || mode != GameMode.BREAKTHROUGH || teamOf(victim) != attackerTeam) {
            return;
        }
        if (attackerTickets > 0) {
            attackerTickets--;
            pendingAttackerRespawns.add(victim);
            setDirty();
        }
    }

    /**
     * Runs capture/ticket/win-condition logic for every point while IN_PROGRESS,
     * advances the result auto-reset countdown while ENDED, then always
     * broadcasts a fresh snapshot to every player so the GUI/HUD show live
     * data at any time.
     */
    public void tickSecond(MinecraftServer server) {
        tickRange(server);

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
                    PointOccupancy occ = tickOnePoint(server, point);
                    if (occ != null) {
                        occupancyByPoint.put(point.getName(), occ);
                    }
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
            } else if (mode == GameMode.BREAKTHROUGH) {
                tickBreakthrough(server, occupancyByPoint);
            }

            checkRevives(server);
            tickHomeZones(server);
            tickBoundary(server);
            tickTeamBeacons();

            // Team-empty check (only if the round is still running after the checks above).
            if (state == RoundState.IN_PROGRESS && Config.END_ON_TEAM_EMPTY.get()) {
                if (onlineCount(server, Team.A) == 0) {
                    endRound(server, Team.B);
                } else if (onlineCount(server, Team.B) == 0) {
                    endRound(server, Team.A);
                }
            }

            // Time limit: higher tickets win, equal tickets draw. Breakthrough has its own
            // sector-timer-based ending instead (ticketsA/B are unused in that mode).
            int limit = Config.ROUND_TIME_LIMIT_SECONDS.get();
            if (state == RoundState.IN_PROGRESS && mode != GameMode.BREAKTHROUGH && limit > 0 && roundElapsedSeconds >= limit) {
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

        if (mode != GameMode.CONQUEST) {
            SquadManager.get(server).setFeatureEnabled(SquadFeature.REVIVE, true);
        }

        Component title;
        Component subtitle = mode == GameMode.BREAKTHROUGH
                ? Component.translatable("conquest.title.result_breakthrough",
                        sectorIndex(), sectorCount(), Math.max(0, attackerTickets))
                : Component.translatable("conquest.title.result_tickets", ticketsA, ticketsB);
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
        teleportToGatherPoint(server);
        restoreTerrainSnapshot(server);

        // No per-participant filtering is exposed by squadtp's public API, so
        // this clears downed/revive state server-wide rather than just for
        // this round's players.
        ReviveSystem.clear();
    }

    /** If a gather point is set, teleports every combatant there (no-op otherwise). */
    private void teleportToGatherPoint(MinecraftServer server) {
        if (gatherDim == null || gatherPos == null) {
            return;
        }
        ServerLevel level = server.getLevel(gatherDim);
        if (level == null) {
            return;
        }
        BlockPos safe = TeleportHelper.findSafeSpot(level, gatherPos);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (teamOf(player.getUUID()).isCombatant()) {
                player.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                        Set.of(), player.getYRot(), player.getXRot());
            }
        }
    }

    /**
     * Conquest: charges the respawning player's own team a ticket (Battlefield-style:
     * deaths cost reinforcements). TDM doesn't use this (its ticket counters instead count
     * kills upward toward the kill limit). Breakthrough handles respawn placement itself,
     * since it's asymmetric (wave-gated attackers, immediate defenders) — see
     * {@link #handleBreakthroughRespawn}. The training range respawns back into its own area,
     * independent of round state (unlike everything else below, gated on IN_PROGRESS).
     */
    public void onRespawn(ServerPlayer player) {
        if (teamOf(player.getUUID()) == Team.RANGE) {
            teleportIntoRange(player);
            return;
        }
        if (state != RoundState.IN_PROGRESS) {
            return;
        }
        Team team = teamOf(player.getUUID());
        if (!team.isCombatant()) {
            return;
        }
        applyMaxHealth(player, team);
        if (mode == GameMode.CONQUEST) {
            int cost = Config.TICKET_COST_PER_RESPAWN.get();
            if (cost > 0) {
                drainTickets(player.server, team, cost);
            }
        } else if (mode == GameMode.BREAKTHROUGH) {
            handleBreakthroughRespawn(player, team);
        }
        // Conquest and TDM don't otherwise teleport on respawn (only at round start). The team
        // beacon and (in conquest) owned points are offered as player choices via
        // ConquestRespawnChoiceProvider instead of being placed automatically here.
    }

    /**
     * Attackers who haven't been released by the current respawn wave yet are held as
     * spectators until {@link #releaseAttackerWave} sends them back in. Defenders respawn
     * immediately at the active sector's defender line (falling back with the front line).
     */
    private void handleBreakthroughRespawn(ServerPlayer player, Team team) {
        if (team == attackerTeam) {
            if (pendingAttackerRespawns.contains(player.getUUID())) {
                player.setGameMode(GameType.SPECTATOR);
            }
        } else {
            teleportToRoleSpawn(player, team);
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
        Sector active = currentSector();
        for (CapturePoint point : points.values()) {
            PointOccupancy occ = occupancyByPoint.getOrDefault(point.getName(), EMPTY_OCCUPANCY);
            boolean pointActive = mode != GameMode.BREAKTHROUGH
                    || (active != null && active.getPointNames().contains(point.getName()));
            statuses.add(new ConquestSyncPacket.PointStatus(point.getName(), point.getRadius(), point.getOwner(),
                    point.getCapturingTeam(), point.getFlagLevel(), occ.contested(),
                    occ.inZone().contains(viewer.getUUID()), pointActive, sectorNumberOf(point.getName()),
                    point.getDimension().location(), point.getPos()));
        }
        List<ConquestSyncPacket.CallInStatus> callInStatuses = new ArrayList<>();
        for (CallIn callIn : callIns.values()) {
            callInStatuses.add(new ConquestSyncPacket.CallInStatus(
                    callIn.getName(), callIn.getScoreCost(), callIn.getItemId(), callIn.getCount()));
        }
        return new ConquestSyncPacket(statuses, ticketsA, ticketsB, isActive(), state, mode,
                teamOf(viewer.getUUID()), viewer.hasPermissions(2), openScreen,
                attackerTeam, sectorIndex(), sectorCount(), attackerTickets, respawnWaveSecondsRemaining,
                callInStatuses, availableScore(viewer.getUUID()));
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
        if (tag.contains("GatherDim")) {
            manager.gatherDim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("GatherDim")));
            manager.gatherPos = NbtUtils.readBlockPos(tag.getCompound("GatherPos"));
        }
        if (tag.contains("ZoneADim")) {
            manager.zoneADim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("ZoneADim")));
            if (tag.contains("ZoneAPos1")) {
                manager.zoneAPos1 = NbtUtils.readBlockPos(tag.getCompound("ZoneAPos1"));
            }
            if (tag.contains("ZoneAPos2")) {
                manager.zoneAPos2 = NbtUtils.readBlockPos(tag.getCompound("ZoneAPos2"));
            }
        }
        if (tag.contains("ZoneBDim")) {
            manager.zoneBDim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("ZoneBDim")));
            if (tag.contains("ZoneBPos1")) {
                manager.zoneBPos1 = NbtUtils.readBlockPos(tag.getCompound("ZoneBPos1"));
            }
            if (tag.contains("ZoneBPos2")) {
                manager.zoneBPos2 = NbtUtils.readBlockPos(tag.getCompound("ZoneBPos2"));
            }
        }
        if (tag.contains("BoundaryDim")) {
            manager.boundaryDim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("BoundaryDim")));
            if (tag.contains("BoundaryPos1")) {
                manager.boundaryPos1 = NbtUtils.readBlockPos(tag.getCompound("BoundaryPos1"));
            }
            if (tag.contains("BoundaryPos2")) {
                manager.boundaryPos2 = NbtUtils.readBlockPos(tag.getCompound("BoundaryPos2"));
            }
        }
        if (tag.contains("RangeDim")) {
            manager.rangeDim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("RangeDim")));
            if (tag.contains("RangePos1")) {
                manager.rangePos1 = NbtUtils.readBlockPos(tag.getCompound("RangePos1"));
            }
            if (tag.contains("RangePos2")) {
                manager.rangePos2 = NbtUtils.readBlockPos(tag.getCompound("RangePos2"));
            }
        }
        ListTag scoreList = tag.getList("Scores", Tag.TAG_COMPOUND);
        for (int i = 0; i < scoreList.size(); i++) {
            CompoundTag s = scoreList.getCompound(i);
            PlayerScore score = new PlayerScore();
            score.kills = s.getInt("Kills");
            score.deaths = s.getInt("Deaths");
            score.assists = s.getInt("Assists");
            score.revives = s.getInt("Revives");
            score.spent = s.getInt("Spent");
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
        ListTag presetList = tag.getList("Presets", Tag.TAG_COMPOUND);
        for (int i = 0; i < presetList.size(); i++) {
            MapPreset preset = MapPreset.load(presetList.getCompound(i));
            manager.presets.put(preset.getName(), preset);
        }
        ListTag sectorList = tag.getList("Sectors", Tag.TAG_COMPOUND);
        for (int i = 0; i < sectorList.size(); i++) {
            Sector sector = Sector.load(sectorList.getCompound(i));
            manager.sectors.put(sector.getNumber(), sector);
        }
        ListTag protectZoneList = tag.getList("ProtectZones", Tag.TAG_COMPOUND);
        for (int i = 0; i < protectZoneList.size(); i++) {
            ProtectZone zone = ProtectZone.load(protectZoneList.getCompound(i));
            manager.protectZones.put(zone.getName(), zone);
        }
        ListTag protectedBlockList = tag.getList("ProtectedBlocks", Tag.TAG_STRING);
        for (int i = 0; i < protectedBlockList.size(); i++) {
            manager.protectedBlocks.add(protectedBlockList.getString(i));
        }
        ListTag callInList = tag.getList("CallIns", Tag.TAG_COMPOUND);
        for (int i = 0; i < callInList.size(); i++) {
            CallIn callIn = CallIn.load(callInList.getCompound(i));
            manager.callIns.put(callIn.getName(), callIn);
        }
        manager.attackerTeam = tag.contains("AttackerTeam") ? Team.valueOf(tag.getString("AttackerTeam")) : Team.A;
        manager.activeSectorNumber = tag.getInt("ActiveSectorNumber");
        manager.attackerTickets = tag.getInt("AttackerTickets");
        manager.sectorSecondsRemaining = tag.getInt("SectorSecondsRemaining");
        manager.respawnWaveSecondsRemaining = tag.getInt("RespawnWaveSecondsRemaining");
        ListTag pendingList = tag.getList("PendingAttackerRespawns", Tag.TAG_COMPOUND);
        for (int i = 0; i < pendingList.size(); i++) {
            manager.pendingAttackerRespawns.add(pendingList.getCompound(i).getUUID("Uuid"));
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
        if (gatherDim != null && gatherPos != null) {
            tag.putString("GatherDim", gatherDim.location().toString());
            tag.put("GatherPos", NbtUtils.writeBlockPos(gatherPos));
        }
        if (spawnBDim != null && spawnBPos != null) {
            tag.putString("SpawnBDim", spawnBDim.location().toString());
            tag.put("SpawnBPos", NbtUtils.writeBlockPos(spawnBPos));
        }
        if (zoneADim != null) {
            tag.putString("ZoneADim", zoneADim.location().toString());
            if (zoneAPos1 != null) {
                tag.put("ZoneAPos1", NbtUtils.writeBlockPos(zoneAPos1));
            }
            if (zoneAPos2 != null) {
                tag.put("ZoneAPos2", NbtUtils.writeBlockPos(zoneAPos2));
            }
        }
        if (zoneBDim != null) {
            tag.putString("ZoneBDim", zoneBDim.location().toString());
            if (zoneBPos1 != null) {
                tag.put("ZoneBPos1", NbtUtils.writeBlockPos(zoneBPos1));
            }
            if (zoneBPos2 != null) {
                tag.put("ZoneBPos2", NbtUtils.writeBlockPos(zoneBPos2));
            }
        }
        if (boundaryDim != null) {
            tag.putString("BoundaryDim", boundaryDim.location().toString());
            if (boundaryPos1 != null) {
                tag.put("BoundaryPos1", NbtUtils.writeBlockPos(boundaryPos1));
            }
            if (boundaryPos2 != null) {
                tag.put("BoundaryPos2", NbtUtils.writeBlockPos(boundaryPos2));
            }
        }
        if (rangeDim != null) {
            tag.putString("RangeDim", rangeDim.location().toString());
            if (rangePos1 != null) {
                tag.put("RangePos1", NbtUtils.writeBlockPos(rangePos1));
            }
            if (rangePos2 != null) {
                tag.put("RangePos2", NbtUtils.writeBlockPos(rangePos2));
            }
        }
        ListTag scoreList = new ListTag();
        for (Map.Entry<UUID, PlayerScore> e : scores.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putUUID("Uuid", e.getKey());
            s.putInt("Kills", e.getValue().kills);
            s.putInt("Deaths", e.getValue().deaths);
            s.putInt("Assists", e.getValue().assists);
            s.putInt("Revives", e.getValue().revives);
            s.putInt("Spent", e.getValue().spent);
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
        ListTag presetList = new ListTag();
        for (MapPreset preset : presets.values()) {
            presetList.add(preset.save());
        }
        tag.put("Presets", presetList);
        ListTag sectorList = new ListTag();
        for (Sector sector : sectors.values()) {
            sectorList.add(sector.save());
        }
        tag.put("Sectors", sectorList);
        ListTag protectZoneList = new ListTag();
        for (ProtectZone zone : protectZones.values()) {
            protectZoneList.add(zone.save());
        }
        tag.put("ProtectZones", protectZoneList);
        ListTag protectedBlockList = new ListTag();
        for (String id : protectedBlocks) {
            protectedBlockList.add(StringTag.valueOf(id));
        }
        tag.put("ProtectedBlocks", protectedBlockList);
        ListTag callInList = new ListTag();
        for (CallIn callIn : callIns.values()) {
            callInList.add(callIn.save());
        }
        tag.put("CallIns", callInList);
        tag.putString("AttackerTeam", attackerTeam.name());
        tag.putInt("ActiveSectorNumber", activeSectorNumber);
        tag.putInt("AttackerTickets", attackerTickets);
        tag.putInt("SectorSecondsRemaining", sectorSecondsRemaining);
        tag.putInt("RespawnWaveSecondsRemaining", respawnWaveSecondsRemaining);
        ListTag pendingList = new ListTag();
        for (UUID uuid : pendingAttackerRespawns) {
            CompoundTag p = new CompoundTag();
            p.putUUID("Uuid", uuid);
            pendingList.add(p);
        }
        tag.put("PendingAttackerRespawns", pendingList);
        return tag;
    }
}
