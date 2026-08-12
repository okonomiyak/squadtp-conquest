package uk.iwaservice.squadtpconquest.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Conquest status window, opened by right-clicking a flag block. Player
 * actions (team join, admin radius/start/stop) simply send the corresponding
 * /conquest command, so server-side validation stays the single source of
 * truth exactly like squadtp's GUI.
 *
 * Tabbed: everyone sees STATUS (tickets/points/team/squad); admins additionally
 * get SETUP (point/spawn/zone/mode/round controls) and, only in breakthrough
 * mode, SECTORS. Only one tab's content exists on screen at a time, so the
 * panel is much shorter than the old single-scroll layout.
 *
 * The admin quick-setup controls (radius box + place/spawn buttons) always
 * target the default point named "Alpha" — managing additional named points
 * is done via /conquest point add|remove|list rather than in this small panel.
 */
public class ConquestScreen extends Screen {

    private enum Tab {
        STATUS("conquest.gui.tab_status"),
        SETUP("conquest.gui.tab_setup"),
        SECTORS("conquest.gui.tab_sectors");

        private final String labelKey;

        Tab(String labelKey) {
            this.labelKey = labelKey;
        }
    }

    private static final String DEFAULT_POINT = "Alpha";
    private static final int DEFAULT_RADIUS = 10;
    private static final int MAX_POINT_ROWS = 5;
    private static final int MAX_SECTOR_ROWS = 5;
    private static final int MAX_SQUAD_ROWS = 5;
    private static final int MAX_CALLIN_ROWS = 5;

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

    private Tab activeTab = Tab.STATUS;

    // STATUS tab row offsets, computed once per rebuild so render() can align
    // text with the buttons created at the same y. Only meaningful while
    // activeTab == STATUS; render()/mouseScrolled() gate on the tab, not on
    // sentinel values, so stale leftovers from a previous tab can't leak through.
    private int ticketsY;
    private int pointsY;
    private int pointRows;
    private int teamY;
    private int squadY;
    private int callInY;
    private int callInRows;

    // SETUP tab.
    private int adminY;

    // SECTORS tab (breakthrough only).
    private int sectorY;
    private int sectorRows;
    private Map<Integer, List<String>> sectorPoints = Map.of();
    private List<Integer> sectorNumbersOrdered = List.of();

    // Scroll offsets for the three lists that can overflow their fixed row cap. Persist across
    // rebuild() (unlike EditBox contents) so scrolling survives the once-a-second data refresh.
    private int pointScrollOffset;
    private int sectorScrollOffset;
    private int squadScrollOffset;
    private int callInScrollOffset;

    private EditBox radiusBox;
    private EditBox sectorNumberBox;
    private EditBox sectorRadiusBox;
    private EditBox pointNameBox;
    private EditBox timeLimitBox;

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

        boolean admin = ConquestClientData.canAdmin();
        List<Tab> tabs = new ArrayList<>();
        tabs.add(Tab.STATUS);
        if (admin) {
            tabs.add(Tab.SETUP);
            if (ConquestClientData.getMode() == GameMode.BREAKTHROUGH) {
                tabs.add(Tab.SECTORS);
            }
        }
        if (!tabs.contains(activeTab)) {
            activeTab = Tab.STATUS;
        }

        int cursor = HEADER_H + 8;

        if (tabs.size() > 1) {
            int gap = 4;
            int tabWidth = (panelWidth - 2 * PAD - gap * (tabs.size() - 1)) / tabs.size();
            for (int i = 0; i < tabs.size(); i++) {
                Tab tab = tabs.get(i);
                Button tabButton = Button.builder(Component.translatable(tab.labelKey), b -> {
                            activeTab = tab;
                            rebuild();
                        })
                        .bounds(panelLeft + PAD + i * (tabWidth + gap), 0, tabWidth, 16).build();
                tabButton.active = tab != activeTab;
                placeAt(tabButton, cursor);
                addRenderableWidget(tabButton);
            }
            cursor += 20;
        }

