package uk.iwaservice.squadtpconquest.mixin.compat.ftbchunks;

import dev.ftb.mods.ftbchunks.api.client.event.MapIconEvent;
import dev.ftb.mods.ftbchunks.client.FTBChunksClient;
import dev.ftb.mods.ftbchunks.client.mapicon.TrackedPlayerMapIcon;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.iwaservice.squadtpconquest.compat.RadarSuppression;

import java.util.Map;
import java.util.UUID;

@Mixin(value = FTBChunksClient.class, remap = false)
public abstract class FtbChunksClientMixin {
    @Shadow @Final private Map<UUID, TrackedPlayerMapIcon> longRangePlayerTracker;

    @Inject(method = "mapIcons", at = @At("HEAD"), require = 0)
    private void squadtpconquest$dropLongRange(MapIconEvent event, CallbackInfo ci) {
        if (RadarSuppression.active()) {
            longRangePlayerTracker.clear(); // server re-sends positions each interval; they never get drawn
        }
    }
}
