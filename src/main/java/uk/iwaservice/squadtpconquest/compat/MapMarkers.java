package uk.iwaservice.squadtpconquest.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.squadtpconquest.client.ConquestClientData;
import uk.iwaservice.squadtpconquest.conquest.GameMode;
import uk.iwaservice.squadtpconquest.conquest.RoundState;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Map-mod-independent list of what to show on a minimap: every capture point (colored by its
 * current owner), each enemy currently spotted by the viewer's team and each location currently
 * pinned by a teammate. Shared by the JourneyMap, Xaero and FTB Chunks integrations.
 */
public final class MapMarkers {

    public record Marker(String id, String name, ResourceLocation dimension, BlockPos pos, int rgb) {}

    /** Empty unless a round is IN_PROGRESS. */
    public static List<Marker> collect() {
        List<Marker> desired = new ArrayList<>();
        if (ConquestClientData.getState() == RoundState.IN_PROGRESS) {
            boolean koth = ConquestClientData.getMode() == GameMode.KOTH;
            for (ConquestSyncPacket.PointStatus point : ConquestClientData.getPoints()) {
                if (koth && !point.active()) {
                    continue; // King of the Hill: only the hill and the announced next hill
                }
                int color = (koth ? ConquestClientData.displayTeam(point) : point.owner()).hudColor() & 0xFFFFFF;
                desired.add(new Marker("point_" + point.name(), point.name(), point.dimension(), point.pos(), color));
            }

            // Spots are only ever sent for enemies, so the target's team is always our opponent's.
            int spotColor = ConquestClientData.getYourTeam().opponent().hudColor() & 0xFFFFFF;
            for (var e : ConquestClientData.getSpots().entrySet()) {
                ConquestClientData.SpotEntry spot = e.getValue();
                desired.add(new Marker("spot_" + e.getKey(), spot.name(), spot.dimension(), spot.pos(), spotColor));
            }

            // Pins are only ever sent by/to teammates, so the placer's team is always our own.
            int pinColor = ConquestClientData.getYourTeam().hudColor() & 0xFFFFFF;
            for (var e : ConquestClientData.getPins().entrySet()) {
                ConquestClientData.PinEntry pin = e.getValue();
                desired.add(new Marker("pin_" + e.getKey(), pin.placerName(), pin.dimension(), pin.pos(), pinColor));
            }
        }
        return desired;
    }

    private MapMarkers() {}
}
