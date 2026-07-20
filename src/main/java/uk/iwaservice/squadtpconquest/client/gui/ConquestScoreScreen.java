package uk.iwaservice.squadtpconquest.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import uk.iwaservice.squadtp.client.SquadClientData;
import uk.iwaservice.squadtpconquest.client.ConquestClientData;
import uk.iwaservice.squadtpconquest.conquest.Team;
import uk.iwaservice.squadtpconquest.network.ConquestScoreboardPacket;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Full-round scoreboard (Right Alt by default; Tab stays vanilla). Pure
 * display — every value comes from ConquestClientData/SquadClientData, both
 * kept fresh by S2C packets, so there is nothing to click or submit here.
 */
public class ConquestScoreScreen extends Screen {

    private static final int PAD = 12;
    // Gray/translucent so the game world stays visible behind it while open,
    // matching vanilla's Tab player-list look.
    private static final int COLOR_PANEL_BG = 0x80808080;
    private static final int COLOR_HEADER_BG = 0xA0505050;
    private static final int COLOR_ACCENT = 0xFFB0B0B0;
    private static final int COLOR_OUTLINE = 0xFF909090;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xA0A8C0;
    private static final int COLOR_TEXT_FAINT = 0x6A7188;
    private static final int COLOR_SEPARATOR = 0x28FFFFFF;

    private static final int ROW_SELF_BG = 0x6060A0FF;
    private static final int ROW_SQUAD_BG = 0x5030C060;

    private static final int ICON_SIZE = 12;
    private static final int ICON_GAP = 4;
    private static final int ICON_NEUTRAL = 0xFF808080;
    private static final int ICON_CONTESTED = 0xFFFFDD33;

    private static final int MAX_ROWS = 20;

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;