        if (activeTab == Tab.STATUS) {
            cursor = buildStatusTab(cursor);
        } else if (activeTab == Tab.SETUP) {
            cursor = buildSetupTab(cursor);
        } else {
            cursor = buildSectorSection(cursor);
        }

        panelHeight = cursor + PAD;
        panelTop = Math.max(12, (this.height - panelHeight) / 2 - 8);
    }

    /** Tickets/point list (read-only to everyone) + team join + squad roster. */
    private int buildStatusTab(int cursor) {
        ticketsY = cursor;
        cursor += 16;

        pointsY = cursor;
        List<ConquestSyncPacket.PointStatus> points = ConquestClientData.getPoints();
        pointScrollOffset = clamp(pointScrollOffset, 0, Math.max(0, points.size() - MAX_POINT_ROWS));
        pointRows = points.isEmpty() ? 1 : Math.min(MAX_POINT_ROWS, points.size());
        cursor += pointRows * 11 + 6;

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
        boolean inSquad = SquadClientData.isInSquad();
        List<ConquestSyncPacket.SquadStatus> joinableSquads = ConquestClientData.getJoinableSquads();
        int squadTotal = inSquad ? SquadClientData.getMembers().size() : joinableSquads.size();
        squadScrollOffset = clamp(squadScrollOffset, 0, Math.max(0, squadTotal - MAX_SQUAD_ROWS));
        int squadRows = (inSquad || !joinableSquads.isEmpty()) ? Math.min(MAX_SQUAD_ROWS, squadTotal) : 0;
        cursor = squadY + 16 + squadRows * 12 + (squadRows == 0 ? 12 : 0);
        // Not in a squad: each visible row of other-squads-on-your-team gets a "request to join"
        // button (/squad join <leaderName>, squadtp's own request-join flow — the leader approves
        // or it joins immediately if the squad has open join enabled). Your own squad's member
        // list (when you're in one) is plain text with no widgets, drawn in render() instead.
        if (!inSquad) {
            for (int row = 0; row < squadRows; row++) {
                ConquestSyncPacket.SquadStatus squad = joinableSquads.get(squadScrollOffset + row);
                Button join = Button.builder(Component.translatable("conquest.gui.squad_join"),
                        b -> command("squad join " + squad.leaderName()))
                        .bounds(panelLeft + panelWidth - PAD - 44, 0, 44, 12).build();
                placeAt(join, squadY + 12 + row * 12);
                addRenderableWidget(join);
            }
        }

        List<ConquestSyncPacket.CallInStatus> callIns = ConquestClientData.getCallIns();
        if (!callIns.isEmpty()) {
            cursor += 4;
            callInY = cursor;
            cursor += 12;
            callInScrollOffset = clamp(callInScrollOffset, 0, Math.max(0, callIns.size() - MAX_CALLIN_ROWS));
            callInRows = Math.min(MAX_CALLIN_ROWS, callIns.size());
            int availableScore = ConquestClientData.getAvailableScore();
            for (int row = 0; row < callInRows; row++) {
                ConquestSyncPacket.CallInStatus callIn = callIns.get(callInScrollOffset + row);
                Button use = Button.builder(Component.translatable("conquest.gui.callin_use"),
                        b -> command("conquest callin use " + callIn.name()))
                        .bounds(panelLeft + panelWidth - PAD - 40, 0, 40, 12).build();
                use.active = availableScore >= callIn.scoreCost();
                placeAt(use, cursor + row * 13);
                addRenderableWidget(use);
            }
            cursor += callInRows * 13 + 4;
        }

        return cursor;
    }

    /** Default point placement, team spawns, home zone corners, mode switch, round control. */
    private int buildSetupTab(int cursor) {
        adminY = cursor;
        cursor += 20;

        String previousRadius = radiusBox != null ? radiusBox.getValue() : null;
        radiusBox = new EditBox(this.font, panelLeft + PAD + 40, 0, 50, 16, Component.translatable("conquest.gui.radius"));
        if (previousRadius != null) {
            radiusBox.setValue(previousRadius);
        } else {
            ConquestSyncPacket.PointStatus defaultPoint = ConquestClientData.getPoint(DEFAULT_POINT);
            radiusBox.setValue(String.valueOf(defaultPoint != null ? defaultPoint.radius() : DEFAULT_RADIUS));
        }
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
        cursor += 22;

        // Home zones apply in every game mode (unlike the sector tab), so they sit here
        // alongside spawn setup. The zone is a box between two corners (not a radius), so
        // each team gets a "stand here" button per corner rather than a single "place" button;
        // set/remove rely on server-side validation for feedback (chat message), same as every
        // other admin button in this tab.
        Button zoneACorner1 = Button.builder(Component.translatable("conquest.gui.zone_a_corner1"),
                b -> command("conquest zone corner1 set a"))
                .bounds(panelLeft + PAD, 0, half, 16).build();
        placeAt(zoneACorner1, cursor);
        addRenderableWidget(zoneACorner1);
        Button zoneACorner2 = Button.builder(Component.translatable("conquest.gui.zone_a_corner2"),
                b -> command("conquest zone corner2 set a"))
                .bounds(panelLeft + PAD + half + 4, 0, half, 16).build();
        placeAt(zoneACorner2, cursor);
        addRenderableWidget(zoneACorner2);
        cursor += 18;

        Button zoneBCorner1 = Button.builder(Component.translatable("conquest.gui.zone_b_corner1"),
                b -> command("conquest zone corner1 set b"))
                .bounds(panelLeft + PAD, 0, half, 16).build();
        placeAt(zoneBCorner1, cursor);
        addRenderableWidget(zoneBCorner1);
        Button zoneBCorner2 = Button.builder(Component.translatable("conquest.gui.zone_b_corner2"),
                b -> command("conquest zone corner2 set b"))
                .bounds(panelLeft + PAD + half + 4, 0, half, 16).build();
        placeAt(zoneBCorner2, cursor);
        addRenderableWidget(zoneBCorner2);
        cursor += 18;

        Button removeZoneA = Button.builder(Component.translatable("conquest.gui.remove_zone_a"),
                b -> command("conquest zone remove a"))
                .bounds(panelLeft + PAD, 0, half, 14).build();
        placeAt(removeZoneA, cursor);
        addRenderableWidget(removeZoneA);
        Button removeZoneB = Button.builder(Component.translatable("conquest.gui.remove_zone_b"),
                b -> command("conquest zone remove b"))
                .bounds(panelLeft + PAD + half + 4, 0, half, 14).build();
        placeAt(removeZoneB, cursor);
        addRenderableWidget(removeZoneB);
        cursor += 20;

        GameMode currentMode = ConquestClientData.getMode();
        GameMode nextMode = currentMode == GameMode.CONQUEST ? GameMode.BREAKTHROUGH : GameMode.CONQUEST;
        Button modeToggle = Button.builder(Component.translatable("conquest.gui.mode_toggle", currentMode.display()),
                b -> command("conquest mode set " + nextMode.key()))
                .bounds(panelLeft + PAD, 0, panelWidth - 2 * PAD, 18).build();
        placeAt(modeToggle, cursor);
        addRenderableWidget(modeToggle);
        cursor += 22;

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

        return cursor;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Right-aligned "3-7/12"-style hint, shown only when a list has more entries than fit (scroll here). */
    private void drawScrollHint(GuiGraphics graphics, int panelRight, int y, int offset, int visibleRows, int total) {
        if (total <= visibleRows) {
            return;
        }
        String text = (offset + 1) + "-" + Math.min(total, offset + visibleRows) + "/" + total;
        graphics.drawString(this.font, text, panelRight - PAD - this.font.width(text), y, COLOR_TEXT_FAINT);
    }

    /**
     * Sector layout management: list of existing sectors (with a remove button each), then
     * fields to add a new capture point to a sector (creating it if new) and set its
     * attacker/defender spawn and time-limit override. Full point/spawn CRUD stays
     * command-only for the base conquest point ("Alpha") — this tab is breakthrough-only.
     */
    private int buildSectorSection(int cursor) {
        Map<Integer, List<String>> allSectorPoints = new TreeMap<>();
        for (ConquestSyncPacket.PointStatus p : ConquestClientData.getPoints()) {
            if (p.sectorNumber() > 0) {
                allSectorPoints.computeIfAbsent(p.sectorNumber(), k -> new ArrayList<>()).add(p.name());
            }
        }
        sectorPoints = allSectorPoints;
        sectorNumbersOrdered = new ArrayList<>(allSectorPoints.keySet());
        sectorScrollOffset = clamp(sectorScrollOffset, 0, Math.max(0, sectorNumbersOrdered.size() - MAX_SECTOR_ROWS));

        sectorY = cursor;
        cursor += 12;

        sectorRows = Math.min(MAX_SECTOR_ROWS, sectorNumbersOrdered.size());
        for (int row = 0; row < sectorRows; row++) {
            int number = sectorNumbersOrdered.get(sectorScrollOffset + row);
            Button remove = Button.builder(Component.literal("x"),
                    b -> command("conquest sector remove " + number))
                    .bounds(panelLeft + panelWidth - PAD - 16, 0, 16, 12).build();
            placeAt(remove, cursor + row * 12);
            addRenderableWidget(remove);
        }
        cursor += Math.max(sectorRows, sectorNumbersOrdered.isEmpty() ? 1 : 0) * 12 + 4;

        int nextNumber = sectorNumbersOrdered.isEmpty() ? 1 : Collections.max(sectorNumbersOrdered) + 1;

        // Number / name / radius fields share one row. This tab has its own radius box (rather
        // than reusing the SETUP tab's) since a player may open SECTORS without ever visiting
        // SETUP this session, leaving that box uncreated.
        int numberW = 34;
        int radiusW = 40;
        int gap = 4;
        int nameW = panelWidth - 2 * PAD - numberW - gap - radiusW - gap;

        String previousSectorNumber = sectorNumberBox != null ? sectorNumberBox.getValue() : null;
        sectorNumberBox = new EditBox(this.font, panelLeft + PAD, 0, numberW, 16, Component.translatable("conquest.gui.sector_number"));
        sectorNumberBox.setValue(previousSectorNumber != null ? previousSectorNumber : String.valueOf(nextNumber));
        sectorNumberBox.setMaxLength(4);
        placeAt(sectorNumberBox, cursor);
        addRenderableWidget(sectorNumberBox);

        String previousPointName = pointNameBox != null ? pointNameBox.getValue() : null;
        pointNameBox = new EditBox(this.font, panelLeft + PAD + numberW + gap, 0, nameW, 16, Component.translatable("conquest.gui.sector_point_name"));
        pointNameBox.setValue(previousPointName != null ? previousPointName : "Point" + nextNumber);
        pointNameBox.setMaxLength(16);
        placeAt(pointNameBox, cursor);
        addRenderableWidget(pointNameBox);

        String previousSectorRadius = sectorRadiusBox != null ? sectorRadiusBox.getValue() : null;
        sectorRadiusBox = new EditBox(this.font, panelLeft + panelWidth - PAD - radiusW, 0, radiusW, 16, Component.translatable("conquest.gui.radius"));
        sectorRadiusBox.setValue(previousSectorRadius != null ? previousSectorRadius : String.valueOf(DEFAULT_RADIUS));
        sectorRadiusBox.setMaxLength(3);
        placeAt(sectorRadiusBox, cursor);
        addRenderableWidget(sectorRadiusBox);
        cursor += 20;

        Button addSectorPoint = Button.builder(Component.translatable("conquest.gui.sector_add_point"),
                b -> command("conquest sector add " + sectorNumberBox.getValue() + " "
                        + pointNameBox.getValue() + " " + sectorRadiusBox.getValue()))
                .bounds(panelLeft + PAD, 0, panelWidth - 2 * PAD, 18).build();
        placeAt(addSectorPoint, cursor);
        addRenderableWidget(addSectorPoint);
        cursor += 22;

        int sectorHalf = (panelWidth - 2 * PAD - 4) / 2;
        Button attackerSpawn = Button.builder(Component.translatable("conquest.gui.sector_spawn_attacker"),
                b -> command("conquest sector spawn set attacker " + sectorNumberBox.getValue()))
                .bounds(panelLeft + PAD, 0, sectorHalf, 18).build();
        placeAt(attackerSpawn, cursor);
        addRenderableWidget(attackerSpawn);
        Button defenderSpawn = Button.builder(Component.translatable("conquest.gui.sector_spawn_defender"),
                b -> command("conquest sector spawn set defender " + sectorNumberBox.getValue()))
                .bounds(panelLeft + PAD + sectorHalf + 4, 0, sectorHalf, 18).build();
        placeAt(defenderSpawn, cursor);
        addRenderableWidget(defenderSpawn);
        cursor += 22;

        String previousTimeLimit = timeLimitBox != null ? timeLimitBox.getValue() : null;
        timeLimitBox = new EditBox(this.font, panelLeft + PAD, 0, 50, 16, Component.translatable("conquest.gui.sector_timelimit"));
        timeLimitBox.setValue(previousTimeLimit != null ? previousTimeLimit : "0");
        timeLimitBox.setMaxLength(6);
        placeAt(timeLimitBox, cursor);
        addRenderableWidget(timeLimitBox);
        Button setTimeLimit = Button.builder(Component.translatable("conquest.gui.sector_timelimit_button"),
                b -> command("conquest sector timelimit set " + sectorNumberBox.getValue() + " " + timeLimitBox.getValue()))
                .bounds(panelLeft + PAD + 54, 0, panelWidth - 2 * PAD - 54, 18).build();
        placeAt(setTimeLimit, cursor);
        addRenderableWidget(setTimeLimit);
        cursor += 24;

        return cursor;
    }

    private void placeAt(AbstractWidget widget, int relY) {
        widget.setY(panelTop + relY);
    }

    private void command(String command) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.connection.sendCommand(command);
        }
    }

    /**
     * Scrolls whichever of the active tab's overflowing lists the cursor is over: points/squad
     * on STATUS, sectors on SECTORS. Points/sectors/joinable-squads reposition per-row buttons, so
     * those call {@link #rebuild()}; your own squad's member list is plain text with no widgets,
     * so its offset alone is enough. Gated by activeTab (not just Y-bounds) since a previous tab's
     * cached row positions could otherwise be accidentally hit-tested against the current tab's
     * content.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int step = delta > 0 ? -1 : delta < 0 ? 1 : 0;
        int localX = (int) mouseX - panelLeft;
        if (step == 0 || localX < 0 || localX > panelWidth) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int localY = (int) mouseY - panelTop;

        if (activeTab == Tab.STATUS) {
            int pointsTotal = ConquestClientData.getPoints().size();
            int pointsBottom = pointsY + Math.max(1, pointRows) * 11 + 6;
            if (pointsTotal > MAX_POINT_ROWS && localY >= pointsY && localY < pointsBottom) {
                pointScrollOffset = clamp(pointScrollOffset + step, 0, pointsTotal - MAX_POINT_ROWS);
                rebuild();
                return true;
            }

            if (SquadClientData.isInSquad()) {
                int squadTotal = SquadClientData.getMembers().size();
                int squadRows = Math.min(MAX_SQUAD_ROWS, squadTotal);
                int squadBottom = squadY + 16 + squadRows * 12;
                if (squadTotal > MAX_SQUAD_ROWS && localY >= squadY && localY < squadBottom) {
                    squadScrollOffset = clamp(squadScrollOffset + step, 0, squadTotal - MAX_SQUAD_ROWS);
                    return true;
                }
            } else {
                // Unlike the plain-text member list above, each row here has its own "request to
                // join" button, so scrolling has to reposition them via rebuild() (same as
                // points/call-ins).
                int squadTotal = ConquestClientData.getJoinableSquads().size();
                int squadRows = Math.min(MAX_SQUAD_ROWS, squadTotal);
                int squadBottom = squadY + 16 + squadRows * 12;
                if (squadTotal > MAX_SQUAD_ROWS && localY >= squadY && localY < squadBottom) {
                    squadScrollOffset = clamp(squadScrollOffset + step, 0, squadTotal - MAX_SQUAD_ROWS);
                    rebuild();
                    return true;
                }
            }

            int callInTotal = ConquestClientData.getCallIns().size();
            int callInBottom = callInY + 14 + Math.max(callInRows, 1) * 13;
            if (callInTotal > MAX_CALLIN_ROWS && localY >= callInY && localY < callInBottom) {
                callInScrollOffset = clamp(callInScrollOffset + step, 0, callInTotal - MAX_CALLIN_ROWS);
                rebuild();
                return true;
            }
        } else if (activeTab == Tab.SECTORS) {
            int sectorBottom = sectorY + 12 + Math.max(sectorRows, 1) * 12;
            if (sectorNumbersOrdered.size() > MAX_SECTOR_ROWS && localY >= sectorY && localY < sectorBottom) {
                sectorScrollOffset = clamp(sectorScrollOffset + step, 0, sectorNumbersOrdered.size() - MAX_SECTOR_ROWS);
                rebuild();
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
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

        if (activeTab == Tab.STATUS) {
            renderStatusTab(graphics, l, t, r);
        } else if (activeTab == Tab.SETUP) {
            graphics.drawString(this.font, Component.literal(DEFAULT_POINT).withStyle(ChatFormatting.GRAY),
                    l + PAD, t + adminY, COLOR_TEXT_DIM);
        } else {
            renderSectorsTab(graphics, l, t, r);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderStatusTab(GuiGraphics graphics, int l, int t, int r) {
        if (ConquestClientData.getMode() == GameMode.BREAKTHROUGH) {
            MutableComponent breakthrough = Component.translatable("conquest.hud.sector",
                            ConquestClientData.getSectorIndex(), ConquestClientData.getSectorCount())
                    .append("   ")
                    .append(Component.translatable("conquest.hud.attacker_tickets", ConquestClientData.getAttackerTickets()));
            graphics.drawString(this.font, breakthrough, l + PAD, t + ticketsY, COLOR_TEXT);
        } else {
            MutableComponent tickets = Component.literal("A: " + ConquestClientData.getTicketsA())
                    .withStyle(ChatFormatting.BLUE)
                    .append(Component.literal("   B: " + ConquestClientData.getTicketsB()).withStyle(ChatFormatting.RED));
            graphics.drawString(this.font, tickets, l + PAD, t + ticketsY, COLOR_TEXT);
        }

        List<ConquestSyncPacket.PointStatus> points = ConquestClientData.getPoints();
        if (points.isEmpty()) {
            if (ConquestClientData.getMode() == GameMode.CONQUEST) {
                graphics.drawString(this.font, Component.translatable("conquest.msg.no_point"), l + PAD, t + pointsY, COLOR_TEXT_FAINT);
            }
        } else {
            // Recompute rows/offset against the live list rather than trusting the cached
            // pointRows/pointScrollOffset from the last rebuild(): a server update (e.g. a
            // sector deletion removing several points at once) can shrink the list between
            // rebuild() calls, and indexing with a stale offset/row-count would throw.
            int visibleRows = Math.min(MAX_POINT_ROWS, points.size());
            int offset = clamp(pointScrollOffset, 0, Math.max(0, points.size() - visibleRows));
            for (int i = 0; i < visibleRows; i++) {
                ConquestSyncPacket.PointStatus p = points.get(offset + i);
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
            drawScrollHint(graphics, r, t + pointsY - 9, offset, visibleRows, points.size());
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
            List<Map.Entry<UUID, String>> members = new ArrayList<>(SquadClientData.getMembers().entrySet());
            // Same live-recompute as the points list above: don't trust the cached
            // squadScrollOffset against a member list that may have shrunk since rebuild().
            int squadRows = Math.min(MAX_SQUAD_ROWS, members.size());
            int offset = clamp(squadScrollOffset, 0, Math.max(0, members.size() - squadRows));
            for (int row = 0; row < squadRows; row++) {
                Map.Entry<UUID, String> entry = members.get(offset + row);
                MutableComponent name = Component.literal(entry.getValue());
                if (entry.getKey().equals(SquadClientData.getLeader())) {
                    name.append(Component.literal(" ★").withStyle(ChatFormatting.GOLD));
                }
                graphics.drawString(this.font, name, l + PAD + 4, t + squadY + 14 + row * 12, COLOR_TEXT_FAINT);
            }
            drawScrollHint(graphics, r, t + squadY, offset, squadRows, members.size());
        } else {
            List<ConquestSyncPacket.SquadStatus> joinableSquads = ConquestClientData.getJoinableSquads();
            if (!joinableSquads.isEmpty()) {
                graphics.drawString(this.font, Component.translatable("conquest.gui.squad_joinable_section"),
                        l + PAD, t + squadY, COLOR_TEXT_DIM);
                // Same live-recompute as the other lists: don't trust the cached squadScrollOffset
                // against a list that may have shrunk since rebuild() (a squad disbanding).
                int squadRows = Math.min(MAX_SQUAD_ROWS, joinableSquads.size());
                int offset = clamp(squadScrollOffset, 0, Math.max(0, joinableSquads.size() - squadRows));
                for (int row = 0; row < squadRows; row++) {
                    ConquestSyncPacket.SquadStatus squad = joinableSquads.get(offset + row);
                    graphics.drawString(this.font, Component.translatable("conquest.gui.squad_joinable_row",
                                    squad.leaderName(), squad.memberCount()),
                            l + PAD + 4, t + squadY + 14 + row * 12, COLOR_TEXT_FAINT);
                }
                drawScrollHint(graphics, r, t + squadY, offset, squadRows, joinableSquads.size());
            }
        }

        List<ConquestSyncPacket.CallInStatus> callIns = ConquestClientData.getCallIns();
        if (!callIns.isEmpty()) {
            int availableScore = ConquestClientData.getAvailableScore();
            graphics.drawString(this.font, Component.translatable("conquest.status.available_score", availableScore),
                    l + PAD, t + callInY, COLOR_TEXT_DIM);
            // Same live-recompute as the other lists: don't trust the cached callInRows/offset
            // against a list that may have shrunk since rebuild() (an OP removing a call-in).
            int rows = Math.min(callInRows, callIns.size());
            int offset = clamp(callInScrollOffset, 0, Math.max(0, callIns.size() - rows));
            for (int row = 0; row < rows; row++) {
                ConquestSyncPacket.CallInStatus callIn = callIns.get(offset + row);
                MutableComponent line = Component.literal(callIn.name() + "  ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(callIn.scoreCost() + " ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(callIn.count() + "x " + callIn.itemId().getPath())
                                .withStyle(ChatFormatting.GRAY));
                graphics.drawString(this.font, line, l + PAD, t + callInY + 14 + row * 13, COLOR_TEXT);
            }
            drawScrollHint(graphics, r, t + callInY, offset, rows, callIns.size());
        }
    }

    private void renderSectorsTab(GuiGraphics graphics, int l, int t, int r) {
        graphics.drawString(this.font, Component.translatable("conquest.msg.sector_list_header"),
                l + PAD, t + sectorY, COLOR_TEXT_DIM);
        if (sectorNumbersOrdered.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("conquest.msg.no_sector"),
                    l + PAD, t + sectorY + 12, COLOR_TEXT_FAINT);
        } else {
            // sectorRows/sectorNumbersOrdered/sectorScrollOffset are all set together in
            // buildSectorSection(), but re-clamp defensively rather than trust that invariant.
            int rows = Math.min(sectorRows, sectorNumbersOrdered.size());
            int offset = clamp(sectorScrollOffset, 0, Math.max(0, sectorNumbersOrdered.size() - rows));
            for (int row = 0; row < rows; row++) {
                int number = sectorNumbersOrdered.get(offset + row);
                List<String> names = sectorPoints.getOrDefault(number, List.of());
                MutableComponent line = Component.literal(number + ": ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(String.join(", ", names)).withStyle(ChatFormatting.WHITE));
                graphics.drawString(this.font, line, l + PAD, t + sectorY + 12 + row * 12, COLOR_TEXT);
            }
            drawScrollHint(graphics, r, t + sectorY - 9, offset, rows, sectorNumbersOrdered.size());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
