package uk.iwaservice.squadtpconquest.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * A snowball that also looks edible: sneak + right-click eats it instead of throwing (a normal
 * right-click still throws and breaks the block it hits, see {@link uk.iwaservice.squadtpconquest.MikanEvents}).
 * Eating it is always lethal — it bypasses squadtp's downed-conversion the same way other
 * scripted executions in this mod do (zone/boundary kills), so it's a real death, not a down.
 */
public class MikanItem extends SnowballItem {
    private static final int EAT_DURATION_TICKS = 32;

    public MikanItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player) {
            player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return EAT_DURATION_TICKS;
    }
}
