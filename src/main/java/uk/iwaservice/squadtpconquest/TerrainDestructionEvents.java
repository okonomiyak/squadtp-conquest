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
 * with terrainDestructionEnabled off, explosions behave exactly like vanilla.
 *
 * <p>Protection is checked against every affected block before {@code maxBlocksPerExplosion}
 * even applies (that cap only limits how many blocks get shaped into crater/rubble, for
 * performance) — otherwise a big enough explosion (a modded weapon with a wider radius than TNT,
 * say) could reach a protected block simply by having more affected positions than we bother
 * cratering, since anything left unhandled falls straight through to vanilla destruction.
 *
 * <p>Restoration is handled separately and unconditionally by
 * {@link ConquestManager#restoreTerrainSnapshot}, which pastes back a whole-region snapshot taken
 * at round start of the battlefield boundary, or — in breakthrough, if no global boundary is set —
 * the union of every sector's combat area (see {@link ConquestManager#resolveSnapshotRegion}). So
 * damage from any source (not just what this class craters) resets as long as one of those is set,
 * regardless of what happens here.
 *
 * <p>Every craterized block still gets {@link Block#wasExploded} called on it (same hook vanilla's
 * own explosion finalization uses), so TNT caught in the blast still primes and chain-detonates —
 * this class replaces how a block disappears, not the vanilla per-block explosion reaction.
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
        ResourceKey<Level> dim = level.dimension();

        // Protection applies to every affected position unconditionally, before the crater-shaping
        // cap below even comes into play — otherwise a single large explosion (a big TNT chain, or
        // a modded weapon with a bigger radius than maxBlocksPerExplosion covers) could reach a
        // protected block/zone simply by having more affected blocks than we bother shaping into
        // craters, since anything left unhandled here falls straight through to vanilla destruction.
        affected.removeIf(pos -> {
            BlockState current = level.getBlockState(pos);
            return !current.isAir() && (manager.isIndestructible(current) || manager.isProtected(dim, pos));
        });
        if (affected.isEmpty()) {
            return;
        }

        Vec3 center = event.getExplosion().getPosition();
        List<BlockPos> byDistance = new ArrayList<>(affected);
        byDistance.sort(Comparator.comparingDouble(pos -> distanceSq(pos, center)));

        // Closest-first cap: any remainder past it is left in `affected` for vanilla's own
        // (unmodified) explosion handling, so a huge explosion degrades gracefully rather than
        // stalling the server tick. Everything here is already known unprotected (filtered above).
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
            // Same hook vanilla's own explosion finalization calls on every destroyed block before
            // clearing it (a no-op for most blocks, but this is what makes caught-in-the-blast TNT
            // prime and chain-detonate — skipping it silently breaks TNT chain reactions).
            current.getBlock().wasExploded(level, pos, event.getExplosion());
            double normalizedDistance = maxDistance <= 0 ? 0 : Math.sqrt(distanceSq(pos, center)) / maxDistance;
            BlockState replacement = normalizedDistance >= (1.0 - ringRatio) ? rubbleState : Blocks.AIR.defaultBlockState();
            level.setBlock(pos, replacement, 3);
            handled.add(pos);
        }
        // Every position we craterized is removed from the event's list so vanilla doesn't also
        // destroy it (which would drop items and undo the crater/rubble shape).
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
