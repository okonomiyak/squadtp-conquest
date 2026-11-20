package uk.iwaservice.squadtpconquest.compat.xaero;

import uk.iwaservice.squadtpconquest.compat.RadarSuppression;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.MinimapProcessor;

/** Disables Xaero's radar by forcing its in-memory fair-play flag (session-scoped, never saved). */
public final class ConquestXaeroRadar {
    /** The processor we forced fair-play on, so we never clear a flag the server itself set. */
    private static MinimapProcessor forcedOn;

    public static void update() {
        XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
        MinimapProcessor p = session == null ? null : session.getMinimapProcessor();
        if (p == null) {
            forcedOn = null;
            return;
        }
        if (RadarSuppression.active()) {
            if (!p.getForcedFairPlay()) {
                p.setFairPlayOnlyMessageReceived(true);
                forcedOn = p;
            }
        } else if (forcedOn == p) {
            // Edge case: a server fair-play code received after we forced it is cleared here too.
            p.setFairPlayOnlyMessageReceived(false);
            forcedOn = null;
        }
    }

    /** Logout: the session (and the flag) is discarded anyway. */
    public static void clear() {
        forcedOn = null;
    }

    private ConquestXaeroRadar() {}
}
