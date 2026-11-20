package uk.iwaservice.squadtpconquest.compat.journeymap;

import journeymap.client.api.IClientAPI;
import journeymap.client.api.display.DisplayType;
import journeymap.client.api.display.Waypoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import uk.iwaservice.squadtpconquest.SquadTpConquest;
import uk.iwaservice.squadtpconquest.compat.MapMarkers;

import java.util.List;

/**
 * Renders every capture point as a JourneyMap waypoint, colored by its current owner, plus a
 * temporary marker for each enemy currently spotted by the viewer's team (see
 * {@link uk.iwaservice.squadtpconquest.network.SpotPacket}) and each location currently pinned by
 * a teammate (see {@link uk.iwaservice.squadtpconquest.network.PinPacket}).
 */
public final class ConquestJmWaypointHandler {

    /** What JourneyMap currently shows, so an unchanged sync doesn't rebuild every waypoint. */
    private static List<MapMarkers.Marker> shown = List.of();

    /** Removes every waypoint this mod has shown, without re-adding any. */
    public static void clear() {
        IClientAPI api = ConquestJmPlugin.api();
        if (api != null) {
            api.removeAll(SquadTpConquest.MODID);
            shown = List.of();
        }
    }

    public static void refresh() {
        IClientAPI api = ConquestJmPlugin.api();
        if (api == null) {
            return;
        }
        List<MapMarkers.Marker> desired = api.playerAccepts(SquadTpConquest.MODID, DisplayType.Waypoint)
                ? MapMarkers.collect() : List.of();
        if (desired.equals(shown)) {
            return;
        }
        api.removeAll(SquadTpConquest.MODID);
        for (MapMarkers.Marker w : desired) {
            show(api, waypoint(w.id(), w.name(), w.dimension(), w.pos(), w.rgb()));
        }
        shown = desired;
    }

    private static Waypoint waypoint(String id, String name, ResourceLocation dimension, BlockPos pos, int color) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimension);
        return new Waypoint(SquadTpConquest.MODID, id, name, dimKey, pos)
                .setColor(color)
                .setPersistent(false)
                .setEditable(false);
    }

    private static void show(IClientAPI api, Waypoint waypoint) {
        try {
            api.show(waypoint);
        } catch (Exception e) {
            SquadTpConquest.LOGGER.warn("Failed to show capture point waypoint {}", waypoint.getName(), e);
        }
    }

    private ConquestJmWaypointHandler() {}
}
