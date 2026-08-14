package uk.iwaservice.squadtpconquest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import uk.iwaservice.squadtpconquest.conquest.ConquestManager;

/**
 * Cancels player-on-player damage to anyone currently standing inside a spawn zone
 * ({@code /conquest spawnzone}), so a spawn area can't be shot/meleed into from outside or camped.
 * Block break/place protection for the same zones is handled separately by
 * {@link BlockProtectionEvents}. Environmental damage (fall, lava, drowning, etc.) is unaffected —
 * only damage whose source is another player is blocked.
 */
public final class SpawnZoneEvents {

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer)) {
            return;
        }
        ConquestManager manager = ConquestManager.get(victim.server);
        if (manager.isInSpawnZone(victim.level().dimension(), victim.blockPosition())) {
            event.setCanceled(true);
        }
    }

    private SpawnZoneEvents() {}
}