    public ConquestScoreScreen() {
        super(Component.translatable("conquest.score.title"));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(480, this.width - 16);
        panelHeight = Math.min(320, this.height - 16);
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Deliberately no renderBackground(): this should overlay like vanilla's
        // Tab player list, not dim the whole game view like a menu screen.

        int l = panelLeft;
        int t = panelTop;
        int r = l + panelWidth;
        int b = t + panelHeight;
        graphics.fill(l, t, r, b, COLOR_PANEL_BG);
        graphics.fill(l, t, r, t + 24, COLOR_HEADER_BG);
        graphics.fill(l, t + 24, r, t + 25, COLOR_ACCENT);
        graphics.renderOutline(l - 1, t - 1, panelWidth + 2, panelHeight + 2, COLOR_OUTLINE);

        int elapsed = ConquestClientData.getRoundElapsedSeconds();
        MutableComponent header = this.title.copy().append(Component.literal("   " + formatTime(elapsed)));
        graphics.drawString(this.font, header, l + PAD, t + 8, COLOR_TEXT);

        int cursor = t + 32;
        cursor = renderTicketBar(graphics, l, r, cursor);
        cursor += 4;
        cursor = renderPointIcons(graphics, l, r, cursor);
        cursor += 6;
        cursor = renderSummaryLine(graphics, l, cursor);
        cursor += 4;
        graphics.fill(l + PAD, cursor, r - PAD, cursor + 1, COLOR_SEPARATOR);
        cursor += 8;

        List<ConquestScoreboardPacket.Entry> all = ConquestClientData.getScoreboard();
        List<ConquestScoreboardPacket.Entry> teamA = sortedTeam(all, Team.A);
        List<ConquestScoreboardPacket.Entry> teamB = sortedTeam(all, Team.B);

        int colWidth = (panelWidth - 3 * PAD) / 2;
        int leftColX = l + PAD;
        int rightColX = l + PAD * 2 + colWidth;
        renderColumn(graphics, leftColX, cursor, colWidth, Team.A, teamA);
        renderColumn(graphics, rightColX, cursor, colWidth, Team.B, teamB);

        // Admins are shown here, separately from the team tally above — they
        // never contribute kills/deaths/assists/tickets/captures.
        List<ConquestScoreboardPacket.Entry> admins = new ArrayList<>();
        for (ConquestScoreboardPacket.Entry e : all) {
            if (e.team() == Team.ADMIN) {
                admins.add(e);
            }
        }
        if (!admins.isEmpty()) {
            renderAdminSection(graphics, l, r, b - PAD - this.font.lineHeight, admins);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int renderTicketBar(GuiGraphics graphics, int panelLeft, int panelRight, int y) {
        int barWidth = 260;
        int barHeight = 10;
        int x = panelLeft + (panelWidth - barWidth) / 2;
        int ticketsA = ConquestClientData.getTicketsA();
        int ticketsB = ConquestClientData.getTicketsB();
        int total = Math.max(1, ticketsA + ticketsB);
        int split = Math.round(barWidth * ticketsA / (float) total);

        graphics.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0xA0000000);
        graphics.fill(x, y, x + split, y + barHeight, 0xFF3B6FE0);
        graphics.fill(x + split, y, x + barWidth, y + barHeight, 0xFFE03B3B);

        String aText = String.valueOf(ticketsA);
        String bText = String.valueOf(ticketsB);
        graphics.drawString(this.font, aText, x - this.font.width(aText) - 4, y + 1, COLOR_TEXT);
        graphics.drawString(this.font, bText, x + barWidth + 4, y + 1, COLOR_TEXT);
        return y + barHeight;
    }

    private int renderPointIcons(GuiGraphics graphics, int panelLeft, int panelRight, int y) {
        if (ConquestClientData.getPoints().isEmpty()) {
            return y;
        }
        record PointIcon(String name, Team activeTeam, boolean contested) {}
        List<PointIcon> points = new ArrayList<>();
        for (ConquestSyncPacket.PointStatus p : ConquestClientData.getPoints()) {
            points.add(new PointIcon(p.name(),
                    Team.resolveActive(p.owner(), p.capturingTeam(), p.flagLevel()), p.contested()));
        }

        int totalWidth = points.size() * ICON_SIZE + (points.size() - 1) * ICON_GAP;
        int startX = panelLeft + (panelWidth - totalWidth) / 2;
        Team yourTeam = ConquestClientData.getYourTeam();

        for (int i = 0; i < points.size(); i++) {
            PointIcon icon = points.get(i);
            int x = startX + i * (ICON_SIZE + ICON_GAP);
            int color;
            if (icon.contested()) {
                color = ICON_CONTESTED;
            } else if (icon.activeTeam() == Team.NEUTRAL) {
                color = ICON_NEUTRAL;
            } else if (icon.activeTeam() == yourTeam) {
                color = 0xFF3B6FE0;
            } else {
                color = 0xFFE03B3B;
            }
            graphics.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, color);
            graphics.renderOutline(x, y, ICON_SIZE, ICON_SIZE, 0xFFFFFFFF);
            String initial = icon.name().isEmpty() ? "?" : icon.name().substring(0, 1).toUpperCase();
            graphics.drawCenteredString(this.font, Component.literal(initial),
                    x + ICON_SIZE / 2, y + 2, COLOR_TEXT);
        }
        return y + ICON_SIZE;
    }

    /** Sector-control diff, per-team death totals, and the viewer's own K/D/A + score. */
    private int renderSummaryLine(GuiGraphics graphics, int panelLeft, int y) {
        int sectorsA = 0;
        int sectorsB = 0;
        for (ConquestSyncPacket.PointStatus p : ConquestClientData.getPoints()) {
            Team active = Team.resolveActive(p.owner(), p.capturingTeam(), p.flagLevel());
            if (active == Team.A) {
                sectorsA++;
            } else if (active == Team.B) {
                sectorsB++;
            }
        }
        int deathsA = 0;
        int deathsB = 0;
        UUID self = selfUuid();
        int selfKills = 0;
        int selfDeaths = 0;
        int selfAssists = 0;
        int selfScore = 0;
        for (ConquestScoreboardPacket.Entry e : ConquestClientData.getScoreboard()) {
            if (e.team() == Team.A) {
                deathsA += e.deaths();
            } else if (e.team() == Team.B) {
                deathsB += e.deaths();
            }
            if (e.uuid().equals(self)) {
                selfKills = e.kills();
                selfDeaths = e.deaths();
                selfAssists = e.assists();
                selfScore = e.score();
            }
        }

        MutableComponent line = Component.empty();
        if (!ConquestClientData.getPoints().isEmpty()) {
            line.append(Component.translatable("conquest.score.sectors", sectorsA, sectorsB)
                    .withStyle(ChatFormatting.GRAY))
                    .append("   ");
        }
        line.append(Component.translatable("conquest.score.deaths", deathsA, deathsB).withStyle(ChatFormatting.GRAY))
                .append("   ")
                .append(Component.translatable("conquest.score.your_kda", selfKills, selfDeaths, selfAssists, selfScore)
                        .withStyle(ChatFormatting.YELLOW));
        graphics.drawString(this.font, line, panelLeft + PAD, y, COLOR_TEXT);
        return y + this.font.lineHeight;
    }

