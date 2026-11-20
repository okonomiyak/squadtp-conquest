package uk.iwaservice.squadtpconquest.compat;

import uk.iwaservice.squadtpconquest.client.ConquestClientData;
import uk.iwaservice.squadtpconquest.conquest.RoundState;

/** Dependency-free gate: true while the minimap player radar should be disabled for this client. */
public final class RadarSuppression {
    public static boolean active() {
        return ConquestClientData.getState() == RoundState.IN_PROGRESS
                && ConquestClientData.getYourTeam().isCombatant();
    }

    private RadarSuppression() {}
}
