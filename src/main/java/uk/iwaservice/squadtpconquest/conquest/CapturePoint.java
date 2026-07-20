package uk.iwaservice.squadtpconquest.conquest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A single capture point: position + radius, current owner and flag progress.
 * All mutation happens server-side through {@link ConquestManager}.
 */
public class CapturePoint {
    private final String name;
    private final ResourceKey<Level> dimension;
    private final BlockPos pos;
    private final int radius;

    /** Team that has fully captured the point; NEUTRAL while unowned. */
    private Team owner = Team.NEUTRAL;
    /** Team currently raising a neutral flag; NEUTRAL when nobody is. */
    private Team capturingTeam = Team.NEUTRAL;
    /** Flag height in percent: 0 = fully lowered, 100 = fully raised. */
    private double flagLevel;

    public CapturePoint(String name, ResourceKey<Level> dimension, BlockPos pos, int radius) {
        this.name = name;
        this.dimension = dimension;
        this.pos = pos.immutable();
        this.radius = radius;
    }

    public String getName() {
        return name;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getRadius() {
        return radius;
    }

    public Team getOwner() {
        return owner;
    }

    public Team getCapturingTeam() {
        return capturingTeam;
    }

    public double getFlagLevel() {
        return flagLevel;
    }

    /** Resets ownership and progress (used on game start). */
    public void reset() {
        owner = Team.NEUTRAL;
        capturingTeam = Team.NEUTRAL;
        flagLevel = 0;
    }

    /** Outcome of one capture tick that callers may want to announce. */
    public enum CaptureEvent { NONE, NEUTRALIZED, CAPTURED }

    /**
     * Advances the capture state by one tick in favor of {@code team}, which is
     * the only team inside the zone. {@code rate} is percent per tick.
     */
    public CaptureEvent advance(Team team, double rate) {
        if (owner == team) {
            // Re-raise an owned flag that was partially torn down.
            flagLevel = Math.min(100, flagLevel + rate);
            return CaptureEvent.NONE;
        }
        if (owner != Team.NEUTRAL) {
            // Enemy-owned: tear the flag down until it neutralizes.
            flagLevel -= rate;
            if (flagLevel <= 0) {
                flagLevel = 0;
                owner = Team.NEUTRAL;
                capturingTeam = team;
                return CaptureEvent.NEUTRALIZED;
            }
            return CaptureEvent.NONE;
        }
        // Neutral: undo the other team's partial raise before raising our own.
        if (capturingTeam != team && capturingTeam != Team.NEUTRAL) {
            flagLevel -= rate;
            if (flagLevel <= 0) {
                flagLevel = 0;
                capturingTeam = team;
            }
            return CaptureEvent.NONE;
        }
        capturingTeam = team;
        flagLevel += rate;
        if (flagLevel >= 100) {
            flagLevel = 100;
            owner = team;
            capturingTeam = Team.NEUTRAL;
            return CaptureEvent.CAPTURED;
        }
        return CaptureEvent.NONE;
    }

    // --- persistence ---

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putString("Dim", dimension.location().toString());
        tag.put("Pos", NbtUtils.writeBlockPos(pos));
        tag.putInt("Radius", radius);
        tag.putString("Owner", owner.name());
        tag.putString("Capturing", capturingTeam.name());
        tag.putDouble("FlagLevel", flagLevel);
        return tag;
    }

    public static CapturePoint load(CompoundTag tag) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("Dim")));
        CapturePoint point = new CapturePoint(tag.getString("Name"), dim,
                NbtUtils.readBlockPos(tag.getCompound("Pos")), tag.getInt("Radius"));
        point.owner = Team.valueOf(tag.getString("Owner"));
        point.capturingTeam = Team.valueOf(tag.getString("Capturing"));
        point.flagLevel = tag.getDouble("FlagLevel");
        return point;
    }
}
