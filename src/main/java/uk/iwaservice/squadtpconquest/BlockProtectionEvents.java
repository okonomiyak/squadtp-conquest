package uk.iwaservice.squadtpconquest;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import uk.iwaservice.squadtpconquest.conquest.ConquestManager;

/**
 * Blocks marked indestructible (config default or {@code /conquest protectblock}) or inside a
 * protect zone ({@code /conquest protectzone}) can't be broken by anything except creative mode.
 * Unlike {@link TerrainDestructionEvents} (explosions only, gated on terrainDestructionEnabled
 * and round state), this applies to ordinary breaking at all times, since it's protecting map
 * setup (flag poles, admin-designated blocks/areas) rather than shaping crater damage.
 */
public final class BlockProtectionEvents {

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player.isCreative() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ConquestManager manager = ConquestManager.get(level.getServer());
        ResourceKey<Level> dim = level.dimension();
        BlockPos pos = event.getPos();
        if (manager.isIndestructible(event.getState()) || manager.isProtected(dim, pos)) {
            event.setCanceled(true);
        }
    }

    private BlockProtectionEvents() {}
}
