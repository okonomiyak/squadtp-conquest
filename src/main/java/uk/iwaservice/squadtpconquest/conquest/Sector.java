package uk.iwaservice.squadtpconquest.conquest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * One front line in a Breakthrough round: a fixed number identifying its place in the
 * attack sequence, the capture points that must all fall to the attacker to clear it, and
 * where each role spawns while it's active. Sectors clear in ascending number order —
 * {@link ConquestManager} tracks which one is currently active rather than this class
 * holding a LOCKED/ACTIVE/CLEARED flag itself.
 */
public final class Sector {
    private final int number;
    private final List<String> pointNames = new ArrayList<>();

    @Nullable
    private ResourceKey<Level> attackerSpawnDim;
    @Nullable
    private BlockPos attackerSpawnPos;
    @Nullable
    private ResourceKey<Level> defenderSpawnDim;
    @Nullable
    private BlockPos defenderSpawnPos;

    /** Seconds; 0 means "use the global breakthrough.sectorTimeLimitSeconds default". */
    private int timeLimitSecondsOverride;

    /**
     * Optional combat area: while this sector is active (and past the post-capture transition
     * grace period), any player outside this box is treated like being outside the battlefield
     * boundary. Both corners must be set (and share combatAreaDim) to be active; unset means
     * this sector falls back to the global /conquest boundary, if any.
     */
    @Nullable
    private ResourceKey<Level> combatAreaDim;
    @Nullable
    private BlockPos combatAreaPos1;
    @Nullable
    private BlockPos combatAreaPos2;

