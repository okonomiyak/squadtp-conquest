package uk.iwaservice.squadtpconquest.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import uk.iwaservice.squadtpconquest.client.ConquestClientData;
import uk.iwaservice.squadtpconquest.conquest.Team;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;

import javax.annotation.Nullable;

/**
 * Large "you are capturing this point" indicator: big label + wide progress
 * bar, shown only while the viewer is physically standing inside a capture
 * point's radius (server-computed, synced per-point via
 * ConquestSyncPacket.PointStatus.inZone). If standing inside more than one
 * overlapping point, the first one found is shown. This is separate from the
 * always-on ticket/point-icon HUD (ConquestHudOverlay), which stays visible
 * the whole round regardless of the player's position.
 */
public class ConquestCaptureOverlay implements IGuiOverlay {

    public static final ConquestCaptureOverlay INSTANCE = new ConquestCaptureOverlay();

    private static final int BAR_WIDTH = 200;
    private static final int BAR_HEIGHT = 8;
    private static final int SELF_COLOR = 0xFF3B6FE0;
    private static final int ENEMY_COLOR = 0xFFE03B3B;
    private static final int CONTESTED_COLOR = 0xFFFFDD33;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        Team yourTeam = ConquestClientData.getYourTeam();
        if (!ConquestClientData.isActive() || !yourTeam.isCombatant()) {
            return;
        }

        ConquestSyncPacket.PointStatus point = findPointInZone();
        if (point == null) {
            return;
        }

        boolean contested = point.contested();
        Team activeTeam = Team.resolveActive(point.owner(), point.capturingTeam(), point.flagLevel());
        int flagLevel = (int) point.flagLevel();

        String labelKey;
        int color;
        if (contested) {
            labelKey = "conquest.capture.contested";
            color = CONTESTED_COLOR;
        } else if (activeTeam == yourTeam && point.owner() == yourTeam && flagLevel >= 100) {
            labelKey = "conquest.capture.secured";
            color = SELF_COLOR;
        } else if (activeTeam == yourTeam) {
            labelKey = "conquest.capture.capturing";
            color = SELF_COLOR;
        } else {
            labelKey = "conquest.capture.losing";
            color = ENEMY_COLOR;
        }

        Font font = mc.font;
        int labelY = height - 92;
        int barY = height - 76;
        int barX = (width - BAR_WIDTH) / 2;

        graphics.pose().pushPose();
        graphics.pose().translate(width / 2f, labelY, 0);
        graphics.pose().scale(1.5f, 1.5f, 1f);
        graphics.drawCenteredString(font, Component.translatable(labelKey).append(" " + flagLevel + "%"), 0, 0, color);
        graphics.pose().popPose();

        graphics.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xA0000000);
        int fill = Math.round(BAR_WIDTH * flagLevel / 100f);
        graphics.fill(barX, barY, barX + fill, barY + BAR_HEIGHT, color);
    }

    @Nullable
    private static ConquestSyncPacket.PointStatus findPointInZone() {
        for (ConquestSyncPacket.PointStatus p : ConquestClientData.getPoints()) {
            if (p.inZone()) {
                return p;
            }
        }
        return null;
    }
}
