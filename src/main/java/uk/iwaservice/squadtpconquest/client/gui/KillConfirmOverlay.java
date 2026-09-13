package uk.iwaservice.squadtpconquest.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import uk.iwaservice.squadtpconquest.client.ConquestClientData;

import java.util.List;

/**
 * Personal kill confirmation, shown just above the hunger bar: "<victim> をkill" while any of your
 * own kills are still in {@link ConquestClientData}'s shared kill-feed list (same entries and
 * expiry as {@link KillFeedOverlay}'s top-right list — this just also renders the most recent one
 * that's yours, bigger and where you're actually looking). Deliberately over the hunger bar (right
 * of center) rather than the health bar (left of center) - health grows a second icon row above
 * itself past 10 hearts/with absorption, which would collide with a fixed offset; hunger never does.
 */
public class KillConfirmOverlay implements IGuiOverlay {

    public static final KillConfirmOverlay INSTANCE = new KillConfirmOverlay();

    private static final int Y = 50;
    /** Roughly centered over vanilla's hunger bar, which is right-aligned to width/2 + 91. */
    private static final int X_OFFSET = 51;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        String you = mc.player.getGameProfile().getName();
        List<ConquestClientData.KillFeedEntry> entries = ConquestClientData.getKillFeed();
        ConquestClientData.KillFeedEntry latest = null;
        for (ConquestClientData.KillFeedEntry entry : entries) {
            if (entry.attackerName().equals(you)) {
                latest = entry;
            }
        }
        if (latest == null) {
            return;
        }

        Font font = mc.font;
        Component line = Component.translatable("conquest.hud.kill_confirm", latest.victimName(), latest.distanceMeters());
        int y = height - Y;
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f + X_OFFSET, y, 0);
        graphics.pose().scale(1.3f, 1.3f, 1f);
        graphics.drawCenteredString(font, line, 0, 0, 0xFFFF5555);
        graphics.pose().popPose();
    }
}
