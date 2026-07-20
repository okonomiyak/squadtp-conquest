package uk.iwaservice.squadtpconquest;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import uk.iwaservice.squadtp.squad.ReviveSystem;
import uk.iwaservice.squadtpconquest.conquest.ConquestManager;
import uk.iwaservice.squadtpconquest.conquest.DamageLog;
import uk.iwaservice.squadtpconquest.conquest.ReviveAttribution;
import uk.iwaservice.squadtpconquest.conquest.RoundState;
import uk.iwaservice.squadtpconquest.conquest.Team;

import java.util.List;
import java.util.UUID;

/**
 * Kill/death/assist scoring, hooked off vanilla Forge combat events only —
 * squadtp is not touched. Revive scoring is fed separately via
 * {@link ReviveAttribution}, written here and consumed by
 * {@link ConquestManager}'s per-second tick.
 */
public final class ScoreEvents {

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == victim) {
            return;
        }
        MinecraftServer server = victim.server;
        ConquestManager manager = ConquestManager.get(server);
        if (manager.getState() != RoundState.IN_PROGRESS) {
            return;
        }
        Team victimTeam = manager.teamOf(victim.getUUID());
        Team attackerTeam = manager.teamOf(attacker.getUUID());
        if (!victimTeam.isCombatant() || !attackerTeam.isCombatant() || victimTeam == attackerTeam) {
            return;
        }
        DamageLog.record(victim.getUUID(), attacker.getUUID(), server.getTickCount());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        MinecraftServer server = victim.server;
        ConquestManager manager = ConquestManager.get(server);
        if (manager.getState() != RoundState.IN_PROGRESS) {
            return;
        }
        Team victimTeam = manager.teamOf(victim.getUUID());
        if (!victimTeam.isCombatant()) {
            return;
        }
        manager.recordDeath(victim.getUUID());

        UUID killerUuid = null;
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            Team killerTeam = manager.teamOf(killer.getUUID());
            if (killerTeam.isCombatant() && killerTeam != victimTeam) {
                manager.recordKill(server, killer.getUUID());
                killerUuid = killer.getUUID();
            }
        }

        int windowTicks = Config.ASSIST_WINDOW_SECONDS.get() * 20;
        List<UUID> attackers = DamageLog.recentAttackers(victim.getUUID(), server.getTickCount(), windowTicks, killerUuid);
        for (UUID attacker : attackers) {
            Team attackerTeam = manager.teamOf(attacker);
            if (attackerTeam.isCombatant() && attackerTeam != victimTeam) {
                manager.recordAssist(attacker);
            }
        }
        DamageLog.clear(victim.getUUID());
    }

    /** Records the last player to hold right-click on a downed player (see ReviveAttribution). */
    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer reviver) || !(event.getTarget() instanceof ServerPlayer target)) {
            return;
        }
        if (!ReviveSystem.isDowned(target.getUUID())) {
            return;
        }
        ReviveAttribution.note(target.getUUID(), reviver.getUUID());
    }

    private ScoreEvents() {}
}
