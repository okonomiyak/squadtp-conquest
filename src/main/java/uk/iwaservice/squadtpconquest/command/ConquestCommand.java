package uk.iwaservice.squadtpconquest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;
import uk.iwaservice.squadtp.squad.Squad;
import uk.iwaservice.squadtp.squad.SquadManager;
import uk.iwaservice.squadtpconquest.Config;
import uk.iwaservice.squadtpconquest.conquest.CapturePoint;
import uk.iwaservice.squadtpconquest.conquest.ConquestManager;
import uk.iwaservice.squadtpconquest.conquest.GameMode;
import uk.iwaservice.squadtpconquest.conquest.RoundState;
import uk.iwaservice.squadtpconquest.conquest.Team;

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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("conquest")
                .then(Commands.literal("team")
                        .then(Commands.literal("join")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"a", "b", "admin"}, b))
                                        .executes(ConquestCommand::joinTeam)))
                        .then(Commands.literal("shuffle")
                                .requires(src -> src.hasPermission(2))
                                .executes(ConquestCommand::shuffleTeams)))
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
                .then(Commands.literal("mode")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"conquest", "tdm"}, b))
                                        .executes(ConquestCommand::setMode))))
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

    private static int start(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        ConquestManager manager = ConquestManager.get(server);
        ConquestManager.StartResult result = manager.start(server);
        return switch (result) {
            case OK -> 1;
            case ALREADY_RUNNING -> fail(ctx, Component.translatable("conquest.msg.already_active"));
            case RESULT_PENDING -> fail(ctx, Component.translatable("conquest.msg.result_pending"));
            case NO_POINT -> fail(ctx, Component.translatable("conquest.msg.no_point"));
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
