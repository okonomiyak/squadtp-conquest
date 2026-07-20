package uk.iwaservice.squadtpconquest.client;

import uk.iwaservice.squadtpconquest.conquest.GameMode;
import uk.iwaservice.squadtpconquest.conquest.RoundState;
import uk.iwaservice.squadtpconquest.conquest.Team;
import uk.iwaservice.squadtpconquest.network.ConquestScoreboardPacket;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;

import javax.annotation.Nullable;
import java.util.List;

/** Client-side mirror of the conquest round, fed exclusively by S2C packets. */
public final class ConquestClientData {

    private static List<ConquestSyncPacket.PointStatus> points = List.of();
    private static int ticketsA;
    private static int ticketsB;
    private static boolean active;
    private static RoundState state = RoundState.WAITING;
    private static GameMode mode = GameMode.CONQUEST;
    private static Team yourTeam = Team.NEUTRAL;
    private static boolean canAdmin;
    private static int roundElapsedSeconds;
    private static List<ConquestScoreboardPacket.Entry> scoreboard = List.of();
    /** Incremented on every update; lets the GUI detect changes cheaply. */
    private static int revision;

    public static synchronized void apply(List<ConquestSyncPacket.PointStatus> newPoints,
                                          int newTicketsA, int newTicketsB, boolean newActive, RoundState newState,
                                          GameMode newMode, Team newYourTeam, boolean newCanAdmin) {
        points = List.copyOf(newPoints);
        ticketsA = newTicketsA;
        ticketsB = newTicketsB;
        active = newActive;
        state = newState;
        mode = newMode;
        yourTeam = newYourTeam;
        canAdmin = newCanAdmin;
        revision++;
    }

    public static synchronized void applyScoreboard(int newRoundElapsedSeconds, List<ConquestScoreboardPacket.Entry> newEntries) {
        roundElapsedSeconds = newRoundElapsedSeconds;
        scoreboard = List.copyOf(newEntries);
        revision++;
    }

    public static synchronized int getRevision() {
        return revision;
    }

    public static synchronized int getRoundElapsedSeconds() {
        return roundElapsedSeconds;
    }

    public static synchronized List<ConquestScoreboardPacket.Entry> getScoreboard() {
        return scoreboard;
    }

    public static synchronized List<ConquestSyncPacket.PointStatus> getPoints() {
        return points;
    }

    /** First point in server insertion order, or null if none exist yet. Used by simple single-point UI. */
    @Nullable
    public static synchronized ConquestSyncPacket.PointStatus getPoint(String name) {
        for (ConquestSyncPacket.PointStatus p : points) {
            if (p.name().equals(name)) {
                return p;
            }
        }
        return null;
    }

    public static synchronized int getTicketsA() {
        return ticketsA;
    }

    public static synchronized int getTicketsB() {
        return ticketsB;
    }

    public static synchronized boolean isActive() {
        return active;
    }

    public static synchronized RoundState getState() {
        return state;
    }

    public static synchronized GameMode getMode() {
        return mode;
    }

    public static synchronized Team getYourTeam() {
        return yourTeam;
    }

    public static synchronized boolean canAdmin() {
        return canAdmin;
    }

    private ConquestClientData() {}
}
