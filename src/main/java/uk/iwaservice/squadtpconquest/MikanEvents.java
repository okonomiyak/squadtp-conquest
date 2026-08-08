package uk.iwaservice.squadtpconquest;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import uk.iwaservice.squadtpconquest.conquest.ConquestManager;

/**
 * The mikan item (registered as a plain vanilla SnowballItem, see {@link ModRegistry#MIKAN})
 * breaks the block it hits on impact, subject to the same indestructible/protected checks as
 * ordinary breaking (see {@link BlockProtectionEvents}) — always active, not gated on round state.
 */
public final class MikanEvents {

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Snowball snowball) || !snowball.getItem().is(ModRegistry.MIKAN.get())) {
            return;
        }
        if (!(event.getRayTraceResult() instanceof BlockHitResult hit)
                || !(snowball.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        ConquestManager manager = ConquestManager.get(level.getServer());
        ResourceKey<Level> dim = level.dimension();
        if (manager.isIndestructible(state) || manager.isProtected(dim, pos)) {
            return;
        }
        level.destroyBlock(pos, true);
    }

    private MikanEvents() {}
}
