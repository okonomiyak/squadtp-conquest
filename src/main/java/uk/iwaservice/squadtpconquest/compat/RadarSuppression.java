package uk.iwaservice.squadtpconquest.compat;

import uk.iwaservice.squadtpconquest.client.ConquestClientData;
import uk.iwaservice.squadtpconquest.conquest.RoundState;

/** Dependency-free gate: true while the local player is a combatant in a running round. */
public final class RadarSuppression {
    public static boolean active() {
        return ConquestClientData.getState() == RoundState.IN_PROGRESS
                && ConquestClientData.getYourTeam().isCombatant();
    }

    private RadarSuppression() {}
}
