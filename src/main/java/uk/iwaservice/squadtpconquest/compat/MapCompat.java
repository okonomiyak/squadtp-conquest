package uk.iwaservice.squadtpconquest.compat;

import net.minecraftforge.fml.ModList;
import uk.iwaservice.squadtpconquest.SquadTpConquest;

/**
 * Sole gateway into the map mod integrations (JourneyMap, Xaero's Minimap, FTB Chunks). Classes under
 * {@code compat.journeymap}/{@code compat.xaero}/{@code compat.ftbchunks} reference those mods' types and are
 * only classloaded behind the {@code isLoaded} checks below, so the mod works unchanged when none is installed.
 */
public final class MapCompat {

    private static final class Integration {
        private final String[] modIds;
        private Boolean loaded;
        private boolean broken;

        Integration(String... modIds) {
            this.modIds = modIds;
        }

        void run(Runnable r) {
            if (loaded == null) {
                loaded = false;
                for (String id : modIds) {
                    loaded |= ModList.get().isLoaded(id);
                }
            }
            if (!loaded || broken) {
                return;
            }
            try {
                r.run();
            } catch (Throwable t) {
                broken = true;
                SquadTpConquest.LOGGER.warn("Map integration {} failed, disabling it for this session", modIds[0], t);
            }
        }
    }

    private static final Integration JM = new Integration("journeymap");
    // Better PvP is Xaero's fair-play build, same classes.
    private static final Integration XAERO = new Integration("xaerominimap", "xaerobetterpvp");
    private static final Integration FTB = new Integration("ftbchunks");

    /** Re-renders all markers from the current client data. Safe to call anywhere on the client. */
    public static void refresh() {
        JM.run(() -> uk.iwaservice.squadtpconquest.compat.journeymap.ConquestJmWaypointHandler.refresh());
        XAERO.run(() -> uk.iwaservice.squadtpconquest.compat.xaero.ConquestXaeroWaypointHandler.refresh());
        XAERO.run(() -> uk.iwaservice.squadtpconquest.compat.xaero.ConquestXaeroRadar.update());
        FTB.run(() -> uk.iwaservice.squadtpconquest.compat.ftbchunks.ConquestFtbMapIcons.refresh());
    }

    /** Removes every marker this mod has shown, e.g. on logout, without needing fresh client data. */
    public static void clear() {
        JM.run(() -> uk.iwaservice.squadtpconquest.compat.journeymap.ConquestJmWaypointHandler.clear());
        XAERO.run(() -> uk.iwaservice.squadtpconquest.compat.xaero.ConquestXaeroWaypointHandler.clear());
        XAERO.run(() -> uk.iwaservice.squadtpconquest.compat.xaero.ConquestXaeroRadar.clear());
        FTB.run(() -> uk.iwaservice.squadtpconquest.compat.ftbchunks.ConquestFtbMapIcons.clear());
    }

    private MapCompat() {}
}
