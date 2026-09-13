package uk.iwaservice.squadtpconquest.client.gui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import uk.iwaservice.squadtpconquest.client.ConquestClientData;

import java.util.List;

/**
 * Personal kill confirmation, shown just above the health bar: "<victim> をkill" while any of your
 * own kills are still in {@link ConquestClientData}'s shared kill-feed list (same entries and
 * expiry as {@link KillFeedOverlay}'s top-right list — this just also renders the most recent one
 * that's yours, bigger and where you're actually looking).
 */
public class KillConfirmOverlay implements LayeredDraw.Layer {

    public static final KillConfirmOverlay INSTANCE = new KillConfirmOverlay();

    private static final int Y = 50;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
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
        Component line = Component.translatable("conquest.hud.kill_confirm", latest.victimName());
        int y = height - Y;
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f, y, 0);
        graphics.pose().scale(1.3f, 1.3f, 1f);
        graphics.drawCenteredString(font, line, 0, 0, 0xFFFF5555);
        graphics.pose().popPose();
    }
}
