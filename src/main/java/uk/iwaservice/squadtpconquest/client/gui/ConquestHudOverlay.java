package uk.iwaservice.squadtpconquest.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import uk.iwaservice.squadtpconquest.client.ConquestClientData;
import uk.iwaservice.squadtpconquest.conquest.GameMode;
import uk.iwaservice.squadtpconquest.conquest.Team;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;

import java.util.List;

/**
 * HUD: top-center ticket bar (your side on the left if you're a combatant, split point tracks the
 * ticket ratio) plus a row of capture point icons below it, one per capture point. Colors are fixed
 * per team (Team A always this blue, Team B always this red — see {@link Team#hudColor()}) rather
 * than self/enemy-relative. Only visible during an active round once the viewer has joined a team;
 * the admin and spectator teams can see it too (Team A fixed on the left, spectating), so anyone
 * can watch the match without joining a side.
 */
public class ConquestHudOverlay implements IGuiOverlay {

    public static final ConquestHudOverlay INSTANCE = new ConquestHudOverlay();

    private static final int BAR_WIDTH = 200;
    private static final int BAR_HEIGHT = 10;
    // Leaves room above the bar for the lead indicator / sector line, which sits at BAR_Y - 10.
    private static final int BAR_Y = 18;

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
        boolean spectating = yourTeam == Team.ADMIN || yourTeam == Team.SPECTATOR;
        if (!ConquestClientData.isActive() || (!yourTeam.isCombatant() && !spectating)) {
            return;
        }

        Font font = mc.font;

        if (ConquestClientData.getMode() == GameMode.BREAKTHROUGH) {
            renderBreakthrough(graphics, font, width, yourTeam, spectating);
            return;
        }

        // Combatants see their own side on the left; a spectating admin always sees Team A on the
        // left instead, since there's no "your side" to anchor on.
        Team leftTeam = spectating ? Team.A : yourTeam;
        Team rightTeam = leftTeam.opponent();
        int leftTickets = leftTeam == Team.A ? ConquestClientData.getTicketsA() : ConquestClientData.getTicketsB();
        int rightTickets = rightTeam == Team.A ? ConquestClientData.getTicketsA() : ConquestClientData.getTicketsB();
        int leftColor = leftTeam.hudColor();
        int rightColor = rightTeam.hudColor();

        int barX = (width - BAR_WIDTH) / 2;
        int barY = BAR_Y;

        graphics.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, 0xA0000000);
        int total = Math.max(1, leftTickets + rightTickets);
        int split = Math.round(BAR_WIDTH * leftTickets / (float) total);
        graphics.fill(barX, barY, barX + split, barY + BAR_HEIGHT, leftColor);
        graphics.fill(barX + split, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, rightColor);

        String leftText = String.valueOf(leftTickets);
        String rightText = String.valueOf(rightTickets);
        graphics.drawString(font, leftText, barX - font.width(leftText) - 4, barY + 1, 0xFFFFFF);
        graphics.drawString(font, rightText, barX + BAR_WIDTH + 4, barY + 1, 0xFFFFFF);

        // Small overall-lead indicator centered above the bar.
        String lead = leftTickets > rightTickets ? "▲" : leftTickets < rightTickets ? "▼" : "=";
        int leadColor = leftTickets > rightTickets ? leftColor : leftTickets < rightTickets ? rightColor : 0xFFFFFF;
        graphics.drawCenteredString(font, Component.literal(lead), barX + BAR_WIDTH / 2, barY - 10, leadColor);

        if (!ConquestClientData.getPoints().isEmpty()) {
            renderPointIcons(graphics, font, width, barY + BAR_HEIGHT + 4, ConquestClientData.getPoints());
        }
    }

    /**
     * Breakthrough: sector progress + role-specific ticket info, then only the active sector's
     * flags. A spectating admin always sees the attacker info (tickets) — "attacker/defender" is
     * meaningless from a neutral viewpoint, so it defaults to whichever side has the more
     * eventful info to show.
     */
    private void renderBreakthrough(GuiGraphics graphics, Font font, int width, Team yourTeam, boolean spectating) {
        Team attackerTeam = ConquestClientData.getAttackerTeam();
        boolean showAttackerInfo = spectating || yourTeam == attackerTeam;
        int barY = BAR_Y;

        graphics.drawCenteredString(font, Component.translatable("conquest.hud.sector",
                ConquestClientData.getSectorIndex(), ConquestClientData.getSectorCount()), width / 2, barY - 10, 0xFFFFFF);

        Component roleLine = showAttackerInfo
                ? Component.translatable("conquest.hud.attacker_tickets", ConquestClientData.getAttackerTickets())
                : Component.translatable("conquest.hud.defending");
        graphics.drawCenteredString(font, roleLine, width / 2, barY, attackerTeam.hudColor());

        int nextY = barY + 10;

        List<ConquestSyncPacket.PointStatus> activePoints = ConquestClientData.getPoints().stream()
                .filter(ConquestSyncPacket.PointStatus::active).toList();
        if (!activePoints.isEmpty()) {
            renderPointIcons(graphics, font, width, nextY + 2, activePoints);
        }
    }

    private void renderPointIcons(GuiGraphics graphics, Font font, int width, int y, List<ConquestSyncPacket.PointStatus> source) {
        List<PointIcon> points = new java.util.ArrayList<>();
        for (ConquestSyncPacket.PointStatus p : source) {
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
