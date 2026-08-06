package uk.iwaservice.squadtpconquest.compat;

import net.minecraftforge.fml.ModList;
import uk.iwaservice.squadtpconquest.SquadTpConquest;

/**
 * Sole gateway into the JourneyMap integration. Classes under
 * {@code compat.journeymap} reference JourneyMap API types and are only
 * classloaded behind the {@code isLoaded} check below, so the mod works
 * unchanged when JourneyMap is not installed. Mirrors squadtp's own
 * {@code compat.JourneyMapCompat} (squadtp is not modified by this).
 */
public final class JourneyMapCompat {

    private static Boolean loaded;
    private static boolean broken;

    private static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("journeymap");
        }
        return loaded && !broken;
    }

    /** Re-renders all capture point waypoints from the current client data. Safe to call anywhere on the client. */
    public static void refresh() {
        if (!isLoaded()) {
            return;
        }
        try {
            uk.iwaservice.squadtpconquest.compat.journeymap.ConquestJmWaypointHandler.refresh();
        } catch (Throwable t) {
            broken = true;
            SquadTpConquest.LOGGER.error("JourneyMap integration failed, disabling it for this session", t);
        }
    }

    /** Removes every waypoint this mod has shown, e.g. on logout, without needing fresh client data. */
    public static void clear() {
        if (!isLoaded()) {
            return;
        }
        try {
            uk.iwaservice.squadtpconquest.compat.journeymap.ConquestJmWaypointHandler.clear();
        } catch (Throwable t) {
            broken = true;
            SquadTpConquest.LOGGER.error("JourneyMap integration failed, disabling it for this session", t);
        }
    }

    private JourneyMapCompat() {}
}
