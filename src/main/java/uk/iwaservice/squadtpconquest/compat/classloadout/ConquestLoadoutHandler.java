package uk.iwaservice.squadtpconquest.compat.classloadout;

import net.minecraft.server.level.ServerPlayer;
import uk.iwaservice.classloadout.ServerEvents;

/**
 * References classloadout classes directly; only ever classloaded behind
 * {@link uk.iwaservice.squadtpconquest.compat.ClassLoadoutCompat#equip}'s {@code isLoaded} check.
 */
public final class ConquestLoadoutHandler {

    public static void equip(ServerPlayer player) {
        ServerEvents.equipLoadout(player);
    }

    private ConquestLoadoutHandler() {}
}
