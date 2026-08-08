package uk.iwaservice.squadtpconquest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;
import uk.iwaservice.squadtp.squad.Squad;
import uk.iwaservice.squadtp.squad.SquadManager;
import uk.iwaservice.squadtpconquest.Config;
import uk.iwaservice.squadtpconquest.conquest.CallIn;
import uk.iwaservice.squadtpconquest.conquest.CapturePoint;
import uk.iwaservice.squadtpconquest.conquest.ConquestManager;
import uk.iwaservice.squadtpconquest.conquest.GameMode;
import uk.iwaservice.squadtpconquest.conquest.MapPreset;
import uk.iwaservice.squadtpconquest.conquest.ProtectZone;
import uk.iwaservice.squadtpconquest.conquest.RoundState;
import uk.iwaservice.squadtpconquest.conquest.Sector;
import uk.iwaservice.squadtpconquest.conquest.Team;
import uk.iwaservice.squadtpconquest.conquest.ZoneSelection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * /conquest command tree. Like squadtp, every conquest operation enters the
 * server exclusively through here — no custom C2S packets.
 */
public final class ConquestCommand {

    /** One adjustable server-config value: parses a raw command argument and reports its current value. */
    private record ConfigEntry(Function<String, Boolean> setter, Supplier<String> getter) {}

    private static final Map<String, ConfigEntry> CONFIG_KEYS = new LinkedHashMap<>();

    static {
        CONFIG_KEYS.put("captureRadius", intEntry(Config.CAPTURE_RADIUS));
        CONFIG_KEYS.put("captureRatePerSecond", doubleEntry(Config.CAPTURE_RATE_PER_SECOND));
        CONFIG_KEYS.put("ticketBleedInterval", intEntry(Config.TICKET_BLEED_INTERVAL));
        CONFIG_KEYS.put("ticketBleedAmount", intEntry(Config.TICKET_BLEED_AMOUNT));
        CONFIG_KEYS.put("startingTickets", intEntry(Config.STARTING_TICKETS));
        CONFIG_KEYS.put("roundTimeLimitSeconds", intEntry(Config.ROUND_TIME_LIMIT_SECONDS));
        CONFIG_KEYS.put("resultDisplaySeconds", intEntry(Config.RESULT_DISPLAY_SECONDS));
        CONFIG_KEYS.put("endOnTeamEmpty", boolEntry(Config.END_ON_TEAM_EMPTY));
        CONFIG_KEYS.put("autoResetAfterResult", boolEntry(Config.AUTO_RESET_AFTER_RESULT));
        CONFIG_KEYS.put("ticketCostPerRespawn", intEntry(Config.TICKET_COST_PER_RESPAWN));
        CONFIG_KEYS.put("maxHealth", doubleEntry(Config.MAX_HEALTH));
        CONFIG_KEYS.put("startCountdownSeconds", intEntry(Config.START_COUNTDOWN_SECONDS));
        CONFIG_KEYS.put("tdmKillLimit", intEntry(Config.TDM_KILL_LIMIT));
        CONFIG_KEYS.put("assistWindowSeconds", intEntry(Config.ASSIST_WINDOW_SECONDS));
        CONFIG_KEYS.put("scorePerKill", intEntry(Config.SCORE_PER_KILL));
        CONFIG_KEYS.put("scorePerAssist", intEntry(Config.SCORE_PER_ASSIST));
        CONFIG_KEYS.put("scorePerRevive", intEntry(Config.SCORE_PER_REVIVE));
    }

