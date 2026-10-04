package uk.iwaservice.squadtpconquest.mixin;

import net.minecraft.world.entity.player.Player;
import uk.iwaservice.squadtpconquest.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "canHarmPlayer", at = @At("HEAD"), cancellable = true)
    private void allowSelfHarm(Player other, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        // Config is SERVER-type: only readable server-side once loaded.
        if (!self.level().isClientSide() && self.getUUID().equals(other.getUUID())
                && Config.SELF_DAMAGE_ENABLED.get()) {
            cir.setReturnValue(true);
        }
    }
}
