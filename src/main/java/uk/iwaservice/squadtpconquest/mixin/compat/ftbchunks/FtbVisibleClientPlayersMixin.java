package uk.iwaservice.squadtpconquest.mixin.compat.ftbchunks;

import dev.ftb.mods.ftbchunks.client.VisibleClientPlayers;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.iwaservice.squadtpconquest.compat.RadarSuppression;

@Mixin(value = VisibleClientPlayers.class, remap = false)
public abstract class FtbVisibleClientPlayersMixin {
    @Inject(method = "isPlayerVisible", at = @At("HEAD"), cancellable = true, require = 0)
    private static void squadtpconquest$hideDuringRound(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (RadarSuppression.active()) {
            cir.setReturnValue(false);
        }
    }
}