    private static ConfigEntry intEntry(ForgeConfigSpec.IntValue value) {
        return new ConfigEntry(raw -> {
            try {
                value.set(Integer.parseInt(raw));
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }, () -> String.valueOf(value.get()));
    }

    private static ConfigEntry doubleEntry(ForgeConfigSpec.DoubleValue value) {
        return new ConfigEntry(raw -> {
            try {
                value.set(Double.parseDouble(raw));
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }, () -> String.valueOf(value.get()));
    }

    private static ConfigEntry boolEntry(ForgeConfigSpec.BooleanValue value) {
        return new ConfigEntry(raw -> {
            if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
                return false;
            }
            value.set(Boolean.parseBoolean(raw));
            return true;
        }, () -> String.valueOf(value.get()));
    }

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> POINT_NAMES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    ConquestManager.get(ctx.getSource().getServer()).getPoints().stream()
                            .map(CapturePoint::getName), builder);

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> PRESET_NAMES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    ConquestManager.get(ctx.getSource().getServer()).getPresetNames(), builder);

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> PROTECT_ZONE_NAMES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    ConquestManager.get(ctx.getSource().getServer()).getProtectZones().stream()
                            .map(ProtectZone::getName), builder);

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> CALLIN_NAMES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    ConquestManager.get(ctx.getSource().getServer()).getCallIns().stream()
                            .map(CallIn::getName), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("conquest")
                .then(Commands.literal("team")
                        .then(Commands.literal("join")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"a", "b", "admin"}, b))
                                        .executes(ConquestCommand::joinTeam)
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .requires(src -> src.hasPermission(2))
                                                .executes(ConquestCommand::joinTeamOther))))
                        .then(Commands.literal("shuffle")
                                .requires(src -> src.hasPermission(2))
                                .executes(ConquestCommand::shuffleTeams))
                        .then(Commands.literal("assign")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"attacker", "defender"}, b))
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"a", "b"}, b))
                                                .executes(ConquestCommand::teamAssign)))))
                .then(Commands.literal("spot")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ConquestCommand::spot)))
                .then(Commands.literal("point")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("set")
                                .executes(ctx -> setPoint(ctx, Config.CAPTURE_RADIUS.get()))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(2, 64))
                                        .executes(ctx -> setPoint(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> addPoint(ctx, Config.CAPTURE_RADIUS.get()))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(2, 64))
                                                .executes(ctx -> addPoint(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(POINT_NAMES)
                                        .executes(ConquestCommand::removePoint)))
                        .then(Commands.literal("list").executes(ConquestCommand::pointList)))
                .then(Commands.literal("spawn")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"a", "b"}, b))
                                        .executes(ConquestCommand::setSpawn))))
                .then(Commands.literal("gather")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("set").executes(ConquestCommand::setGatherPoint))
                        .then(Commands.literal("remove").executes(ConquestCommand::removeGatherPoint))
                        .then(Commands.literal("list").executes(ConquestCommand::gatherList)))
                .then(Commands.literal("zone")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"a", "b"}, b))
                                        .executes(ConquestCommand::setZoneFromWand)
                                        .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                        .executes(ConquestCommand::setZone)))))
                        .then(Commands.literal("corner1")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"a", "b"}, b))
                                                .executes(ctx -> setZoneCorner(ctx, true)))))
                        .then(Commands.literal("corner2")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"a", "b"}, b))
                                                .executes(ctx -> setZoneCorner(ctx, false)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"a", "b"}, b))
                                        .executes(ConquestCommand::removeZone)))
                        .then(Commands.literal("list").executes(ConquestCommand::zoneList)))
                .then(Commands.literal("protectzone")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ConquestCommand::protectZoneAddFromWand)
                                        .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                        .executes(ConquestCommand::protectZoneAdd)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(PROTECT_ZONE_NAMES)
                                        .executes(ConquestCommand::protectZoneRemove)))
                        .then(Commands.literal("list").executes(ConquestCommand::protectZoneList)))
                .then(Commands.literal("protectblock")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("block", ResourceLocationArgument.id())
                                        .executes(ConquestCommand::protectBlockAdd)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("block", ResourceLocationArgument.id())
                                        .executes(ConquestCommand::protectBlockRemove)))
                        .then(Commands.literal("list").executes(ConquestCommand::protectBlockList)))
                .then(Commands.literal("boundary")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("set")
                                .executes(ConquestCommand::setBoundaryFromWand)
                                .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                        .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                .executes(ConquestCommand::setBoundary))))
                        .then(Commands.literal("corner1")
                                .then(Commands.literal("set").executes(ctx -> setBoundaryCorner(ctx, true))))
                        .then(Commands.literal("corner2")
                                .then(Commands.literal("set").executes(ctx -> setBoundaryCorner(ctx, false))))
                        .then(Commands.literal("remove").executes(ConquestCommand::removeBoundary))
                        .then(Commands.literal("list").executes(ConquestCommand::boundaryList))
                        .then(Commands.literal("restore").executes(ConquestCommand::boundaryRestore)))
                .then(Commands.literal("callin")
                        .then(Commands.literal("add")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("cost", IntegerArgumentType.integer(0))
                                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                                        .executes(ctx -> callInAdd(ctx, 1))
                                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                                .executes(ctx -> callInAdd(ctx, IntegerArgumentType.getInteger(ctx, "count"))))))))
                        .then(Commands.literal("remove")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(CALLIN_NAMES)
                                        .executes(ConquestCommand::callInRemove)))
                        .then(Commands.literal("list").executes(ConquestCommand::callInList))
                        .then(Commands.literal("use")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(CALLIN_NAMES)
                                        .executes(ConquestCommand::callInUse))))
                .then(Commands.literal("mode")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"conquest", "tdm", "breakthrough"}, b))
                                        .executes(ConquestCommand::setMode))))
                .then(Commands.literal("sector")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("add")
                                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(ctx -> sectorAdd(ctx, Config.CAPTURE_RADIUS.get()))
                                                .then(Commands.argument("radius", IntegerArgumentType.integer(2, 64))
                                                        .executes(ctx -> sectorAdd(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))))
                        .then(Commands.literal("spawn")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("role", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"attacker", "defender"}, b))
                                                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                                        .executes(ConquestCommand::sectorSpawnSet)))))
                        .then(Commands.literal("timelimit")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 86400))
                                                        .executes(ConquestCommand::sectorTimeLimitSet)))))
                        .then(Commands.literal("area")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                                .executes(ConquestCommand::sectorAreaSetFromWand)
                                                .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                                        .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                                .executes(ConquestCommand::sectorAreaSet)))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                                .executes(ConquestCommand::sectorAreaRemove))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                        .executes(ConquestCommand::sectorRemove)))
                        .then(Commands.literal("list").executes(ConquestCommand::sectorList)))
                .then(Commands.literal("preset")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("save")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ConquestCommand::presetSave)))
                        .then(Commands.literal("load")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(PRESET_NAMES)
                                        .executes(ConquestCommand::presetLoad)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(PRESET_NAMES)
                                        .executes(ConquestCommand::presetRemove)))
                        .then(Commands.literal("list").executes(ConquestCommand::presetList)))
                .then(Commands.literal("start")
                        .requires(src -> src.hasPermission(2))
                        .executes(ConquestCommand::start))
                .then(Commands.literal("stop")
                        .requires(src -> src.hasPermission(2))
                        .executes(ConquestCommand::stop))
                .then(Commands.literal("reset")
                        .requires(src -> src.hasPermission(2))
                        .executes(ConquestCommand::reset))
                .then(Commands.literal("config")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("list").executes(ConquestCommand::configList))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(CONFIG_KEYS.keySet(), b))
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(ConquestCommand::configSet)))))
                .then(Commands.literal("status")
                        .executes(ConquestCommand::status)));
    }

    private static int joinTeam(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Team team = Team.byKey(StringArgumentType.getString(ctx, "team"));
        if (team == null) {
            return fail(ctx, Component.translatable("conquest.msg.unknown_team"));
        }
        if (team == Team.ADMIN && !ctx.getSource().hasPermission(2)) {
            return fail(ctx, Component.translatable("conquest.msg.admin_team_requires_op"));
        }
        ConquestManager.get(ctx.getSource().getServer()).joinTeam(player, team);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.team_joined", team.display()), false);
        return 1;
    }

    /** OP-only: assigns another player to a team, e.g. to fix someone AFK at the team-select screen. */
    private static int joinTeamOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Team team = Team.byKey(StringArgumentType.getString(ctx, "team"));
        if (team == null) {
            return fail(ctx, Component.translatable("conquest.msg.unknown_team"));
        }
        ConquestManager.get(ctx.getSource().getServer()).joinTeam(target, team);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.team_joined_other", target.getName(), team.display()), true);
        target.displayClientMessage(Component.translatable("conquest.msg.team_joined", team.display()), true);
        return 1;
    }

    /**
     * Marks an enemy's position for the spotter's team (see {@link ConquestManager#spotPlayer}).
     * Triggered by the client's spot key after its own line-of-sight/team raycast, so failures
     * here are silent (0, no chat message) rather than user-facing errors — the client has
     * already filtered out anything a legitimate press wouldn't send.
     */
    private static int spot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer spotter = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        ConquestManager manager = ConquestManager.get(ctx.getSource().getServer());
        if (manager.getState() != RoundState.IN_PROGRESS) {
            return 0;
        }
        Team spotterTeam = manager.teamOf(spotter.getUUID());
        Team targetTeam = manager.teamOf(target.getUUID());
        if (!spotterTeam.isCombatant() || !targetTeam.isCombatant() || spotterTeam == targetTeam || !target.isAlive()) {
            return 0;
        }
        return manager.spotPlayer(ctx.getSource().getServer(), spotter, target) ? 1 : 0;
    }

    private static int setMode(CommandContext<CommandSourceStack> ctx) {
        GameMode mode = GameMode.byKey(StringArgumentType.getString(ctx, "mode"));
        if (mode == null) {
            return fail(ctx, Component.translatable("conquest.msg.unknown_mode"));
        }
        ConquestManager manager = ConquestManager.get(ctx.getSource().getServer());
        if (!manager.setMode(mode)) {
            return fail(ctx, Component.translatable("conquest.msg.mode_locked"));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.mode_set", mode.display()), true);
        return 1;
    }

    private static int presetSave(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        ConquestManager manager = ConquestManager.get(ctx.getSource().getServer());
        manager.savePreset(name);
        MapPreset preset = manager.getPreset(name);
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.preset_saved",
                name, preset.getPoints().size(), preset.getMode().display()), true);
        return 1;
    }

    private static int presetLoad(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        MinecraftServer server = ctx.getSource().getServer();
        ConquestManager.LoadPresetResult result = ConquestManager.get(server).loadPreset(server, name);
        return switch (result) {
            case OK -> {
                ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.preset_loaded", name), true);
                yield 1;
            }
            case NOT_FOUND -> fail(ctx, Component.translatable("conquest.msg.preset_not_found", name));
            case ROUND_ACTIVE -> fail(ctx, Component.translatable("conquest.msg.preset_locked"));
        };
    }

    private static int presetRemove(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!ConquestManager.get(ctx.getSource().getServer()).removePreset(name)) {
            return fail(ctx, Component.translatable("conquest.msg.preset_not_found", name));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.preset_removed", name), true);
        return 1;
    }

    private static int presetList(CommandContext<CommandSourceStack> ctx) {
        ConquestManager manager = ConquestManager.get(ctx.getSource().getServer());
        if (manager.getPresetNames().isEmpty()) {
            return fail(ctx, Component.translatable("conquest.msg.no_preset"));
        }
        MutableComponent msg = Component.translatable("conquest.msg.preset_list_header").withStyle(ChatFormatting.GOLD);
        for (String name : manager.getPresetNames()) {
            MapPreset preset = manager.getPreset(name);
            msg.append("\n").append(Component.translatable("conquest.status.preset",
                    name, preset.getPoints().size(), preset.getMode().display()));
        }
        MutableComponent result = msg;
        ctx.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int teamAssign(CommandContext<CommandSourceStack> ctx) {
        String role = StringArgumentType.getString(ctx, "role");
        if (!role.equalsIgnoreCase("attacker") && !role.equalsIgnoreCase("defender")) {
            return fail(ctx, Component.translatable("conquest.msg.unknown_role"));
        }
        Team team = Team.byKey(StringArgumentType.getString(ctx, "team"));
        if (team == null) {
            return fail(ctx, Component.translatable("conquest.msg.unknown_team"));
        }
        Team attackerTeam = role.equalsIgnoreCase("attacker") ? team : team.opponent();
        ConquestManager manager = ConquestManager.get(ctx.getSource().getServer());
        if (!manager.setAttackerTeam(attackerTeam)) {
            return fail(ctx, Component.translatable("conquest.msg.mode_locked"));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.team_assigned",
                attackerTeam.display(), attackerTeam.opponent().display()), true);
        return 1;
    }

    private static int sectorAdd(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        int number = IntegerArgumentType.getInteger(ctx, "number");
        String name = StringArgumentType.getString(ctx, "name");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ConquestManager.get(ctx.getSource().getServer())
                .addSectorPoint(level, number, name, player.blockPosition(), radius);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.sector_point_added", number, name, radius), true);
        return 1;
    }

    private static int sectorSpawnSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String role = StringArgumentType.getString(ctx, "role");
        if (!role.equalsIgnoreCase("attacker") && !role.equalsIgnoreCase("defender")) {
            return fail(ctx, Component.translatable("conquest.msg.unknown_role"));
        }
        int number = IntegerArgumentType.getInteger(ctx, "number");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean attackerRole = role.equalsIgnoreCase("attacker");
        boolean ok = ConquestManager.get(ctx.getSource().getServer())
                .setSectorSpawn(player.serverLevel(), number, attackerRole, player.blockPosition());
        if (!ok) {
            return fail(ctx, Component.translatable("conquest.msg.sector_not_found", number));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.sector_spawn_set", role, number), true);
        return 1;
    }

    private static int sectorTimeLimitSet(CommandContext<CommandSourceStack> ctx) {
        int number = IntegerArgumentType.getInteger(ctx, "number");
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        if (!ConquestManager.get(ctx.getSource().getServer()).setSectorTimeLimit(number, seconds)) {
            return fail(ctx, Component.translatable("conquest.msg.sector_not_found", number));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.sector_timelimit_set", number, seconds), true);
        return 1;
    }

    private static int sectorAreaSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int number = IntegerArgumentType.getInteger(ctx, "number");
        BlockPos pos1 = BlockPosArgument.getBlockPos(ctx, "pos1");
        BlockPos pos2 = BlockPosArgument.getBlockPos(ctx, "pos2");
        if (!ConquestManager.get(ctx.getSource().getServer()).setSectorArea(ctx.getSource().getLevel(), number, pos1, pos2)) {
            return fail(ctx, Component.translatable("conquest.msg.sector_not_found", number));
        }
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.sector_area_set", number, pos1.toShortString(), pos2.toShortString()), true);
        return 1;
    }

    /** {@code /conquest sector area set <number>} with no coordinates: uses the sender's zone wand selection instead. */
    private static int sectorAreaSetFromWand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int number = IntegerArgumentType.getInteger(ctx, "number");
        BlockPos[] selection = ZoneSelection.get(player);
        if (selection == null) {
            return fail(ctx, Component.translatable("conquest.msg.wand_no_selection"));
        }
        if (!ConquestManager.get(ctx.getSource().getServer()).setSectorArea(player.serverLevel(), number, selection[0], selection[1])) {
            return fail(ctx, Component.translatable("conquest.msg.sector_not_found", number));
        }
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.sector_area_set", number, selection[0].toShortString(), selection[1].toShortString()), true);
        return 1;
    }

    private static int sectorAreaRemove(CommandContext<CommandSourceStack> ctx) {
        int number = IntegerArgumentType.getInteger(ctx, "number");
        if (!ConquestManager.get(ctx.getSource().getServer()).removeSectorArea(number)) {
            return fail(ctx, Component.translatable("conquest.msg.sector_area_not_found", number));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.sector_area_removed", number), true);
        return 1;
    }

    private static int sectorRemove(CommandContext<CommandSourceStack> ctx) {
        int number = IntegerArgumentType.getInteger(ctx, "number");
        if (!ConquestManager.get(ctx.getSource().getServer()).removeSector(ctx.getSource().getServer(), number)) {
            return fail(ctx, Component.translatable("conquest.msg.sector_not_found", number));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.sector_removed", number), true);
        return 1;
    }

    private static int sectorList(CommandContext<CommandSourceStack> ctx) {
        ConquestManager manager = ConquestManager.get(ctx.getSource().getServer());
        if (!manager.hasSectors()) {
            return fail(ctx, Component.translatable("conquest.msg.no_sector"));
        }
        MutableComponent msg = Component.translatable("conquest.msg.sector_list_header").withStyle(ChatFormatting.GOLD);
        for (Sector sector : manager.getSectors()) {
            String timeLimit = sector.getTimeLimitSecondsOverride() > 0
                    ? sector.getTimeLimitSecondsOverride() + "s" : "default";
            msg.append("\n").append(Component.translatable("conquest.status.sector",
                    sector.getNumber(), String.join(", ", sector.getPointNames()), timeLimit));
        }
        MutableComponent result = msg;
        ctx.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int shuffleTeams(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        ConquestManager manager = ConquestManager.get(server);
        int count = manager.shuffleTeams(server);
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.shuffled", count), true);
        return count;
    }

    private static int setPoint(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ConquestManager.get(ctx.getSource().getServer())
                .setPoint(level, "Alpha", player.blockPosition(), radius);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.point_set", "Alpha", radius), true);
        return 1;
    }

    private static int addPoint(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ConquestManager.get(ctx.getSource().getServer())
                .setPoint(level, name, player.blockPosition(), radius);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.point_set", name, radius), true);
        return 1;
    }

    private static int removePoint(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!ConquestManager.get(ctx.getSource().getServer()).removePoint(ctx.getSource().getServer(), name)) {
            return fail(ctx, Component.translatable("conquest.msg.point_not_found", name));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.point_removed", name), true);
        return 1;
    }

    private static int pointList(CommandContext<CommandSourceStack> ctx) {
        ConquestManager manager = ConquestManager.get(ctx.getSource().getServer());
        if (!manager.hasPoints()) {
            return fail(ctx, Component.translatable("conquest.msg.no_point"));
        }
        MutableComponent msg = Component.translatable("conquest.msg.point_list_header").withStyle(ChatFormatting.GOLD);
        for (CapturePoint point : manager.getPoints()) {
            msg.append("\n").append(Component.translatable("conquest.status.point",
                    point.getName(), point.getPos().toShortString(), point.getRadius(),
                    point.getOwner().display(), (int) point.getFlagLevel()));
        }
        MutableComponent result = msg;
        ctx.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int setSpawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Team team = Team.byKey(StringArgumentType.getString(ctx, "team"));
        if (team == null) {
            return fail(ctx, Component.translatable("conquest.msg.unknown_team"));
        }
        ConquestManager.get(ctx.getSource().getServer())
                .setSpawn(player.serverLevel(), team, player.blockPosition());
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.spawn_set", team.display()), true);
        return 1;
    }

    private static int setGatherPoint(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ConquestManager.get(ctx.getSource().getServer())
                .setGatherPoint(player.serverLevel(), player.blockPosition());
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.gather_set"), true);
        return 1;
    }

    private static int removeGatherPoint(CommandContext<CommandSourceStack> ctx) {
        if (!ConquestManager.get(ctx.getSource().getServer()).removeGatherPoint()) {
            return fail(ctx, Component.translatable("conquest.msg.no_gather_point"));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.gather_removed"), true);
        return 1;
    }

    private static int gatherList(CommandContext<CommandSourceStack> ctx) {
        ConquestManager manager = ConquestManager.get(ctx.getSource().getServer());
        BlockPos pos = manager.getGatherPos();
        if (pos == null) {
            return fail(ctx, Component.translatable("conquest.msg.no_gather_point"));
        }
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.status.gather", pos.toShortString()), false);
        return 1;
    }

    private static int setZone(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Team team = Team.byKey(StringArgumentType.getString(ctx, "team"));
        if (team == null) {
            return fail(ctx, Component.translatable("conquest.msg.unknown_team"));
        }
        BlockPos pos1 = BlockPosArgument.getBlockPos(ctx, "pos1");
        BlockPos pos2 = BlockPosArgument.getBlockPos(ctx, "pos2");
        ConquestManager.get(ctx.getSource().getServer())
                .setZone(ctx.getSource().getLevel(), team, pos1, pos2);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.zone_set", team.display(),
                        pos1.toShortString(), pos2.toShortString()), true);
        return 1;
    }

    /** {@code /conquest zone set <a|b>} with no coordinates: uses the sender's zone wand selection instead. */
    private static int setZoneFromWand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Team team = Team.byKey(StringArgumentType.getString(ctx, "team"));
        if (team == null) {
            return fail(ctx, Component.translatable("conquest.msg.unknown_team"));
        }
        BlockPos[] selection = ZoneSelection.get(player);
        if (selection == null) {
            return fail(ctx, Component.translatable("conquest.msg.wand_no_selection"));
        }
        ConquestManager.get(ctx.getSource().getServer())
                .setZone(player.serverLevel(), team, selection[0], selection[1]);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.zone_set", team.display(),
                        selection[0].toShortString(), selection[1].toShortString()), true);
        return 1;
    }

    private static int setZoneCorner(CommandContext<CommandSourceStack> ctx, boolean corner1) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Team team = Team.byKey(StringArgumentType.getString(ctx, "team"));
        if (team == null) {
            return fail(ctx, Component.translatable("conquest.msg.unknown_team"));
        }
        ConquestManager.get(ctx.getSource().getServer())
                .setZoneCorner(player.serverLevel(), team, corner1, player.blockPosition());
        ctx.getSource().sendSuccess(() -> Component.translatable(
                corner1 ? "conquest.msg.zone_corner1_set" : "conquest.msg.zone_corner2_set", team.display()), true);
        return 1;
    }

    private static int removeZone(CommandContext<CommandSourceStack> ctx) {
        Team team = Team.byKey(StringArgumentType.getString(ctx, "team"));
        if (team == null) {
            return fail(ctx, Component.translatable("conquest.msg.unknown_team"));
        }
        if (!ConquestManager.get(ctx.getSource().getServer()).removeZone(team)) {
            return fail(ctx, Component.translatable("conquest.msg.no_zone", team.display()));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.zone_removed", team.display()), true);
        return 1;
    }

    private static int zoneList(CommandContext<CommandSourceStack> ctx) {
        ConquestManager manager = ConquestManager.get(ctx.getSource().getServer());
        MutableComponent msg = Component.translatable("conquest.msg.zone_list_header").withStyle(ChatFormatting.GOLD);
        boolean any = false;
        for (Team team : new Team[]{Team.A, Team.B}) {
            BlockPos min = manager.getZoneMin(team);
            BlockPos max = manager.getZoneMax(team);
            if (min != null && max != null) {
                any = true;
                msg.append("\n").append(Component.translatable("conquest.status.zone",
                        team.display(), min.toShortString(), max.toShortString()));
            }
        }
        if (!any) {
            return fail(ctx, Component.translatable("conquest.msg.no_zone_any"));
        }
        MutableComponent result = msg;
        ctx.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int setBoundary(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        BlockPos pos1 = BlockPosArgument.getBlockPos(ctx, "pos1");
        BlockPos pos2 = BlockPosArgument.getBlockPos(ctx, "pos2");
        ConquestManager.get(ctx.getSource().getServer())
                .setBoundary(ctx.getSource().getLevel(), pos1, pos2);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.boundary_set",
                        pos1.toShortString(), pos2.toShortString()), true);
        return 1;
    }

    /** {@code /conquest boundary set} with no coordinates: uses the sender's zone wand selection instead. */
    private static int setBoundaryFromWand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        BlockPos[] selection = ZoneSelection.get(player);
        if (selection == null) {
            return fail(ctx, Component.translatable("conquest.msg.wand_no_selection"));
        }
        ConquestManager.get(ctx.getSource().getServer())
                .setBoundary(player.serverLevel(), selection[0], selection[1]);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.boundary_set",
                        selection[0].toShortString(), selection[1].toShortString()), true);
        return 1;
    }

    private static int setBoundaryCorner(CommandContext<CommandSourceStack> ctx, boolean corner1) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ConquestManager.get(ctx.getSource().getServer())
                .setBoundaryCorner(player.serverLevel(), corner1, player.blockPosition());
        ctx.getSource().sendSuccess(() -> Component.translatable(
                corner1 ? "conquest.msg.boundary_corner1_set" : "conquest.msg.boundary_corner2_set"), true);
        return 1;
    }

    private static int removeBoundary(CommandContext<CommandSourceStack> ctx) {
        if (!ConquestManager.get(ctx.getSource().getServer()).removeBoundary()) {
            return fail(ctx, Component.translatable("conquest.msg.no_boundary"));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.boundary_removed"), true);
        return 1;
    }

    /** Manually restores the current terrain snapshot, e.g. after a mid-round {@code /conquest stop}. */
    private static int boundaryRestore(CommandContext<CommandSourceStack> ctx) {
        if (!ConquestManager.get(ctx.getSource().getServer()).restoreTerrain(ctx.getSource().getServer())) {
            return fail(ctx, Component.translatable("conquest.msg.no_terrain_snapshot"));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.terrain_restored"), true);
        return 1;
    }

    private static int boundaryList(CommandContext<CommandSourceStack> ctx) {
        ConquestManager manager = ConquestManager.get(ctx.getSource().getServer());
        BlockPos min = manager.getBoundaryMin();
        BlockPos max = manager.getBoundaryMax();
        if (min == null || max == null) {
            return fail(ctx, Component.translatable("conquest.msg.no_boundary"));
        }
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.status.boundary", min.toShortString(), max.toShortString()), false);
        return 1;
    }

    private static int callInAdd(CommandContext<CommandSourceStack> ctx, int count) {
        String name = StringArgumentType.getString(ctx, "name");
        int cost = IntegerArgumentType.getInteger(ctx, "cost");
        ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item");
        if (!ForgeRegistries.ITEMS.containsKey(itemId)) {
            return fail(ctx, Component.translatable("conquest.msg.unknown_item", itemId.toString()));
        }
        ConquestManager.get(ctx.getSource().getServer()).addCallIn(name, cost, itemId, count);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.callin_added", name, cost, count, itemId.toString()), true);
        return 1;
    }

    private static int callInRemove(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!ConquestManager.get(ctx.getSource().getServer()).removeCallIn(name)) {
            return fail(ctx, Component.translatable("conquest.msg.callin_not_found", name));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.callin_removed", name), true);
        return 1;
    }

    private static int callInList(CommandContext<CommandSourceStack> ctx) {
        ConquestManager manager = ConquestManager.get(ctx.getSource().getServer());
        if (manager.getCallIns().isEmpty()) {
            return fail(ctx, Component.translatable("conquest.msg.no_callin"));
        }
        MutableComponent msg = Component.translatable("conquest.msg.callin_list_header").withStyle(ChatFormatting.GOLD);
        for (CallIn callIn : manager.getCallIns()) {
            msg.append("\n").append(Component.translatable("conquest.status.callin",
                    callIn.getName(), callIn.getScoreCost(), callIn.getCount(), callIn.getItemId().toString()));
        }
        MutableComponent result = msg;
        ctx.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int callInUse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        ConquestManager manager = ConquestManager.get(ctx.getSource().getServer());
        ConquestManager.UseCallInResult result = manager.useCallIn(player, name);
        return switch (result) {
            case OK -> {
                CallIn callIn = manager.getCallIn(name);
                int cost = callIn == null ? 0 : callIn.getScoreCost();
                ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.callin_used", name, cost), true);
                yield 1;
            }
            case NOT_FOUND -> fail(ctx, Component.translatable("conquest.msg.callin_not_found", name));
            case NOT_ACTIVE -> fail(ctx, Component.translatable("conquest.msg.not_active"));
            case INSUFFICIENT_SCORE -> fail(ctx, Component.translatable("conquest.msg.callin_insufficient_score", name));
            case UNKNOWN_ITEM -> fail(ctx, Component.translatable("conquest.msg.callin_unknown_item", name));
        };
    }

    private static int protectZoneAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        BlockPos pos1 = BlockPosArgument.getBlockPos(ctx, "pos1");
        BlockPos pos2 = BlockPosArgument.getBlockPos(ctx, "pos2");
        ConquestManager.get(ctx.getSource().getServer())
                .addProtectZone(name, ctx.getSource().getLevel(), pos1, pos2);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.protectzone_added", name,
                        pos1.toShortString(), pos2.toShortString()), true);
        return 1;
    }

    /** {@code /conquest protectzone add <name>} with no coordinates: uses the sender's zone wand selection instead. */
    private static int protectZoneAddFromWand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        BlockPos[] selection = ZoneSelection.get(player);
        if (selection == null) {
            return fail(ctx, Component.translatable("conquest.msg.wand_no_selection"));
        }
        ConquestManager.get(ctx.getSource().getServer())
                .addProtectZone(name, player.serverLevel(), selection[0], selection[1]);
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.protectzone_added", name,
                        selection[0].toShortString(), selection[1].toShortString()), true);
        return 1;
    }

    private static int protectZoneRemove(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!ConquestManager.get(ctx.getSource().getServer()).removeProtectZone(name)) {
            return fail(ctx, Component.translatable("conquest.msg.protectzone_not_found", name));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.protectzone_removed", name), true);
        return 1;
    }

    private static int protectZoneList(CommandContext<CommandSourceStack> ctx) {
        ConquestManager manager = ConquestManager.get(ctx.getSource().getServer());
        if (manager.getProtectZones().isEmpty()) {
            return fail(ctx, Component.translatable("conquest.msg.no_protectzone"));
        }
        MutableComponent msg = Component.translatable("conquest.msg.protectzone_list_header").withStyle(ChatFormatting.GOLD);
        for (ProtectZone zone : manager.getProtectZones()) {
            msg.append("\n").append(Component.translatable("conquest.status.protectzone",
                    zone.getName(), zone.getMin().toShortString(), zone.getMax().toShortString()));
        }
        MutableComponent result = msg;
        ctx.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int protectBlockAdd(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation blockId = ResourceLocationArgument.getId(ctx, "block");
        if (!ForgeRegistries.BLOCKS.containsKey(blockId)) {
            return fail(ctx, Component.translatable("conquest.msg.unknown_block", blockId.toString()));
        }
        if (!ConquestManager.get(ctx.getSource().getServer()).addProtectedBlock(blockId.toString())) {
            return fail(ctx, Component.translatable("conquest.msg.protectblock_already", blockId.toString()));
        }
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.protectblock_added", blockId.toString()), true);
        return 1;
    }

    private static int protectBlockRemove(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation blockId = ResourceLocationArgument.getId(ctx, "block");
        if (!ConquestManager.get(ctx.getSource().getServer()).removeProtectedBlock(blockId.toString())) {
            return fail(ctx, Component.translatable("conquest.msg.protectblock_not_found", blockId.toString()));
        }
        ctx.getSource().sendSuccess(() ->
                Component.translatable("conquest.msg.protectblock_removed", blockId.toString()), true);
        return 1;
    }

    private static int protectBlockList(CommandContext<CommandSourceStack> ctx) {
        ConquestManager manager = ConquestManager.get(ctx.getSource().getServer());
        MutableComponent msg = Component.translatable("conquest.msg.protectblock_list_header").withStyle(ChatFormatting.GOLD);
        for (String id : Config.INDESTRUCTIBLE_BLOCKS.get()) {
            msg.append("\n").append(Component.translatable("conquest.status.protectblock_config", id));
        }
        for (String id : manager.getProtectedBlocks()) {
            msg.append("\n").append(Component.translatable("conquest.status.protectblock_custom", id));
        }
        MutableComponent result = msg;
        ctx.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        ConquestManager manager = ConquestManager.get(server);
        ConquestManager.StartResult result = manager.start(server);
        return switch (result) {
            case OK -> 1;
            case ALREADY_RUNNING -> fail(ctx, Component.translatable("conquest.msg.already_active"));
            case RESULT_PENDING -> fail(ctx, Component.translatable("conquest.msg.result_pending"));
            case NO_POINT -> fail(ctx, Component.translatable("conquest.msg.no_point"));
            case NO_SECTOR -> fail(ctx, Component.translatable("conquest.msg.no_sector"));
            case TEAM_A_EMPTY -> fail(ctx, Component.translatable("conquest.msg.team_empty", Team.A.display()));
            case TEAM_B_EMPTY -> fail(ctx, Component.translatable("conquest.msg.team_empty", Team.B.display()));
        };
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        if (!ConquestManager.get(server).stop(server)) {
            return fail(ctx, Component.translatable("conquest.msg.not_active"));
        }
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) {
        ConquestManager manager = ConquestManager.get(ctx.getSource().getServer());
        if (!manager.reset()) {
            return fail(ctx, Component.translatable("conquest.msg.nothing_to_reset"));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.msg.reset_done"), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        ConquestManager manager = ConquestManager.get(server);

        RoundState state = manager.getState();
        String stateKey = switch (state) {
            case WAITING -> "conquest.status.waiting";
            case STARTING -> "conquest.status.starting";
            case IN_PROGRESS -> "conquest.status.running";
            case ENDED -> "conquest.status.ended";
        };
        MutableComponent msg = Component.translatable("conquest.status.header").withStyle(ChatFormatting.GOLD);
        msg.append("\n").append(Component.translatable("conquest.status.mode", manager.getMode().display())
                .withStyle(ChatFormatting.GRAY));
        msg.append("\n").append(Component.translatable("conquest.status.tickets",
                Component.literal(String.valueOf(manager.tickets(Team.A))).withStyle(ChatFormatting.BLUE),
                Component.literal(String.valueOf(manager.tickets(Team.B))).withStyle(ChatFormatting.RED),
                Component.translatable(stateKey)));

        if (state == RoundState.STARTING) {
            msg.append("\n").append(Component.translatable("conquest.status.countdown",
                    manager.remainingStartCountdown()).withStyle(ChatFormatting.GRAY));
        } else if (state == RoundState.IN_PROGRESS) {
            int remaining = manager.remainingRoundSeconds();
            if (remaining >= 0) {
                msg.append("\n").append(Component.translatable("conquest.status.time_left", remaining)
                        .withStyle(ChatFormatting.GRAY));
            }
        } else if (state == RoundState.ENDED) {
            Team winner = manager.getLastWinner();
            msg.append("\n").append(Component.translatable("conquest.status.last_result",
                    winner == null ? Component.translatable("conquest.title.draw") : winner.display()));
            int remaining = manager.remainingResultSeconds();
            if (remaining >= 0) {
                msg.append("\n").append(Component.translatable("conquest.status.reset_in", remaining)
                        .withStyle(ChatFormatting.GRAY));
            }
        }

        if (!manager.hasPoints()) {
            if (manager.getMode() == GameMode.CONQUEST) {
                msg.append("\n").append(Component.translatable("conquest.msg.no_point").withStyle(ChatFormatting.GRAY));
            }
        } else {
            for (CapturePoint point : manager.getPoints()) {
                msg.append("\n").append(Component.translatable("conquest.status.point",
                        point.getName(),
                        point.getPos().toShortString(),
                        point.getRadius(),
                        point.getOwner().display(),
                        (int) point.getFlagLevel()));
            }
        }

        Team myTeam = manager.teamOf(player.getUUID());
        msg.append("\n").append(Component.translatable("conquest.status.your_team",
                myTeam == Team.NEUTRAL
                        ? Component.translatable("conquest.status.unassigned").withStyle(ChatFormatting.GRAY)
                        : myTeam.display()));

        // Squad info read through squadtp's public API (read-only; the deeper
        // squad/team integration is a later stage).
        Squad squad = SquadManager.get(server).getSquadOf(player.getUUID());
        if (squad != null) {
            MutableComponent members = Component.empty();
            boolean first = true;
            for (Map.Entry<UUID, String> e : squad.getMembers().entrySet()) {
                if (!first) {
                    members.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
                }
                first = false;
                Team memberTeam = manager.teamOf(e.getKey());
                members.append(Component.literal(e.getValue())
                        .withStyle(memberTeam == Team.NEUTRAL ? ChatFormatting.WHITE : memberTeam.color()));
                if (squad.isLeader(e.getKey())) {
                    members.append(Component.literal("★").withStyle(ChatFormatting.GOLD));
                }
            }
            msg.append("\n").append(Component.translatable("conquest.status.squad", members));
        }

        if (!manager.getCallIns().isEmpty()) {
            msg.append("\n").append(Component.translatable("conquest.status.available_score",
                    manager.availableScore(player.getUUID())).withStyle(ChatFormatting.GRAY));
        }

        MutableComponent result = msg;
        ctx.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int configList(CommandContext<CommandSourceStack> ctx) {
        MutableComponent msg = Component.translatable("conquest.config.header").withStyle(ChatFormatting.GOLD);
        for (Map.Entry<String, ConfigEntry> e : CONFIG_KEYS.entrySet()) {
            msg.append("\n").append(Component.literal(e.getKey() + " = " + e.getValue().getter().get())
                    .withStyle(ChatFormatting.GRAY));
        }
        MutableComponent result = msg;
        ctx.getSource().sendSuccess(() -> result, false);
        return 1;
    }

    private static int configSet(CommandContext<CommandSourceStack> ctx) {
        String key = StringArgumentType.getString(ctx, "key");
        String value = StringArgumentType.getString(ctx, "value");
        ConfigEntry entry = CONFIG_KEYS.get(key);
        if (entry == null) {
            return fail(ctx, Component.translatable("conquest.config.unknown_key", key));
        }
        if (!entry.setter().apply(value)) {
            return fail(ctx, Component.translatable("conquest.config.invalid_value", value, key));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("conquest.config.set", key, value), true);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, Component message) {
        ctx.getSource().sendFailure(message);
        return 0;
    }

    private ConquestCommand() {}
}
