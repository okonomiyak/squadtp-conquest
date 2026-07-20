package uk.iwaservice.squadtpconquest.conquest;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3f;

/**
 * Traces each capture point's radius as a ring of colored dust particles at
 * ground level, refreshed on a short interval. Purely visual (no world
 * modification, nothing to clean up), tinted to match the point's current
 * owner/capturing team so the boundary color reflects who is winning it.
 */
public final class CaptureZoneVisualizer {
    private static final Vector3f COLOR_A = new Vector3f(0.23f, 0.44f, 0.88f);
    private static final Vector3f COLOR_B = new Vector3f(0.88f, 0.23f, 0.23f);
    private static final Vector3f COLOR_NEUTRAL = new Vector3f(0.85f, 0.85f, 0.85f);

    /** Vanilla clamps dust scale to [0.01, 4.0]; this is chunky without clipping. */
    private static final float PARTICLE_SCALE = 3.0f;
    /** Heights (relative to ground) stacked at each boundary point so the ring reads as a wall, not a floor stripe. */
    private static final double[] HEIGHT_OFFSETS = {0.1, 0.9, 1.7};

    public static void render(ServerLevel level, CapturePoint point) {
        Team color = Team.resolveActive(point.getOwner(), point.getCapturingTeam(), point.getFlagLevel());
        DustParticleOptions options = new DustParticleOptions(teamColor(color), PARTICLE_SCALE);

        double cx = point.getPos().getX() + 0.5;
        double baseY = point.getPos().getY();
        double cz = point.getPos().getZ() + 0.5;
        double radius = point.getRadius();

        int segments = Math.max(16, Math.min(64, point.getRadius()));
        for (int i = 0; i < segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            double x = cx + radius * Math.cos(angle);
            double z = cz + radius * Math.sin(angle);
            for (double dy : HEIGHT_OFFSETS) {
                level.sendParticles(options, x, baseY + dy, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private static Vector3f teamColor(Team team) {
        return switch (team) {
            case A -> COLOR_A;
            case B -> COLOR_B;
            // Capture points are never owned/contested by ADMIN, so this is
            // just a safe fallback rather than a real case.
            case NEUTRAL, ADMIN -> COLOR_NEUTRAL;
        };
    }

    private CaptureZoneVisualizer() {}
}
