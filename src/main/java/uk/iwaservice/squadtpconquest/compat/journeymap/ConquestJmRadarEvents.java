package uk.iwaservice.squadtpconquest.compat.journeymap;

import journeymap.api.v2.client.entity.WrappedEntity;
import journeymap.api.v2.client.event.EntityRadarUpdateEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;

/**
 * Hides opposing-team players from JourneyMap's built-in radar — a live "who's nearby" display
 * separate from waypoints — so seeing where the enemy is stays gated behind actually spotting them
 * (see {@link ConquestJmWaypointHandler}) rather than always being visible regardless of the spot
 * mechanic. Enforced here (a plain JourneyMap client setting wouldn't be: any player could just
 * flip their own radar back on). Teammates are left alone, matching squadtp's own ally position
 * sharing on the same map. Subscribed via {@code ClientEventRegistry} (see
 * {@link ConquestJmPlugin#initialize}), not the NeoForge event bus — JourneyMap v2's own
 * {@code ClientEvent} types aren't NeoForge {@code Event}s and are dispatched separately.
 */
public final class ConquestJmRadarEvents {

    public static void onEntityRadarUpdate(EntityRadarUpdateEvent event) {
        if (event.getType() != EntityRadarUpdateEvent.EntityType.PLAYER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Team myTeam = mc.player.getTeam();
        if (myTeam == null) {
            return;
        }
        WrappedEntity wrapped = event.getWrappedEntity();
        Entity entity = wrapped.getEntityRef().get();
        if (!(entity instanceof Player target) || target == mc.player) {
            return;
        }
        Team targetTeam = target.getTeam();
        if (targetTeam != null && targetTeam != myTeam) {
            wrapped.setDisable(true);
        }
    }

    private ConquestJmRadarEvents() {}
}
