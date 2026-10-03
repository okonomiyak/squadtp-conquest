package uk.iwaservice.squadtpconquest.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "canHarmPlayer", at = @At("HEAD"), cancellable = true)
    private void allowSelfHarm(Player other, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (self.getUUID().equals(other.getUUID())) {
            cir.setReturnValue(true);
        }
    }
}
