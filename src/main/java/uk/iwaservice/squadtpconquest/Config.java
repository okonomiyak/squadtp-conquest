package uk.iwaservice.squadtpconquest;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue CAPTURE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue CAPTURE_RATE_PER_SECOND;
    public static final ForgeConfigSpec.IntValue TICKET_BLEED_INTERVAL;
    public static final ForgeConfigSpec.IntValue TICKET_BLEED_AMOUNT;
    public static final ForgeConfigSpec.IntValue STARTING_TICKETS;
    public static final ForgeConfigSpec.IntValue ROUND_TIME_LIMIT_SECONDS;
    public static final ForgeConfigSpec.IntValue RESULT_DISPLAY_SECONDS;
    public static final ForgeConfigSpec.BooleanValue END_ON_TEAM_EMPTY;
    public static final ForgeConfigSpec.BooleanValue AUTO_RESET_AFTER_RESULT;
    public static final ForgeConfigSpec.IntValue TICKET_COST_PER_RESPAWN;
    public static final ForgeConfigSpec.IntValue START_COUNTDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue TDM_KILL_LIMIT;
    public static final ForgeConfigSpec.IntValue ASSIST_WINDOW_SECONDS;
    public static final ForgeConfigSpec.IntValue SCORE_PER_KILL;
    public static final ForgeConfigSpec.IntValue SCORE_PER_ASSIST;
    public static final ForgeConfigSpec.IntValue SCORE_PER_REVIVE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("conquest");
        CAPTURE_RADIUS = b
                .comment("Default capture radius in blocks around a capture point.")
                .defineInRange("captureRadius", 10, 2, 64);
        CAPTURE_RATE_PER_SECOND = b
                .comment("Flag progress change in percent per second while one team holds the zone.")
                .defineInRange("captureRatePerSecond", 5.0, 0.1, 100.0);
        TICKET_BLEED_INTERVAL = b
                .comment("Seconds between ticket bleed ticks while a team owns the capture point.")
                .defineInRange("ticketBleedInterval", 5, 1, 600);
        TICKET_BLEED_AMOUNT = b
                .comment("Tickets removed from the enemy team per bleed tick.")
                .defineInRange("ticketBleedAmount", 1, 1, 100);
        STARTING_TICKETS = b
                .comment("Tickets each team starts with. First team to reach 0 loses.")
                .defineInRange("startingTickets", 100, 1, 100000);
        ROUND_TIME_LIMIT_SECONDS = b
                .comment("Round time limit in seconds. 0 disables the limit (unlimited round length).",
                        "On reaching the limit, the team with more tickets wins; equal tickets is a draw.")
                .defineInRange("roundTimeLimitSeconds", 0, 0, 86400);
        RESULT_DISPLAY_SECONDS = b
                .comment("Seconds the result title is shown before an automatic reset to WAITING.")
                .defineInRange("resultDisplaySeconds", 10, 1, 300);
        END_ON_TEAM_EMPTY = b
                .comment("If true, a round ends immediately in favor of the other team when one team has 0 online players.")
                .define("endOnTeamEmpty", false);
        AUTO_RESET_AFTER_RESULT = b
                .comment("If true, the round automatically resets to WAITING after resultDisplaySeconds.",
                        "If false, an OP must run /conquest reset.")
                .define("autoResetAfterResult", true);
        TICKET_COST_PER_RESPAWN = b
                .comment("Tickets removed from a player's own team each time they respawn. 0 disables this.")
                .defineInRange("ticketCostPerRespawn", 1, 0, 100);
        START_COUNTDOWN_SECONDS = b
                .comment("Seconds of countdown shown as a title after /conquest start before the round actually",
                        "begins (points/tickets are already reset and teams already teleported during it).",
                        "0 skips the countdown and starts immediately.")
                .defineInRange("startCountdownSeconds", 5, 0, 60);
        TDM_KILL_LIMIT = b
                .comment("Kills a team needs to win a Team Deathmatch round. 0 disables the limit,",
                        "so the round is decided by roundTimeLimitSeconds (or endOnTeamEmpty) instead.")
                .defineInRange("tdmKillLimit", 50, 0, 100000);
        b.pop();

        b.push("scoreboard");
        ASSIST_WINDOW_SECONDS = b
                .comment("Seconds before a death during which damaging the victim still counts as an assist.")
                .defineInRange("assistWindowSeconds", 10, 1, 120);
        SCORE_PER_KILL = b
                .comment("Score points awarded per kill.")
                .defineInRange("scorePerKill", 100, 0, 10000);
        SCORE_PER_ASSIST = b
                .comment("Score points awarded per assist.")
                .defineInRange("scorePerAssist", 50, 0, 10000);
        SCORE_PER_REVIVE = b
                .comment("Score points awarded per successful revive.")
                .defineInRange("scorePerRevive", 50, 0, 10000);
        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
