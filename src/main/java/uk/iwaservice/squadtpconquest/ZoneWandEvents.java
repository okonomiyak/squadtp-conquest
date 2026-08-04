package uk.iwaservice.squadtpconquest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import uk.iwaservice.squadtpconquest.conquest.ZoneSelection;

/**
 * Left-click half of the zone wand's two-corner selection (right-click is handled by
 * {@link uk.iwaservice.squadtpconquest.item.ZoneWandItem#useOn}, since {@code Item} has a hook
 * for that but not for left-click). Cancels the event so holding the wand never breaks blocks.
 */
public final class ZoneWandEvents {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.getMainHandItem().is(ModRegistry.ZONE_WAND.get())) {
            return;
        }
        event.setCanceled(true);
        ZoneSelection.setCorner1(player, event.getPos());
    }

    private ZoneWandEvents() {}
}
