package uk.iwaservice.squadtpconquest.conquest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A named, admin-defined axis-aligned box (typically drawn inside a battlefield boundary or
 * breakthrough sector's combat area, around a spawn point) in which players can't damage each
 * other and can't break or place blocks — a spawn-camping/spawn-griefing guard. Structurally
 * identical to {@link ProtectZone} but kept as its own type since the two protect against
 * different things (terrain destruction vs. combat/building) and are managed independently via
 * {@code /conquest spawnzone}.
 */
public final class SpawnZone {
    private final String name;
    private final ResourceKey<Level> dim;
    private final BlockPos min;
    private final BlockPos max;

    public SpawnZone(String name, ResourceKey<Level> dim, BlockPos pos1, BlockPos pos2) {
        this.name = name;
        this.dim = dim;
        this.min = new BlockPos(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
        this.max = new BlockPos(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
    }

    public String getName() {
        return name;
    }

    public ResourceKey<Level> getDim() {
        return dim;
    }

    public BlockPos getMin() {
        return min;
    }

    public BlockPos getMax() {
        return max;
    }

    // --- persistence ---

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putString("Dim", dim.location().toString());
        tag.put("Min", NbtUtils.writeBlockPos(min));
        tag.put("Max", NbtUtils.writeBlockPos(max));
        return tag;
    }

    static SpawnZone load(CompoundTag tag) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("Dim")));
        BlockPos min = NbtUtils.readBlockPos(tag.getCompound("Min"));
        BlockPos max = NbtUtils.readBlockPos(tag.getCompound("Max"));
        return new SpawnZone(tag.getString("Name"), dim, min, max);
    }
}
