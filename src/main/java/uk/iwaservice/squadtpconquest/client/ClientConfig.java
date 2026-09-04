package uk.iwaservice.squadtpconquest.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only preferences (never loaded on a dedicated server). */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue HOLD_TO_OPEN_SCOREBOARD;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

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
