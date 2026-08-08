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
 * A named, reusable map layout: capture point positions/radii, team spawns, home zones,
 * battlefield boundary, protect zones, in-game-added indestructible block types and the game mode
 * they belong to. Captured/loaded from the live {@link ConquestManager} state, but holds no
 * round-in-progress data (ownership, tickets, scores) — only what's needed to rebuild the setup.
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

    /** A saved axis-aligned zone box (home zone / battlefield boundary), corners as given (not pre-min/max). */
    public record ZoneBox(ResourceKey<Level> dimension, BlockPos pos1, BlockPos pos2) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dim", dimension.location().toString());
            tag.put("Pos1", NbtUtils.writeBlockPos(pos1));
            tag.put("Pos2", NbtUtils.writeBlockPos(pos2));
            return tag;
        }

        static ZoneBox load(CompoundTag tag) {
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("Dim")));
            return new ZoneBox(dim, NbtUtils.readBlockPos(tag.getCompound("Pos1")), NbtUtils.readBlockPos(tag.getCompound("Pos2")));
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
    @Nullable
    private final ZoneBox zoneA;
    @Nullable
    private final ZoneBox zoneB;
    @Nullable
    private final ZoneBox boundary;
    private final List<ProtectZone> protectZones;
    private final List<String> protectedBlocks;

    public MapPreset(String name, GameMode mode, List<PointLayout> points,
                      @Nullable ResourceKey<Level> spawnADim, @Nullable BlockPos spawnAPos,
                      @Nullable ResourceKey<Level> spawnBDim, @Nullable BlockPos spawnBPos,
                      @Nullable ZoneBox zoneA, @Nullable ZoneBox zoneB, @Nullable ZoneBox boundary,
                      List<ProtectZone> protectZones, List<String> protectedBlocks) {
        this.name = name;
        this.mode = mode;
        this.points = points;
        this.spawnADim = spawnADim;
        this.spawnAPos = spawnAPos;
        this.spawnBDim = spawnBDim;
        this.spawnBPos = spawnBPos;
        this.zoneA = zoneA;
        this.zoneB = zoneB;
        this.boundary = boundary;
        this.protectZones = protectZones;
        this.protectedBlocks = protectedBlocks;
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

    @Nullable
    public ZoneBox getZoneA() {
        return zoneA;
    }

    @Nullable
    public ZoneBox getZoneB() {
        return zoneB;
    }

    @Nullable
    public ZoneBox getBoundary() {
        return boundary;
    }

    public List<ProtectZone> getProtectZones() {
        return protectZones;
    }

    public List<String> getProtectedBlocks() {
        return protectedBlocks;
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
        if (zoneA != null) {
            tag.put("ZoneA", zoneA.save());
        }
        if (zoneB != null) {
            tag.put("ZoneB", zoneB.save());
        }
        if (boundary != null) {
            tag.put("Boundary", boundary.save());
        }
        ListTag protectZoneList = new ListTag();
        for (ProtectZone zone : protectZones) {
            protectZoneList.add(zone.save());
        }
        tag.put("ProtectZones", protectZoneList);
        ListTag protectedBlockList = new ListTag();
        for (String id : protectedBlocks) {
            protectedBlockList.add(StringTag.valueOf(id));
        }
        tag.put("ProtectedBlocks", protectedBlockList);
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
        ZoneBox zoneA = tag.contains("ZoneA") ? ZoneBox.load(tag.getCompound("ZoneA")) : null;
        ZoneBox zoneB = tag.contains("ZoneB") ? ZoneBox.load(tag.getCompound("ZoneB")) : null;
        ZoneBox boundary = tag.contains("Boundary") ? ZoneBox.load(tag.getCompound("Boundary")) : null;
        List<ProtectZone> protectZones = new ArrayList<>();
        ListTag protectZoneList = tag.getList("ProtectZones", Tag.TAG_COMPOUND);
        for (int i = 0; i < protectZoneList.size(); i++) {
            protectZones.add(ProtectZone.load(protectZoneList.getCompound(i)));
        }
        List<String> protectedBlocks = new ArrayList<>();
        ListTag protectedBlockList = tag.getList("ProtectedBlocks", Tag.TAG_STRING);
        for (int i = 0; i < protectedBlockList.size(); i++) {
            protectedBlocks.add(protectedBlockList.getString(i));
        }
        return new MapPreset(name, mode, points, spawnADim, spawnAPos, spawnBDim, spawnBPos,
                zoneA, zoneB, boundary, protectZones, protectedBlocks);
    }
}
