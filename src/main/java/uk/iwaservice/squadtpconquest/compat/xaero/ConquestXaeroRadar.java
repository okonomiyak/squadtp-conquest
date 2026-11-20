package uk.iwaservice.squadtpconquest.compat.xaero;

import uk.iwaservice.squadtpconquest.compat.RadarSuppression;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.MinimapProcessor;

/**
 * Disables Xaero's radar by forcing its in-memory "fair play" flag (also turns off cave mode and
 * slime chunks while set). Session-scoped, never saved.
 */
public final class ConquestXaeroRadar {
    /** The processor we forced fair-play on, so we never clear a flag the server itself set. */
    private static MinimapProcessor forcedOn;

    // Edge case: if the server sends its own fair-play code after we forced it, we still clear it at round end.
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
            p.setFairPlayOnlyMessageReceived(false);
            forcedOn = null;
        }
    }

    /** Release on logout/clear. */
    public static void clear() {
        XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
        MinimapProcessor p = session == null ? null : session.getMinimapProcessor();
        if (forcedOn != null && forcedOn == p) {
            p.setFairPlayOnlyMessageReceived(false);
        }
        forcedOn = null;
    }

    private ConquestXaeroRadar() {}
}
