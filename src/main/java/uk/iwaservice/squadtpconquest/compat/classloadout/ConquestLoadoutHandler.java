package uk.iwaservice.squadtpconquest.compat.classloadout;

import net.minecraft.server.level.ServerPlayer;
import uk.iwaservice.classloadout.ServerEvents;
import uk.iwaservice.classloadout.loadout.LoadoutManager;

/**
 * References classloadout classes directly; only ever classloaded behind
 * {@link uk.iwaservice.squadtpconquest.compat.ClassLoadoutCompat#equip}'s {@code isLoaded} check.
 */
public final class ConquestLoadoutHandler {

    public static void equip(ServerPlayer player) {
        ServerEvents.equipLoadout(player);
    }

    public static void awardPoints(ServerPlayer player, int amount) {
        LoadoutManager.get(player.server).addPoints(player.server, player, amount);
    }

    private ConquestLoadoutHandler() {}
}
