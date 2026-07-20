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

import java.util.List;

/**
 * HUD: top-center ticket bar (your side always on the left, split point
 * tracks the ticket ratio) plus a row of capture point icons below it, one
 * per capture point. Colors are fixed per team (Team A always this blue,
 * Team B always this red — see {@link Team#hudColor()}) rather than
 * self/enemy-relative. Only visible during an active round once the viewer
 * has joined a team.
 */
public class ConquestHudOverlay implements IGuiOverlay {

    public static final ConquestHudOverlay INSTANCE = new ConquestHudOverlay();

    private static final int BAR_WIDTH = 200;
    private static final int BAR_HEIGHT = 10;
    private static final int BAR_Y = 6;

    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 6;
    private static final int ICON_CONTESTED = 0xFFFFDD33;

    private record PointIcon(String name, Team activeTeam, boolean contested, int flagPercent) {}

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

        Font font = mc.font;
        int selfTickets = yourTeam == Team.A ? ConquestClientData.getTicketsA() : ConquestClientData.getTicketsB();
        int enemyTickets = yourTeam == Team.A ? ConquestClientData.getTicketsB() : ConquestClientData.getTicketsA();
        int selfColor = yourTeam.hudColor();
        int enemyColor = yourTeam.opponent().hudColor();

        int barX = (width - BAR_WIDTH) / 2;
        int barY = BAR_Y;

        graphics.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xA0000000);
        int total = Math.max(1, selfTickets + enemyTickets);
        int split = Math.round(BAR_WIDTH * selfTickets / (float) total);
        graphics.fill(barX, barY, barX + split, barY + BAR_HEIGHT, selfColor);
        graphics.fill(barX + split, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, enemyColor);

        String selfText = String.valueOf(selfTickets);
        String enemyText = String.valueOf(enemyTickets);
        graphics.drawString(font, selfText, barX - font.width(selfText) - 4, barY + 1, 0xFFFFFF);
        graphics.drawString(font, enemyText, barX + BAR_WIDTH + 4, barY + 1, 0xFFFFFF);

        // Small overall-lead indicator centered above the bar.
        String lead = selfTickets > enemyTickets ? "▲" : selfTickets < enemyTickets ? "▼" : "=";
        int leadColor = selfTickets > enemyTickets ? selfColor : selfTickets < enemyTickets ? enemyColor : 0xFFFFFF;
        graphics.drawCenteredString(font, Component.literal(lead), barX + BAR_WIDTH / 2, barY - 10, leadColor);

        if (!ConquestClientData.getPoints().isEmpty()) {
            renderPointIcons(graphics, font, width, barY + BAR_HEIGHT + 4);
        }
    }

    private void renderPointIcons(GuiGraphics graphics, Font font, int width, int y) {
        List<PointIcon> points = new java.util.ArrayList<>();
        for (ConquestSyncPacket.PointStatus p : ConquestClientData.getPoints()) {
            points.add(new PointIcon(p.name(),
                    Team.resolveActive(p.owner(), p.capturingTeam(), p.flagLevel()),
                    p.contested(), (int) p.flagLevel()));
        }

        int totalWidth = points.size() * ICON_SIZE + (points.size() - 1) * ICON_GAP;
        int startX = (width - totalWidth) / 2;

        for (int i = 0; i < points.size(); i++) {
            PointIcon icon = points.get(i);
            int x = startX + i * (ICON_SIZE + ICON_GAP);
            int color = icon.contested() ? ICON_CONTESTED : icon.activeTeam().hudColor();
            graphics.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, color);
            graphics.renderOutline(x, y, ICON_SIZE, ICON_SIZE, 0xFFFFFFFF);
            String initial = icon.name().isEmpty() ? "?" : icon.name().substring(0, 1).toUpperCase();
            graphics.drawCenteredString(font, Component.literal(initial),
                    x + ICON_SIZE / 2, y + (ICON_SIZE - 8) / 2, 0xFFFFFF);

            String percent = icon.flagPercent() + "%";
            graphics.drawCenteredString(font, Component.literal(percent),
                    x + ICON_SIZE / 2, y + ICON_SIZE + 2, icon.contested() ? ICON_CONTESTED : 0xFFFFFF);
        }
    }
}