    public Sector(int number) {
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

    public List<String> getPointNames() {
        return pointNames;
    }

    @Nullable
    public ResourceKey<Level> getAttackerSpawnDim() {
        return attackerSpawnDim;
    }

    @Nullable
    public BlockPos getAttackerSpawnPos() {
        return attackerSpawnPos;
    }

    @Nullable
    public ResourceKey<Level> getDefenderSpawnDim() {
        return defenderSpawnDim;
    }

    @Nullable
    public BlockPos getDefenderSpawnPos() {
        return defenderSpawnPos;
    }

    public void setAttackerSpawn(ResourceKey<Level> dim, BlockPos pos) {
        this.attackerSpawnDim = dim;
        this.attackerSpawnPos = pos.immutable();
    }

    public void setDefenderSpawn(ResourceKey<Level> dim, BlockPos pos) {
        this.defenderSpawnDim = dim;
        this.defenderSpawnPos = pos.immutable();
    }

    public int getTimeLimitSecondsOverride() {
        return timeLimitSecondsOverride;
    }

    public void setTimeLimitSecondsOverride(int seconds) {
        this.timeLimitSecondsOverride = seconds;
    }

    public void setCombatArea(ResourceKey<Level> dim, BlockPos pos1, BlockPos pos2) {
        this.combatAreaDim = dim;
        this.combatAreaPos1 = pos1.immutable();
        this.combatAreaPos2 = pos2.immutable();
    }

    /**
     * Sets one corner of the combat area, leaving the other corner untouched (the area only
     * becomes active once both are set). If it already had a corner in a different dimension,
     * both corners are reset first — same rule as the home zone's corner1/corner2 set.
     */
    public void setCombatAreaCorner(ResourceKey<Level> dim, boolean corner1, BlockPos pos) {
        if (combatAreaDim != null && !combatAreaDim.equals(dim)) {
            combatAreaPos1 = null;
            combatAreaPos2 = null;
        }
        combatAreaDim = dim;
        if (corner1) {
            combatAreaPos1 = pos.immutable();
        } else {
            combatAreaPos2 = pos.immutable();
        }
    }

    /** Clears this sector's combat area (falls back to the global boundary). False if it wasn't set. */
    public boolean removeCombatArea() {
        if (combatAreaPos1 == null && combatAreaPos2 == null) {
            return false;
        }
        combatAreaDim = null;
        combatAreaPos1 = null;
        combatAreaPos2 = null;
        return true;
    }

    @Nullable
    public ResourceKey<Level> getCombatAreaDim() {
        return combatAreaDim;
    }

    /** Lower corner of the combat area's box; null unless both corners are set. */
    @Nullable
    public BlockPos getCombatAreaMin() {
        BlockPos[] bounds = combatAreaBounds();
        return bounds == null ? null : bounds[0];
    }

    /** Upper corner of the combat area's box; null unless both corners are set. */
    @Nullable
    public BlockPos getCombatAreaMax() {
        BlockPos[] bounds = combatAreaBounds();
        return bounds == null ? null : bounds[1];
    }

    @Nullable
    private BlockPos[] combatAreaBounds() {
        if (combatAreaPos1 == null || combatAreaPos2 == null) {
            return null;
        }
        BlockPos min = new BlockPos(Math.min(combatAreaPos1.getX(), combatAreaPos2.getX()), Math.min(combatAreaPos1.getY(), combatAreaPos2.getY()), Math.min(combatAreaPos1.getZ(), combatAreaPos2.getZ()));
        BlockPos max = new BlockPos(Math.max(combatAreaPos1.getX(), combatAreaPos2.getX()), Math.max(combatAreaPos1.getY(), combatAreaPos2.getY()), Math.max(combatAreaPos1.getZ(), combatAreaPos2.getZ()));
        return new BlockPos[]{min, max};
    }

    // --- persistence ---

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Number", number);
        ListTag pointList = new ListTag();
        for (String name : pointNames) {
            pointList.add(StringTag.valueOf(name));
        }
        tag.put("Points", pointList);
        if (attackerSpawnDim != null && attackerSpawnPos != null) {
            tag.putString("AttackerSpawnDim", attackerSpawnDim.location().toString());
            tag.put("AttackerSpawnPos", NbtUtils.writeBlockPos(attackerSpawnPos));
        }
        if (defenderSpawnDim != null && defenderSpawnPos != null) {
            tag.putString("DefenderSpawnDim", defenderSpawnDim.location().toString());
            tag.put("DefenderSpawnPos", NbtUtils.writeBlockPos(defenderSpawnPos));
        }
        if (combatAreaDim != null && combatAreaPos1 != null && combatAreaPos2 != null) {
            tag.putString("CombatAreaDim", combatAreaDim.location().toString());
            tag.put("CombatAreaPos1", NbtUtils.writeBlockPos(combatAreaPos1));
            tag.put("CombatAreaPos2", NbtUtils.writeBlockPos(combatAreaPos2));
        }
        tag.putInt("TimeLimitOverride", timeLimitSecondsOverride);
        return tag;
    }

    static Sector load(CompoundTag tag) {
        Sector sector = new Sector(tag.getInt("Number"));
        ListTag pointList = tag.getList("Points", Tag.TAG_STRING);
        for (int i = 0; i < pointList.size(); i++) {
            sector.pointNames.add(pointList.getString(i));
        }
        if (tag.contains("AttackerSpawnDim")) {
            sector.attackerSpawnDim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("AttackerSpawnDim")));
            sector.attackerSpawnPos = NbtUtils.readBlockPos(tag.getCompound("AttackerSpawnPos"));
        }
        if (tag.contains("DefenderSpawnDim")) {
            sector.defenderSpawnDim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("DefenderSpawnDim")));
            sector.defenderSpawnPos = NbtUtils.readBlockPos(tag.getCompound("DefenderSpawnPos"));
        }
        if (tag.contains("CombatAreaDim")) {
            sector.combatAreaDim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("CombatAreaDim")));
            sector.combatAreaPos1 = NbtUtils.readBlockPos(tag.getCompound("CombatAreaPos1"));
            sector.combatAreaPos2 = NbtUtils.readBlockPos(tag.getCompound("CombatAreaPos2"));
        }
        sector.timeLimitSecondsOverride = tag.getInt("TimeLimitOverride");
        return sector;
    }
}