    private void renderColumn(GuiGraphics graphics, int x, int y, int width, Team team,
                               List<ConquestScoreboardPacket.Entry> entries) {
        MutableComponent header = team.display().copy()
                .append(Component.literal("  (" + entries.size() + ")").withStyle(ChatFormatting.GRAY));
        graphics.drawString(this.font, header, x, y, COLOR_TEXT);
        y += 12;

        graphics.drawString(this.font, Component.translatable("conquest.score.col_header"), x, y, COLOR_TEXT_FAINT);
        y += 11;

        UUID self = selfUuid();
        java.util.Set<UUID> squadMates = squadMateUuids();

        int shown = Math.min(MAX_ROWS, entries.size());
        int ownRank = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).uuid().equals(self)) {
                ownRank = i + 1;
                break;
            }
        }

        for (int i = 0; i < shown; i++) {
            ConquestScoreboardPacket.Entry e = entries.get(i);
            boolean isSelf = e.uuid().equals(self);
            boolean isSquadMate = !isSelf && squadMates.contains(e.uuid());
            if (isSelf) {
                graphics.fill(x - 2, y - 1, x + width, y + 9, ROW_SELF_BG);
            } else if (isSquadMate) {
                graphics.fill(x - 2, y - 1, x + width, y + 9, ROW_SQUAD_BG);
            }
            drawRow(graphics, x, y, width, i + 1, e);
            y += 10;
        }

        if (ownRank > MAX_ROWS) {
            y += 2;
            graphics.fill(x + PAD, y - 3, x + width, y + 8, COLOR_SEPARATOR);
            ConquestScoreboardPacket.Entry own = entries.get(ownRank - 1);
            graphics.fill(x - 2, y - 1, x + width, y + 9, ROW_SELF_BG);
            drawRow(graphics, x, y, width, ownRank, own);
        }
    }

    private void renderAdminSection(GuiGraphics graphics, int panelLeft, int panelRight, int y,
                                     List<ConquestScoreboardPacket.Entry> admins) {
        graphics.fill(panelLeft + PAD, y - 4, panelRight - PAD, y - 3, COLOR_SEPARATOR);
        MutableComponent line = Component.translatable("conquest.score.spectating").withStyle(ChatFormatting.GOLD)
                .append(": ");
        boolean first = true;
        for (ConquestScoreboardPacket.Entry e : admins) {
            if (!first) {
                line.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            first = false;
            line.append(Component.literal(e.name()).withStyle(ChatFormatting.GRAY));
        }
        graphics.drawString(this.font, line, panelLeft + PAD, y, COLOR_TEXT);
    }

    private void drawRow(GuiGraphics graphics, int x, int y, int width, int rank, ConquestScoreboardPacket.Entry e) {
        graphics.drawString(this.font, "#" + rank, x, y, COLOR_TEXT_DIM);
        graphics.drawString(this.font, e.name(), x + 22, y, COLOR_TEXT);
        String stats = e.score() + "  " + e.kills() + "/" + e.deaths() + "/" + e.assists();
        graphics.drawString(this.font, stats, x + width - this.font.width(stats), y, COLOR_TEXT_DIM);
    }

    private static List<ConquestScoreboardPacket.Entry> sortedTeam(
            List<ConquestScoreboardPacket.Entry> all, Team team) {
        List<ConquestScoreboardPacket.Entry> list = new ArrayList<>();
        for (ConquestScoreboardPacket.Entry e : all) {
            if (e.team() == team) {
                list.add(e);
            }
        }
        list.sort(Comparator.comparingInt(ConquestScoreboardPacket.Entry::score).reversed());
        return list;
    }

    private java.util.Set<UUID> squadMateUuids() {
        if (!SquadClientData.isInSquad()) {
            return java.util.Set.of();
        }
        return SquadClientData.getMembers().keySet();
    }

    @javax.annotation.Nullable
    private UUID selfUuid() {
        return this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getUUID() : null;
    }

    private static String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
