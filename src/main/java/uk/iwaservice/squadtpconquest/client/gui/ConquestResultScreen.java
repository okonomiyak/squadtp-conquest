package uk.iwaservice.squadtpconquest.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import uk.iwaservice.squadtpconquest.client.ConquestClientData;
import uk.iwaservice.squadtpconquest.conquest.GameMode;
import uk.iwaservice.squadtpconquest.conquest.Team;
import uk.iwaservice.squadtpconquest.network.ConquestScoreboardPacket;
import uk.iwaservice.squadtpconquest.network.RoundResultPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Shown on every client when a round ends (see {@link RoundResultPacket}): win/lose/draw from the
 * viewer's side, MVP and award lines, and per-team totals. Everything is computed client-side from
 * {@link ConquestClientData#getScoreboard()} (which carries the round snapshot's teams after the
 * round). Closes itself once the round is no longer ENDED.
 */
public class ConquestResultScreen extends Screen {

    private static final int PAD = 12;
    private static final int COLOR_PANEL_BG = 0x90303030;
    private static final int COLOR_HEADER_BG = 0xA0202020;
    private static final int COLOR_ACCENT = 0xFFB0B0B0;
    private static final int COLOR_OUTLINE = 0xFF909090;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_FAINT = 0xA0A6B4;
    private static final int COLOR_SEPARATOR = 0x28FFFFFF;
    private static final int COL_WIDTH = 46;

    /** One line of the totals table: a label and its kills/deaths/revives/captures/score sums. */
    private record Row(Component label, int[] totals) {}

    private int panelWidth;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    /** Client data revision this screen was built from; a newer one (e.g. late assists) triggers a rebuild. */
    private int builtRevision;

    private Component outcome = Component.empty();
    private int outcomeColor;
    private Component subtitle = Component.empty();
    private final List<Component> lines = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();

    public ConquestResultScreen() {
        super(Component.translatable("conquest.result.reopen"));
    }

    @Override
    protected void init() {
        builtRevision = ConquestClientData.getRevision();
        lines.clear();
        rows.clear();
        RoundResultPacket result = ConquestClientData.getLastResult();
        boolean sdm = ConquestClientData.getMode() == GameMode.SQUAD_DM;
        List<ConquestScoreboardPacket.Entry> entries = new ArrayList<>();
        for (ConquestScoreboardPacket.Entry e : ConquestClientData.getScoreboard()) {
            if (e.team().isCombatant()) {
                entries.add(e);
            }
        }
        if (result != null) {
            subtitle = result.subtitle();
            computeOutcome(result, sdm, entries);
        }
        computeLines(sdm, entries);
        computeRows(sdm, entries);

        panelWidth = Math.min(360, this.width - 16);
        // title + subtitle + separator, text lines, table header + rows, buttons
        panelHeight = PAD + 24 + 12 + 10 + lines.size() * 11 + 7 + 12 + rows.size() * 11 + 10 + 16 + PAD;
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        int by = panelTop + panelHeight - PAD - 16;
        int bw = 80;
        int cx = panelLeft + panelWidth / 2;
        addRenderableWidget(new FlatButton(Component.translatable("conquest.result.scoreboard"),
                b -> this.minecraft.setScreen(new ConquestScoreScreen()), cx - bw - 4, by, bw, 16));
        addRenderableWidget(new FlatButton(Component.translatable("conquest.result.close"),
                b -> onClose(), cx + 4, by, bw, 16));
    }

    /** Win/lose/draw from the viewer's own entry (round snapshot team / squad); a neutral "X wins" for non-participants. */
    private void computeOutcome(RoundResultPacket result, boolean sdm, List<ConquestScoreboardPacket.Entry> entries) {
        boolean draw = sdm ? result.sdmWinner() == 0 : result.winner() == Team.NEUTRAL;
        if (draw) {
            outcome = Component.translatable("conquest.result.draw");
            outcomeColor = 0xFFFF55;
            return;
        }
        ConquestScoreboardPacket.Entry self = null;
        if (this.minecraft != null && this.minecraft.player != null) {
            for (ConquestScoreboardPacket.Entry e : entries) {
                if (e.uuid().equals(this.minecraft.player.getUUID())) {
                    self = e;
                }
            }
        }
        if (self == null) {
            Component winner = sdm ? ConquestHudOverlay.squadLabel(result.sdmWinner()) : result.winner().display();
            outcome = Component.translatable("conquest.result.wins", winner);
            outcomeColor = 0xFFAA00;
            return;
        }
        boolean won = sdm ? self.sdmSquad() == result.sdmWinner() : self.team() == result.winner();
        outcome = Component.translatable(won ? "conquest.result.victory" : "conquest.result.defeat");
        outcomeColor = won ? 0xFFAA00 : 0xFF5555;
    }

