package uk.iwaservice.squadtpconquest.client;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only preferences (never loaded on a dedicated server). */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue HOLD_TO_OPEN_SCOREBOARD;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("gui");
        HOLD_TO_OPEN_SCOREBOARD = b
                .comment("If true, the scoreboard (Right Alt by default) only stays open while the key is held",
                        "down, like vanilla's Tab player list, instead of toggling open until closed.")
                .define("holdToOpenScoreboard", false);
        b.pop();

        SPEC = b.build();
    }

    private ClientConfig() {}
}
