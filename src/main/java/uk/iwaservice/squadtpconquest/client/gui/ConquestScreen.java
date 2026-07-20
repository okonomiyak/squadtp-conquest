package uk.iwaservice.squadtpconquest.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import uk.iwaservice.squadtp.client.SquadClientData;
import uk.iwaservice.squadtpconquest.client.ConquestClientData;
import uk.iwaservice.squadtpconquest.conquest.GameMode;
import uk.iwaservice.squadtpconquest.conquest.RoundState;
import uk.iwaservice.squadtpconquest.conquest.Team;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;

import java.util.List;
import java.util.Map;

/**
 * Conquest status window, opened by right-clicking a flag block. Player
 * actions (team join, admin radius/start/stop) simply send the corresponding
 * /conquest command, so server-side validation stays the single source of
 * truth exactly like squadtp's GUI.
 *
 * The admin quick-setup controls (radius box + place/spawn buttons) always
 * target the default point named "Alpha" — managing additional named points
 * is done via /conquest point add|remove|list rather than in this small panel.
 */
public class ConquestScreen extends Screen {

    private static final String DEFAULT_POINT = "Alpha";
    private static final int DEFAULT_RADIUS = 10;
    private static final int MAX_POINT_ROWS = 5;

    private static final int HEADER_H = 24;
    private static final int PAD = 12;

    private static final int COLOR_PANEL_BG = 0xF4141420;
    private static final int COLOR_HEADER_BG = 0xFF1F2333;
    private static final int COLOR_ACCENT = 0xFF4A5A8A;
    private static final int COLOR_OUTLINE = 0xFF454A66;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0xA0A8C0;
    private static final int COLOR_TEXT_FAINT = 0x6A7188;
    private static final int COLOR_SEPARATOR = 0x28FFFFFF;

    private int panelWidth = 280;
    private int panelLeft;
    private int panelTop;
    private int panelHeight;
    private int dataRevision = -1;

    // Fixed row offsets, computed once per rebuild so render() can align text
    // with the buttons created at the same y.
    private int ticketsY;
    private int pointsY;
    private int pointRows;
    private int adminY = -1;
    private int teamY;
    private int squadY;

    private EditBox radiusBox;

    public ConquestScreen() {
        super(Component.translatable("conquest.gui.title"));
    }

    @Override
    protected void init() {
        rebuild();
    }

    @Override
    public void tick() {
        if (dataRevision != ConquestClientData.getRevision()) {
            rebuild();
        }
    }

    private void rebuild() {
        clearWidgets();
        dataRevision = ConquestClientData.getRevision();

        panelWidth = Math.min(280, this.width - 16);
        panelLeft = (this.width - panelWidth) / 2;

        int cursor = HEADER_H + 8;
        ticketsY = cursor;
        cursor += 16;

        pointsY = cursor;
        List<ConquestSyncPacket.PointStatus> points = ConquestClientData.getPoints();
        pointRows = points.isEmpty() ? 1 : Math.min(MAX_POINT_ROWS, points.size());
        cursor += pointRows * 11 + 6;

        if (ConquestClientData.canAdmin()) {
            adminY = cursor;
            cursor += 20;

            ConquestSyncPacket.PointStatus defaultPoint = ConquestClientData.getPoint(DEFAULT_POINT);
            radiusBox = new EditBox(this.font, panelLeft + PAD + 40, 0, 50, 16, Component.translatable("conquest.gui.radius"));
            radiusBox.setValue(String.valueOf(defaultPoint != null ? defaultPoint.radius() : DEFAULT_RADIUS));
            radiusBox.setMaxLength(3);
            placeAt(radiusBox, cursor - 4);
            addRenderableWidget(radiusBox);

            Button placePoint = Button.builder(Component.translatable("conquest.gui.place_point"),
                    b -> command("conquest point set " + radiusBox.getValue()))
                    .bounds(panelLeft + panelWidth - PAD - 90, 0, 90, 16).build();
            placeAt(placePoint, cursor - 4);
            addRenderableWidget(placePoint);
            cursor += 20;

            int half = (panelWidth - 2 * PAD - 4) / 2;
            Button spawnA = Button.builder(Component.translatable("conquest.gui.place_spawn_a"),
                    b -> command("conquest spawn set a"))
                    .bounds(panelLeft + PAD, 0, half, 18).build();
            placeAt(spawnA, cursor);
            addRenderableWidget(spawnA);
            Button spawnB = Button.builder(Component.translatable("conquest.gui.place_spawn_b"),
                    b -> command("conquest spawn set b"))
                    .bounds(panelLeft + PAD + half + 4, 0, half, 18).build();
            placeAt(spawnB, cursor);
            addRenderableWidget(spawnB);
            cursor += 24;

            RoundState state = ConquestClientData.getState();
            String toggleKey = switch (state) {
                case WAITING -> "conquest.gui.start";
                case STARTING -> "conquest.gui.cancel";
                case IN_PROGRESS -> "conquest.gui.stop";
                case ENDED -> "conquest.gui.reset";
            };
            String toggleCommand = switch (state) {
                case WAITING -> "conquest start";
                case STARTING, IN_PROGRESS -> "conquest stop";
                case ENDED -> "conquest reset";
            };
            Button toggle = Button.builder(Component.translatable(toggleKey), b -> command(toggleCommand))
                    .bounds(panelLeft + PAD, 0, panelWidth - 2 * PAD, 20).build();
            placeAt(toggle, cursor);
            addRenderableWidget(toggle);
            cursor += 26;
        } else {
            adminY = -1;
        }

        teamY = cursor;
        int half = (panelWidth - 2 * PAD - 4) / 2;
        Button teamA = Button.builder(Component.translatable("conquest.gui.team_a"),
                b -> command("conquest team join a"))
                .bounds(panelLeft + PAD, 0, half, 20).build();
        placeAt(teamA, teamY + 12);
        addRenderableWidget(teamA);
        Button teamB = Button.builder(Component.translatable("conquest.gui.team_b"),
                b -> command("conquest team join b"))
                .bounds(panelLeft + PAD + half + 4, 0, half, 20).build();
        placeAt(teamB, teamY + 12);
        addRenderableWidget(teamB);
        cursor = teamY + 36;

        squadY = cursor;
        int squadRows = SquadClientData.isInSquad() ? Math.min(5, SquadClientData.getMembers().size()) : 0;
        cursor = squadY + 16 + squadRows * 12 + (squadRows == 0 ? 12 : 0);

        panelHeight = cursor + PAD;
        panelTop = Math.max(12, (this.height - panelHeight) / 2 - 8);
    }