    private void computeLines(boolean sdm, List<ConquestScoreboardPacket.Entry> entries) {
        ConquestScoreboardPacket.Entry mvp = top(entries, ConquestScoreboardPacket.Entry::score);
        if (mvp != null) {
            MutableComponent line = Component.translatable("conquest.result.mvp").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(": " + mvp.name() + " (" + mvp.score() + ")").withStyle(ChatFormatting.WHITE));
            lines.add(line);
        }
        if (!sdm) {
            MutableComponent teamTop = Component.empty();
            for (Team team : new Team[]{Team.A, Team.B}) {
                ConquestScoreboardPacket.Entry best = top(entries.stream().filter(e -> e.team() == team).toList(),
                        ConquestScoreboardPacket.Entry::score);
                if (best != null) {
                    if (!teamTop.getSiblings().isEmpty()) {
                        teamTop.append("   ");
                    }
                    teamTop.append(team.display()).append(Component.literal(" " + best.name() + " (" + best.score() + ")")
                            .withStyle(ChatFormatting.WHITE));
                }
            }
            if (!teamTop.getSiblings().isEmpty()) {
                lines.add(teamTop);
            }
        }
        addAward("conquest.result.most_kills", entries, ConquestScoreboardPacket.Entry::kills);
        addAward("conquest.result.most_revives", entries, ConquestScoreboardPacket.Entry::revives);
        addAward("conquest.result.most_captures", entries, ConquestScoreboardPacket.Entry::captures);
    }

    private void addAward(String key, List<ConquestScoreboardPacket.Entry> entries, ToIntFunction<ConquestScoreboardPacket.Entry> stat) {
        ConquestScoreboardPacket.Entry best = top(entries, stat);
        if (best != null && stat.applyAsInt(best) > 0) {
            lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(": " + best.name() + " (" + stat.applyAsInt(best) + ")").withStyle(ChatFormatting.WHITE)));
        }
    }

    private void computeRows(boolean sdm, List<ConquestScoreboardPacket.Entry> entries) {
        if (sdm) {
            java.util.TreeSet<Integer> squads = new java.util.TreeSet<>();
            for (ConquestScoreboardPacket.Entry e : entries) {
                if (e.sdmSquad() > 0) {
                    squads.add(e.sdmSquad());
                }
            }
            for (int squad : squads) {
                rows.add(new Row(ConquestHudOverlay.squadLabel(squad),
                        sum(entries.stream().filter(e -> e.sdmSquad() == squad).toList())));
            }
        } else {
            for (Team team : new Team[]{Team.A, Team.B}) {
                rows.add(new Row(team.display(), sum(entries.stream().filter(e -> e.team() == team).toList())));
            }
        }
    }

    private static int[] sum(List<ConquestScoreboardPacket.Entry> list) {
        int[] t = new int[5];
        for (ConquestScoreboardPacket.Entry e : list) {
            t[0] += e.kills();
            t[1] += e.deaths();
            t[2] += e.revives();
            t[3] += e.captures();
            t[4] += e.score();
        }
        return t;
    }

    /** First entry with the highest value of {@code stat}, or null for an empty list. */
    private static ConquestScoreboardPacket.Entry top(List<ConquestScoreboardPacket.Entry> list, ToIntFunction<ConquestScoreboardPacket.Entry> stat) {
        ConquestScoreboardPacket.Entry best = null;
        for (ConquestScoreboardPacket.Entry e : list) {
            if (best == null || stat.applyAsInt(e) > stat.applyAsInt(best)) {
                best = e;
            }
        }
        return best;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (ConquestClientData.getLastResult() == null) {
            onClose();
        } else if (ConquestClientData.getRevision() != builtRevision) {
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int l = panelLeft;
        int t = panelTop;
        int r = l + panelWidth;
        graphics.fill(l, t, r, t + panelHeight, COLOR_PANEL_BG);
        graphics.fill(l, t, r, t + PAD + 24 + 12 + 2, COLOR_HEADER_BG);
        graphics.fill(l, t + PAD + 24 + 12 + 2, r, t + PAD + 24 + 12 + 3, COLOR_ACCENT);
        graphics.renderOutline(l - 1, t - 1, panelWidth + 2, panelHeight + 2, COLOR_OUTLINE);

        int cx = l + panelWidth / 2;
        int y = t + PAD;
        graphics.pose().pushPose();
        graphics.pose().translate(cx, y, 0);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.drawCenteredString(this.font, outcome, 0, 0, outcomeColor);
        graphics.pose().popPose();
        y += 24;
        graphics.drawCenteredString(this.font, subtitle, cx, y, COLOR_TEXT);
        y += 12 + 10;

        for (Component line : lines) {
            graphics.drawString(this.font, line, l + PAD, y, COLOR_TEXT);
            y += 11;
        }
        y += 2;
        graphics.fill(l + PAD, y, r - PAD, y + 1, COLOR_SEPARATOR);
        y += 4;

        String[] keys = {"kills", "deaths", "revives", "captures", "score"};
        for (int i = 0; i < keys.length; i++) {
            Component h = Component.translatable("conquest.result." + keys[i]);
            graphics.drawString(this.font, h, r - PAD - (keys.length - i - 1) * COL_WIDTH - this.font.width(h), y, COLOR_TEXT_FAINT);
        }
        y += 12;
        for (Row row : rows) {
            graphics.drawString(this.font, row.label(), l + PAD, y, COLOR_TEXT);
            for (int i = 0; i < keys.length; i++) {
                String v = String.valueOf(row.totals()[i]);
                graphics.drawString(this.font, v, r - PAD - (keys.length - i - 1) * COL_WIDTH - this.font.width(v), y, COLOR_TEXT);
            }
            y += 11;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
