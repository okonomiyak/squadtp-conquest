package uk.iwaservice.squadtpconquest.compat.xaero;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.squadtpconquest.SquadTpConquest;
import uk.iwaservice.squadtpconquest.compat.MapMarkers;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;

import java.util.List;
import java.util.Locale;

/** Shows markers as Xaero third-party waypoints (memory only, never written to waypoint files). */
public final class ConquestXaeroWaypointHandler {

    private static final ResourceLocation ORIGIN = new ResourceLocation(SquadTpConquest.MODID, "conquest");
    private static MinimapSession lastSession;
    private static List<MapMarkers.Marker> shown = List.of();

    public static void refresh() {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        MinimapWorldRootContainer root = session == null ? null : session.getWorldManager().getAutoRootContainer();
        if (root == null) {
            lastSession = null;
            shown = List.of();
            return; // retry tick will try again
        }
        List<MapMarkers.Marker> desired = MapMarkers.collect();
        if (session == lastSession && desired.equals(shown)) {
            return;
        }
        clearOrigin(root);
        for (MapMarkers.Marker m : desired) {
            String dimDir = session.getDimensionHelper()
                    .getDimensionDirectoryName(ResourceKey.create(Registries.DIMENSION, m.dimension()));
            MinimapWorldContainer c = root.addSubContainer(root.getPath().resolve(dimDir));
            Waypoint wp = new Waypoint(m.pos().getX(), m.pos().getY(), m.pos().getZ(), m.name(), initials(m.name()),
                    nearestColor(m.rgb()), WaypointPurpose.NORMAL);
            c.getThirdPartyWaypointManager().get(ORIGIN).add(m.id(), wp);
        }
        lastSession = session;
        shown = desired;
    }

    public static void clear() {
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        MinimapWorldRootContainer root = session == null ? null : session.getWorldManager().getAutoRootContainer();
        if (root != null) {
            clearOrigin(root);
        }
        shown = List.of();
        lastSession = null;
    }

    private static void clearOrigin(MinimapWorldRootContainer root) {
        for (MinimapWorldContainer c : root.getSubContainers()) {
            c.getThirdPartyWaypointManager().clearOrigin(ORIGIN);
        }
    }

    private static String initials(String name) {
        return name.isEmpty() ? "?" : name.substring(0, Math.min(2, name.length())).toUpperCase(Locale.ROOT);
    }

    /** Waypoint colors are a fixed palette, so pick the entry closest in RGB. */
    private static WaypointColor nearestColor(int rgb) {
        WaypointColor best = WaypointColor.values()[0];
        long bestDist = Long.MAX_VALUE;
        for (WaypointColor c : WaypointColor.values()) {
            int hex = c.getHex() & 0xFFFFFF;
            long dr = ((rgb >> 16) & 0xFF) - ((hex >> 16) & 0xFF);
            long dg = ((rgb >> 8) & 0xFF) - ((hex >> 8) & 0xFF);
            long db = (rgb & 0xFF) - (hex & 0xFF);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    private ConquestXaeroWaypointHandler() {}
}