    private void placeAt(net.minecraft.client.gui.components.AbstractWidget widget, int relY) {
        widget.setY(panelTop + relY);
    }

    private void command(String command) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(command);
        }
    }

    // --- rendering (reads live data each frame; only widget positions are cached) ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int l = panelLeft;
        int t = panelTop;
        int r = l + panelWidth;
        int b = t + panelHeight;
        graphics.fill(l - 1, t - 1, r + 1, b + 1, 0x90000000);
        graphics.fill(l, t, r, b, COLOR_PANEL_BG);
        graphics.fill(l, t, r, t + HEADER_H, COLOR_HEADER_BG);
        graphics.fill(l, t + HEADER_H, r, t + HEADER_H + 1, COLOR_ACCENT);
        graphics.renderOutline(l - 1, t - 1, panelWidth + 2, panelHeight + 2, COLOR_OUTLINE);
        graphics.drawString(this.font, this.title, l + PAD, t + 8, COLOR_TEXT);

        MutableComponent tickets = Component.literal("A: " + ConquestClientData.getTicketsA())
                .withStyle(ChatFormatting.BLUE)
                .append(Component.literal("   B: " + ConquestClientData.getTicketsB()).withStyle(ChatFormatting.RED));
        graphics.drawString(this.font, tickets, l + PAD, t + ticketsY, COLOR_TEXT);

        List<ConquestSyncPacket.PointStatus> points = ConquestClientData.getPoints();
        if (points.isEmpty()) {
            if (ConquestClientData.getMode() == GameMode.CONQUEST) {
                graphics.drawString(this.font, Component.translatable("conquest.msg.no_point"), l + PAD, t + pointsY, COLOR_TEXT_FAINT);
            }
        } else {
            for (int i = 0; i < pointRows; i++) {
                ConquestSyncPacket.PointStatus p = points.get(i);
                Team flagTeam = Team.resolveActive(p.owner(), p.capturingTeam(), p.flagLevel());
                MutableComponent line = Component.literal(p.name() + "  ").withStyle(ChatFormatting.GRAY)
                        .append(flagTeam.display())
                        .append(Component.literal(" " + (int) p.flagLevel() + "%")
                                .withStyle(p.contested() ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
                if (p.contested()) {
                    line.append(Component.translatable("conquest.hud.contested").withStyle(ChatFormatting.YELLOW));
                }
                graphics.drawString(this.font, line, l + PAD, t + pointsY + i * 11, COLOR_TEXT);
            }
        }

        if (adminY >= 0) {
            graphics.drawString(this.font, Component.literal(DEFAULT_POINT).withStyle(ChatFormatting.GRAY),
                    l + PAD, t + adminY, COLOR_TEXT_DIM);
        }

        graphics.fill(l + PAD, t + teamY - 4, r - PAD, t + teamY - 3, COLOR_SEPARATOR);
        Team yourTeam = ConquestClientData.getYourTeam();
        graphics.drawString(this.font, Component.translatable("conquest.status.your_team",
                        yourTeam == Team.NEUTRAL
                                ? Component.translatable("conquest.status.unassigned")
                                : yourTeam.display()),
                l + PAD, t + teamY, COLOR_TEXT);

        if (SquadClientData.isInSquad()) {
            graphics.drawString(this.font, Component.translatable("conquest.gui.squad_section"), l + PAD, t + squadY, COLOR_TEXT_DIM);
            int row = 0;
            for (Map.Entry<java.util.UUID, String> entry : SquadClientData.getMembers().entrySet()) {
                if (row >= 5) {
                    break;
                }
                MutableComponent name = Component.literal(entry.getValue());
                if (entry.getKey().equals(SquadClientData.getLeader())) {
                    name.append(Component.literal(" ★").withStyle(ChatFormatting.GOLD));
                }
                graphics.drawString(this.font, name, l + PAD + 4, t + squadY + 14 + row * 12, COLOR_TEXT_FAINT);
                row++;
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
