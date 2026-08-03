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
        sector.timeLimitSecondsOverride = tag.getInt("TimeLimitOverride");
        return sector;
    }
}
