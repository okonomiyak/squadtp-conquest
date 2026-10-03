package uk.iwaservice.squadtpconquest.compat.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import uk.iwaservice.squadtpconquest.SquadTpConquest;
import uk.iwaservice.squadtpconquest.client.ConquestClientData;
import uk.iwaservice.squadtpconquest.conquest.RoundState;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders every capture point as a JourneyMap waypoint, colored by its current owner, plus a
 * temporary marker for each enemy currently spotted by the viewer's team (see
 * {@link uk.iwaservice.squadtpconquest.network.SpotPacket}) and each location currently pinned by
 * a teammate (see {@link uk.iwaservice.squadtpconquest.network.PinPacket}).
 */
public final class ConquestJmWaypointHandler {

    private record Wp(String name, ResourceLocation dimension, BlockPos pos, int color) {}

    /** What JourneyMap currently shows, so an unchanged sync doesn't rebuild every waypoint. */
    private static List<Wp> shown = List.of();

    /** Removes every waypoint this mod has shown, without re-adding any. */
    public static void clear() {
        IClientAPI api = ConquestJmPlugin.api();
        if (api != null) {
            api.removeAllWaypoints(SquadTpConquest.MODID);
            shown = List.of();
        }
    }

    public static void refresh() {
        IClientAPI api = ConquestJmPlugin.api();
        if (api == null) {
            return;
        }
        List<Wp> desired = new ArrayList<>();
        if (ConquestClientData.getState() == RoundState.IN_PROGRESS) {
            for (ConquestSyncPacket.PointStatus point : ConquestClientData.getPoints()) {
                int color = point.owner().hudColor() & 0xFFFFFF;
                desired.add(new Wp(point.name(), point.dimension(), point.pos(), color));
            }

            // Spots are only ever sent for enemies, so the target's team is always our opponent's.
            int spotColor = ConquestClientData.getYourTeam().opponent().hudColor() & 0xFFFFFF;
            for (ConquestClientData.SpotEntry spot : ConquestClientData.getSpots().values()) {
                desired.add(new Wp(spot.name(), spot.dimension(), spot.pos(), spotColor));
            }

            // Pins are only ever sent by/to teammates, so the placer's team is always our own.
            int pinColor = ConquestClientData.getYourTeam().hudColor() & 0xFFFFFF;
            for (ConquestClientData.PinEntry pin : ConquestClientData.getPins().values()) {
                desired.add(new Wp(pin.placerName(), pin.dimension(), pin.pos(), pinColor));
            }
        }
        if (desired.equals(shown)) {
            return;
        }
        api.removeAllWaypoints(SquadTpConquest.MODID);
        for (Wp w : desired) {
            show(api, waypoint(w.name(), w.dimension(), w.pos(), w.color()));
        }
        shown = desired;
    }

    private static Waypoint waypoint(String name, ResourceLocation dimension, BlockPos pos, int color) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimension);
        Waypoint waypoint = WaypointFactory.createWaypoint(SquadTpConquest.MODID, pos, name, dimKey, false);
        waypoint.setColor(color);
        return waypoint;
    }

    private static void show(IClientAPI api, Waypoint waypoint) {
        try {
            api.addWaypoint(SquadTpConquest.MODID, waypoint);
        } catch (Exception e) {
            SquadTpConquest.LOGGER.warn("Failed to show capture point waypoint {}", waypoint.getName(), e);
        }
    }

    private ConquestJmWaypointHandler() {}
}
