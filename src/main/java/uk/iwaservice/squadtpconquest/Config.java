package uk.iwaservice.squadtpconquest;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

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
    public static final ForgeConfigSpec.IntValue HOME_ZONE_KILL_SECONDS;
    public static final ForgeConfigSpec.IntValue BOUNDARY_KILL_SECONDS;
    public static final ForgeConfigSpec.IntValue TEAM_BEACON_LIFETIME_SECONDS;
    public static final ForgeConfigSpec.BooleanValue SPAWN_AT_OWNED_POINTS_ENABLED;
    public static final ForgeConfigSpec.IntValue SPOT_RANGE_BLOCKS;
    public static final ForgeConfigSpec.IntValue SPOT_DURATION_SECONDS;
    public static final ForgeConfigSpec.IntValue SPOT_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue ASSIST_WINDOW_SECONDS;
    public static final ForgeConfigSpec.IntValue SCORE_PER_KILL;
    public static final ForgeConfigSpec.IntValue SCORE_PER_ASSIST;
    public static final ForgeConfigSpec.IntValue SCORE_PER_REVIVE;

    public static final ForgeConfigSpec.IntValue BT_ATTACKER_TICKETS;
    public static final ForgeConfigSpec.IntValue BT_RESPAWN_WAVE_INTERVAL_SECONDS;
    public static final ForgeConfigSpec.IntValue BT_SECTOR_TIME_LIMIT_SECONDS;
    public static final ForgeConfigSpec.IntValue BT_SECTOR_TIME_EXTENSION_ON_CAPTURE;
    public static final ForgeConfigSpec.IntValue BT_SECTOR_AREA_TRANSITION_GRACE_SECONDS;
    public static final ForgeConfigSpec.IntValue BT_TICKETS_PER_SECTOR_CAPTURE;

    public static final ForgeConfigSpec.BooleanValue TERRAIN_DESTRUCTION_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> INDESTRUCTIBLE_BLOCKS;
    public static final ForgeConfigSpec.ConfigValue<String> CRATER_RUBBLE_BLOCK;
    public static final ForgeConfigSpec.DoubleValue CRATER_RUBBLE_RING_RATIO;
    public static final ForgeConfigSpec.IntValue MAX_BLOCKS_PER_EXPLOSION;

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
        HOME_ZONE_KILL_SECONDS = b
                .comment("Continuous seconds an enemy player may spend inside a team's home zone before being",
                        "executed. Resets to 0 the instant they leave the zone. Applies in every game mode.")
                .defineInRange("homeZoneKillSeconds", 10, 1, 600);
        BOUNDARY_KILL_SECONDS = b
                .comment("Continuous seconds any combatant player may spend outside the battlefield boundary",
                        "before being executed (BF-style out-of-bounds). Resets to 0 the instant they return",
                        "inside. Applies in every game mode, only while a round is IN_PROGRESS. No effect if no",
                        "boundary is set (/conquest boundary set).")
                .defineInRange("boundaryKillSeconds", 10, 1, 600);
        TEAM_BEACON_LIFETIME_SECONDS = b
                .comment("Seconds a placed team respawn beacon (squadtpconquest:team_beacon item) stays active.",
                        "While active, any teammate who dies respawns there instead of the usual spawn point,",
                        "with no limit on how many times. Placing a new beacon for a team replaces that team's",
                        "existing one.")
                .defineInRange("teamBeaconLifetimeSeconds", 30, 5, 600);
        SPAWN_AT_OWNED_POINTS_ENABLED = b
                .comment("Conquest only: if true, respawning teleports to whichever capture point the player's",
                        "team owns that's closest to where they died (falls back to the usual global team",
                        "spawn if the team owns none), instead of always the fixed global spawn. A team",
                        "beacon (see teamBeaconLifetimeSeconds) still takes priority over this when active.")
                .define("spawnAtOwnedPointsEnabled", true);
        SPOT_RANGE_BLOCKS = b
                .comment("Max distance (blocks) the spot key can mark an enemy at. Blocked by line of sight",
                        "(walls stop it short of this).")
                .defineInRange("spotRangeBlocks", 100, 10, 300);
        SPOT_DURATION_SECONDS = b
                .comment("Seconds a spotted enemy's position stays visible to the spotter's team (JourneyMap",
                        "waypoint). The mark is a one-time snapshot of where they were, not a live tracker.")
                .defineInRange("spotDurationSeconds", 8, 1, 60);
        SPOT_COOLDOWN_SECONDS = b
                .comment("Seconds a player must wait between uses of the spot key.")
                .defineInRange("spotCooldownSeconds", 2, 0, 30);
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

        b.push("breakthrough");
        BT_ATTACKER_TICKETS = b
                .comment("Total respawn tickets the attacking team starts a Breakthrough round with.",
                        "Each attacker death consumes one; once exhausted, that death is permanent for the round.")
                .defineInRange("attackerTickets", 30, 1, 100000);
        BT_RESPAWN_WAVE_INTERVAL_SECONDS = b
                .comment("Seconds between attacker respawn waves. Dead attackers wait as spectators until the",
                        "next wave, then deploy together at the active sector's attacker spawn.")
                .defineInRange("respawnWaveIntervalSeconds", 15, 1, 600);
        BT_SECTOR_TIME_LIMIT_SECONDS = b
                .comment("Default seconds the attacker has to clear the active sector before the defenders win.",
                        "Overridable per sector with /conquest sector timelimit set.")
                .defineInRange("sectorTimeLimitSeconds", 300, 10, 86400);
        BT_SECTOR_TIME_EXTENSION_ON_CAPTURE = b
                .comment("Seconds added to the active sector's remaining time whenever the attacker captures",
                        "one of its capture points (not just on clearing the whole sector).")
                .defineInRange("sectorTimeExtensionOnCapture", 120, 0, 3600);
        BT_SECTOR_AREA_TRANSITION_GRACE_SECONDS = b
                .comment("Seconds after a sector is cleared before the next sector's combat area (if it has",
                        "one, see /conquest sector area set) starts being enforced as an out-of-bounds",
                        "boundary. Gives players time to walk into the new area without being executed for",
                        "still being outside it. No effect on sectors with no combat area set.")
                .defineInRange("sectorAreaTransitionGraceSeconds", 20, 0, 600);
        BT_TICKETS_PER_SECTOR_CAPTURE = b
                .comment("Tickets added to the attacker's shared pool whenever a sector is fully captured and",
                        "the front line advances (not per individual capture point, see",
                        "sectorTimeExtensionOnCapture for that). 0 disables the bonus. Not awarded on the final",
                        "sector, since capturing it ends the round immediately.")
                .defineInRange("ticketsPerSectorCapture", 10, 0, 100000);
        b.pop();

        b.push("terrainDestruction");
        TERRAIN_DESTRUCTION_ENABLED = b
                .comment("If true, explosions (TNT, creepers, and any other mod's explosions) that happen while",
                        "a round is IN_PROGRESS carve a crater instead of vanilla's block removal, and the",
                        "damage is restored the next time /conquest start runs.")
                .define("terrainDestructionEnabled", true);
        INDESTRUCTIBLE_BLOCKS = b
                .comment("Block registry names that are never destroyed by conquest terrain destruction,",
                        "regardless of blast resistance. Format: \"modid:block_id\".")
                .defineList("indestructibleBlocks",
                        List.of("minecraft:bedrock", "minecraft:chest", "minecraft:trapped_chest",
                                "minecraft:barrel", "minecraft:end_portal_frame", "squadtpconquest:conquest_flag"),
                        o -> o instanceof String);
        CRATER_RUBBLE_BLOCK = b
                .comment("Block placed in the outer ring of a crater (see craterRubbleRingRatio).",
                        "Format: \"modid:block_id\".")
                .define("craterRubbleBlock", "minecraft:coarse_dirt");
        CRATER_RUBBLE_RING_RATIO = b
                .comment("Fraction (0.0-1.0) of an explosion's affected blocks, by distance from the blast center,",
                        "that become craterRubbleBlock instead of air. 0.25 means the outer 25% (by distance) is",
                        "rubble and the inner 75% is air.")
                .defineInRange("craterRubbleRingRatio", 0.25, 0.0, 1.0);
        MAX_BLOCKS_PER_EXPLOSION = b
                .comment("Safety cap: at most this many of an explosion's affected blocks (closest to the blast",
                        "center first) are turned into crater/rubble. Any remainder is left to vanilla's default",
                        "explosion handling instead of being skipped, so very large explosions degrade gracefully",
                        "rather than lagging the server.")
                .defineInRange("maxBlocksPerExplosion", 200, 1, 100000);
        b.pop();

        SPEC = b.build();
    }

    private Config() {}
}
