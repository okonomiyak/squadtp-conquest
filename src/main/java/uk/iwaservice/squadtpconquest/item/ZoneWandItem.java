package uk.iwaservice.squadtpconquest.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import uk.iwaservice.squadtpconquest.conquest.ZoneSelection;

/**
 * Admin selection tool, WorldEdit-wand-style: left-click a block to set corner 1 (handled by
 * {@link uk.iwaservice.squadtpconquest.ZoneWandEvents}, since {@link Item} has no left-click
 * hook of its own), right-click a block ({@link #useOn}) to set corner 2. The selection itself
 * lives in {@link ZoneSelection}, not on this item, so it's unaffected by which wand (or how
 * many) a player is holding.
 */
public class ZoneWandItem extends Item {
    public ZoneWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ZoneSelection.setCorner2(player, context.getClickedPos());
        return InteractionResult.SUCCESS;
    }
}
