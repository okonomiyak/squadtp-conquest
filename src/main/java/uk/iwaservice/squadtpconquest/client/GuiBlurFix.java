package uk.iwaservice.squadtpconquest.client;

import net.minecraft.client.Minecraft;

/**
 * Vanilla's "Menu Background Blurriness" option blurs this mod's custom screens even though
 * they draw their own opaque panel (a known upstream quirk affecting custom Screens generally -
 * see github.com/kgbcupcake/nourished issue #9 for an unrelated mod hitting the identical
 * symptom). Each screen suppresses it for as long as it's open and restores the player's real
 * setting when it closes, so screens stay crisp without touching the player's actual preference.
 */
public final class GuiBlurFix {
    private GuiBlurFix() {}

    public static int suppress() {
        var opt = Minecraft.getInstance().options.menuBackgroundBlurriness();
        int saved = opt.get();
        opt.set(0);
        return saved;
    }

    public static void restore(int saved) {
        Minecraft.getInstance().options.menuBackgroundBlurriness().set(saved);
    }
}
