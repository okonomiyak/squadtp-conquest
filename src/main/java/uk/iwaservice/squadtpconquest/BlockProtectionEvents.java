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
 * Blocks marked indestructible (config default or {@code /conquest protectblock}), inside a
 * protect zone ({@code /conquest protectzone}), or inside a spawn zone
 * ({@code /conquest spawnzone}) can't be broken by anything except creative mode; spawn zones
 * additionally block placement. Unlike {@link TerrainDestructionEvents} (explosions only, gated on
 * terrainDestructionEnabled and round state), this applies to ordinary breaking/placing at all
 * times, since it's protecting map setup (flag poles, admin-designated blocks/areas/spawns) rather
 * than shaping crater damage.
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
        if (manager.isIndestructible(event.getState()) || manager.isProtected(dim, pos)
                || manager.isInSpawnZone(dim, pos)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.isCreative()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ConquestManager manager = ConquestManager.get(level.getServer());
        if (manager.isInSpawnZone(level.dimension(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    private BlockProtectionEvents() {}
}
