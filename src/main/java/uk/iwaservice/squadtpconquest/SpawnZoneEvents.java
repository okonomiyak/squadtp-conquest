package uk.iwaservice.squadtpconquest;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import uk.iwaservice.squadtpconquest.conquest.ConquestManager;
import uk.iwaservice.squadtpconquest.conquest.RoundState;
import uk.iwaservice.squadtpconquest.conquest.Team;

/**
 * Cancels player-on-player damage in three cases: anyone currently standing inside a spawn zone
 * ({@code /conquest spawnzone}, so a spawn area can't be shot/meleed into from outside or camped),
 * everyone once a round has ended ({@link RoundState#ENDED}, so the crowd gathered at the result
 * screen — often armed, often standing right next to the enemy team at the gather point — can't
 * keep fighting until the next round starts), and anyone on {@link Team#WAITING} on either side of
 * the hit (that team stays in normal survival, unlike SPECTATOR, so without this it could both
 * deal and take damage). Block break/place protection for spawn zones is handled separately by
 * {@link BlockProtectionEvents}. Environmental damage (fall, lava, drowning, etc.) is unaffected in
 * all three cases — only damage whose source is another player is blocked.
 */
public final class SpawnZoneEvents {

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        ConquestManager manager = ConquestManager.get(victim.server);
        if (manager.getState() == RoundState.ENDED
                || manager.isInSpawnZone(victim.level().dimension(), victim.blockPosition())
                || manager.teamOf(victim.getUUID()) == Team.WAITING
                || manager.teamOf(attacker.getUUID()) == Team.WAITING) {
            event.setCanceled(true);
        }
    }

    private SpawnZoneEvents() {}
}
