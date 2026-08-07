package uk.iwaservice.squadtpconquest;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import uk.iwaservice.squadtpconquest.conquest.ConquestManager;
import uk.iwaservice.squadtpconquest.conquest.RoundState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns explosions (TNT, creepers, any other mod's) that happen while a conquest round is
 * STARTING (the "Get Ready!" countdown) or IN_PROGRESS into a simple crater instead of vanilla's
 * clean block removal: blocks near the blast center become air, an outer ring becomes rubble,
 * and both indestructible block types (see {@link ConquestManager#isIndestructible}, config
 * default plus {@code /conquest protectblock} additions) and protect zones (by area,
 * {@code /conquest protectzone}) are left untouched entirely. Outside those two round states, or
 * with terrainDestructionEnabled off, explosions behave exactly like vanilla — and, critically,
 * are NOT tracked for restoration, since {@link ConquestManager#recordDestroyedBlock} is only
 * ever called from here (WAITING/ENDED-state explosions are permanent, by design).
 */
public final class TerrainDestructionEvents {

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!Config.TERRAIN_DESTRUCTION_ENABLED.get() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        ConquestManager manager = ConquestManager.get(server);
        RoundState state = manager.getState();
        if (state != RoundState.STARTING && state != RoundState.IN_PROGRESS) {
            return;
        }

        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) {
            return;
        }

        Vec3 center = event.getExplosion().getPosition();
        ResourceKey<Level> dim = level.dimension();
        List<BlockPos> byDistance = new ArrayList<>(affected);
        byDistance.sort(Comparator.comparingDouble(pos -> distanceSq(pos, center)));

        // Closest-first cap: any remainder past it is left in `affected` for vanilla's own
        // (unmodified) explosion handling, so a huge explosion degrades gracefully rather than
        // stalling the server tick.
        int cap = Math.min(byDistance.size(), Config.MAX_BLOCKS_PER_EXPLOSION.get());
        List<BlockPos> toHandle = byDistance.subList(0, cap);
        double maxDistance = Math.sqrt(distanceSq(toHandle.get(cap - 1), center));
        double ringRatio = Config.CRATER_RUBBLE_RING_RATIO.get();
        BlockState rubbleState = resolveBlock(Config.CRATER_RUBBLE_BLOCK.get()).defaultBlockState();

        Set<BlockPos> handled = new HashSet<>();
        for (BlockPos pos : toHandle) {
            BlockState current = level.getBlockState(pos);
            if (current.isAir()) {
                continue;
            }
            if (manager.isIndestructible(current) || manager.isProtected(dim, pos)) {
                handled.add(pos);
                continue;
            }
            manager.recordDestroyedBlock(dim, pos, current);
            double normalizedDistance = maxDistance <= 0 ? 0 : Math.sqrt(distanceSq(pos, center)) / maxDistance;
            BlockState replacement = normalizedDistance >= (1.0 - ringRatio) ? rubbleState : Blocks.AIR.defaultBlockState();
            level.setBlock(pos, replacement, 3);
            handled.add(pos);
        }
        // Every position we handled ourselves (destroyed, or left alone as protected) is removed
        // from the event's list so vanilla neither destroys it nor drops items for it.
        affected.removeIf(handled::contains);
    }

    private static double distanceSq(BlockPos pos, Vec3 center) {
        double dx = pos.getX() + 0.5 - center.x;
        double dy = pos.getY() + 0.5 - center.y;
        double dz = pos.getZ() + 0.5 - center.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Falls back to air (i.e. no rubble ring, crater is all-air) if craterRubbleBlock is misconfigured. */
    private static Block resolveBlock(String id) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        return block != null ? block : Blocks.AIR;
    }

    private TerrainDestructionEvents() {}
}
