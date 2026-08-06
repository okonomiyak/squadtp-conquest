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
import uk.iwaservice.squadtpconquest.client.ConquestClientData;
import uk.iwaservice.squadtpconquest.conquest.RoundState;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;

/** Renders every capture point as a JourneyMap waypoint, colored by its current owner. */
public final class ConquestJmWaypointHandler {

    /** Removes every waypoint this mod has shown, without re-adding any. */
    public static void clear() {
        IClientAPI api = ConquestJmPlugin.api();
        if (api != null) {
            api.removeAll(SquadTpConquest.MODID);
        }
    }

    public static void refresh() {
        IClientAPI api = ConquestJmPlugin.api();
        if (api == null) {
            return;
        }
        api.removeAll(SquadTpConquest.MODID);

        if (ConquestClientData.getState() != RoundState.IN_PROGRESS
                || !api.playerAccepts(SquadTpConquest.MODID, DisplayType.Waypoint)) {
            return;
        }

        for (ConquestSyncPacket.PointStatus point : ConquestClientData.getPoints()) {
            int color = point.owner().hudColor() & 0xFFFFFF;
            show(api, waypoint(point.name(), point.name(), point.dimension(), point.pos(), color));
        }
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
