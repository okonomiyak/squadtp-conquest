package uk.iwaservice.squadtpconquest.compat;

import net.neoforged.fml.ModList;
import uk.iwaservice.squadtpconquest.SquadTpConquest;

/**
 * Sole gateway into the map mod integrations (JourneyMap, Xaero's Minimap, FTB Chunks). The
 * {@code compat.journeymap}, {@code compat.xaero} and {@code compat.ftbchunks} classes reference
 * those mods' types and are only classloaded behind the {@code isLoaded} checks below, so the mod
 * works unchanged when none of them is installed.
 */
public final class MapCompat {

    /** One optional map mod: loaded flag + broken flag + catch-and-disable. */
    private static final class Integration {
        private final String name;
        private final String[] modIds;
        private Boolean loaded;
        private boolean broken;

        Integration(String name, String... modIds) {
            this.name = name;
            this.modIds = modIds;
        }

        void run(Runnable action) {
            if (loaded == null) {
                boolean any = false;
                for (String id : modIds) {
                    any |= ModList.get().isLoaded(id);
                }
                loaded = any;
            }
            if (!loaded || broken) {
                return;
            }
            try {
                action.run();
            } catch (Throwable t) {
                broken = true;
                SquadTpConquest.LOGGER.warn("{} integration failed, disabling it for this session", name, t);
            }
        }
    }

    private static final Integration JM = new Integration("JourneyMap", "journeymap");
    // Better PvP is Xaero's fair-play build of the same minimap classes.
    private static final Integration XAERO = new Integration("Xaero's Minimap", "xaerominimap", "xaerobetterpvp");
    private static final Integration FTB = new Integration("FTB Chunks", "ftbchunks");

    private static boolean dirty;

    /** Requests a {@link #refresh} on the next client tick, so a burst of packets costs one rebuild. */
    public static void markDirty() {
        dirty = true;
    }

    public static void refreshIfDirty() {
        if (dirty) {
            dirty = false;
            refresh();
        }
    }

    /** Re-renders all markers from the current client data. Safe to call anywhere on the client. */
    public static void refresh() {
        JM.run(() -> uk.iwaservice.squadtpconquest.compat.journeymap.ConquestJmWaypointHandler.refresh());
        XAERO.run(() -> uk.iwaservice.squadtpconquest.compat.xaero.ConquestXaeroWaypointHandler.refresh());
        XAERO.run(() -> uk.iwaservice.squadtpconquest.compat.xaero.ConquestXaeroRadar.update());
        FTB.run(() -> uk.iwaservice.squadtpconquest.compat.ftbchunks.ConquestFtbMapIcons.refresh());
    }

    /** Removes every marker this mod has shown, e.g. on logout, without needing fresh client data. */
    public static void clear() {
        dirty = false;
        JM.run(() -> uk.iwaservice.squadtpconquest.compat.journeymap.ConquestJmWaypointHandler.clear());
        XAERO.run(() -> uk.iwaservice.squadtpconquest.compat.xaero.ConquestXaeroWaypointHandler.clear());
        XAERO.run(() -> uk.iwaservice.squadtpconquest.compat.xaero.ConquestXaeroRadar.clear());
        FTB.run(() -> uk.iwaservice.squadtpconquest.compat.ftbchunks.ConquestFtbMapIcons.clear());
    }

    private MapCompat() {}
}
