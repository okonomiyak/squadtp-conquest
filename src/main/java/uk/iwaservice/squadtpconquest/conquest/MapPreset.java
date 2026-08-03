package uk.iwaservice.squadtpconquest.conquest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A named, reusable map layout: capture point positions/radii, team spawns
 * and the game mode they belong to. Captured/loaded from the live
 * {@link ConquestManager} state, but holds no round-in-progress data
 * (ownership, tickets, scores) — only what's needed to rebuild the setup.
 */
public final class MapPreset {

    /** One capture point's layout, without owner/capture-progress state. */
    public record PointLayout(String name, ResourceKey<Level> dimension, BlockPos pos, int radius) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", name);
            tag.putString("Dim", dimension.location().toString());
            tag.put("Pos", NbtUtils.writeBlockPos(pos));
            tag.putInt("Radius", radius);
            return tag;
        }

        static PointLayout load(CompoundTag tag) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("Dim")));
            return new PointLayout(tag.getString("Name"), dim, NbtUtils.readBlockPos(tag.getCompound("Pos")), tag.getInt("Radius"));
        }
    }

    private final String name;
    private final GameMode mode;
    private final List<PointLayout> points;
    @Nullable
    private final ResourceKey<Level> spawnADim;
    @Nullable
    private final BlockPos spawnAPos;
    @Nullable
    private final ResourceKey<Level> spawnBDim;
    @Nullable
    private final BlockPos spawnBPos;

    public MapPreset(String name, GameMode mode, List<PointLayout> points,
                      @Nullable ResourceKey<Level> spawnADim, @Nullable BlockPos spawnAPos,
                      @Nullable ResourceKey<Level> spawnBDim, @Nullable BlockPos spawnBPos) {
        this.name = name;
        this.mode = mode;
        this.points = points;
        this.spawnADim = spawnADim;
        this.spawnAPos = spawnAPos;
        this.spawnBDim = spawnBDim;
        this.spawnBPos = spawnBPos;
    }

    public String getName() {
        return name;
    }

    public GameMode getMode() {
        return mode;
    }

    public List<PointLayout> getPoints() {
        return points;
    }

    @Nullable
    public ResourceKey<Level> getSpawnADim() {
        return spawnADim;
    }

    @Nullable
    public BlockPos getSpawnAPos() {
        return spawnAPos;
    }

    @Nullable
    public ResourceKey<Level> getSpawnBDim() {
        return spawnBDim;
    }

    @Nullable
    public BlockPos getSpawnBPos() {
        return spawnBPos;
    }

    // --- persistence ---

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putString("Mode", mode.name());
        ListTag pointList = new ListTag();
        for (PointLayout point : points) {
            pointList.add(point.save());
        }
        tag.put("Points", pointList);
        if (spawnADim != null && spawnAPos != null) {
            tag.putString("SpawnADim", spawnADim.location().toString());
            tag.put("SpawnAPos", NbtUtils.writeBlockPos(spawnAPos));
        }
        if (spawnBDim != null && spawnBPos != null) {
            tag.putString("SpawnBDim", spawnBDim.location().toString());
            tag.put("SpawnBPos", NbtUtils.writeBlockPos(spawnBPos));
        }
        return tag;
    }

    static MapPreset load(CompoundTag tag) {
        String name = tag.getString("Name");
        GameMode mode = GameMode.valueOf(tag.getString("Mode"));
        List<PointLayout> points = new ArrayList<>();
        ListTag pointList = tag.getList("Points", Tag.TAG_COMPOUND);
        for (int i = 0; i < pointList.size(); i++) {
            points.add(PointLayout.load(pointList.getCompound(i)));
        }
        ResourceKey<Level> spawnADim = null;
        BlockPos spawnAPos = null;
        if (tag.contains("SpawnADim")) {
            spawnADim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("SpawnADim")));
            spawnAPos = NbtUtils.readBlockPos(tag.getCompound("SpawnAPos"));
        }
        ResourceKey<Level> spawnBDim = null;
        BlockPos spawnBPos = null;
        if (tag.contains("SpawnBDim")) {
            spawnBDim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("SpawnBDim")));
            spawnBPos = NbtUtils.readBlockPos(tag.getCompound("SpawnBPos"));
        }
        return new MapPreset(name, mode, points, spawnADim, spawnAPos, spawnBDim, spawnBPos);
    }
}
