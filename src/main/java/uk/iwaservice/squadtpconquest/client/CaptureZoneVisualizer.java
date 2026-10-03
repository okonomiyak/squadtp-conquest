package uk.iwaservice.squadtpconquest.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import uk.iwaservice.squadtpconquest.conquest.Team;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;
import uk.iwaservice.squadtpconquest.network.ConquestZonesPacket;

/**
 * Traces each capture point's radius, and every zone outline from {@link ConquestZonesPacket}, as
 * colored dust particles spawned locally (identical to what the server used to send one packet per
 * particle for), refreshed on a short interval. Purely visual (no world modification, nothing to
 * clean up); capture rings are tinted to match the point's current owner/capturing team so the
 * boundary color reflects who is winning it.
 */
public final class CaptureZoneVisualizer {

    /** Vanilla clamps dust scale to [0.01, 4.0]; this is chunky without clipping. */
    private static final float PARTICLE_SCALE = 3.0f;
    /** Heights (relative to ground) stacked at each boundary point so the ring reads as a wall, not a floor stripe. */
    private static final double[] HEIGHT_OFFSETS = {0.1, 0.9, 1.7};

    /** Spawns one refresh of every zone in the client's current dimension. */
    public static void render(ClientLevel level) {
        ResourceLocation dim = level.dimension().location();
        for (ConquestSyncPacket.PointStatus point : ConquestClientData.getPoints()) {
            if (point.dimension().equals(dim)) {
                Team color = Team.resolveActive(point.owner(), point.capturingTeam(), point.flagLevel());
                renderRing(level, point.pos(), point.radius(), color.zoneRgb());
            }
        }
        ConquestZonesPacket zones = ConquestClientData.getZones();
        for (ConquestZonesPacket.Box box : zones.boxes()) {
            if (box.dimension().equals(dim)) {
                renderBox(level, box.min(), box.max(), box.rgb());
            }
        }
        for (ConquestZonesPacket.Ring ring : zones.rings()) {
            if (ring.dimension().equals(dim)) {
                renderRing(level, ring.pos(), ring.radius(), ring.rgb());
            }
        }
    }

    private static void renderRing(ClientLevel level, BlockPos pos, int radius, int rgb) {
        DustParticleOptions options = new DustParticleOptions(vec(rgb), PARTICLE_SCALE);

        double cx = pos.getX() + 0.5;
        double baseY = pos.getY();
        double cz = pos.getZ() + 0.5;

        int segments = Math.max(16, Math.min(64, radius));
        for (int i = 0; i < segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            double x = cx + radius * Math.cos(angle);
            double z = cz + radius * Math.sin(angle);
            for (double dy : HEIGHT_OFFSETS) {
                level.addParticle(options, x, baseY + dy, z, 0, 0, 0);
            }
        }
    }

    /** Wireframe box (12 edges) marking a zone's bounds, from its min corner to its max corner (inclusive). */
    private static void renderBox(ClientLevel level, BlockPos min, BlockPos max, int rgb) {
        DustParticleOptions options = new DustParticleOptions(vec(rgb), PARTICLE_SCALE);

        double x1 = min.getX();
        double y1 = min.getY();
        double z1 = min.getZ();
        double x2 = max.getX() + 1;
        double y2 = max.getY() + 1;
        double z2 = max.getZ() + 1;

        // Bottom face, top face, then the four verticals connecting them.
        renderEdge(level, options, x1, y1, z1, x2, y1, z1);
        renderEdge(level, options, x2, y1, z1, x2, y1, z2);
        renderEdge(level, options, x2, y1, z2, x1, y1, z2);
        renderEdge(level, options, x1, y1, z2, x1, y1, z1);

        renderEdge(level, options, x1, y2, z1, x2, y2, z1);
        renderEdge(level, options, x2, y2, z1, x2, y2, z2);
        renderEdge(level, options, x2, y2, z2, x1, y2, z2);
        renderEdge(level, options, x1, y2, z2, x1, y2, z1);

        renderEdge(level, options, x1, y1, z1, x1, y2, z1);
        renderEdge(level, options, x2, y1, z1, x2, y2, z1);
        renderEdge(level, options, x2, y1, z2, x2, y2, z2);
        renderEdge(level, options, x1, y1, z2, x1, y2, z2);
    }

    private static void renderEdge(ClientLevel level, DustParticleOptions options,
                                    double x1, double y1, double z1, double x2, double y2, double z2) {
        double length = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1));
        int segments = Math.max(1, Math.min(64, (int) length));
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            level.addParticle(options, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t, 0, 0, 0);
        }
    }

    private static Vector3f vec(int rgb) {
        return new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }

    private CaptureZoneVisualizer() {}
}
