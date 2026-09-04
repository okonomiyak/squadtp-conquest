package uk.iwaservice.squadtpconquest.compat;

import net.minecraft.server.level.ServerPlayer;

/**
 * Sole gateway into the classloadout integration. Mirrors this mod's own
 * {@code compat.JourneyMapCompat} (classloadout is not modified by this).
 */
public final class ClassLoadoutCompat {

    /**
     * Equips the player's saved personal loadout into their hotbar (same as classloadout's own
     * {@code /class select}/loadout station "apply now" path), granting per-slot ammo too.
     * No-op if classloadout isn't installed or the player never set a personal loadout.
     */
    public static void equip(ServerPlayer player) {
        // ponytail: classloadout has no NeoForge/1.21.1 build yet; restore the real delegation
        // (see squadtp-conquest-1.20.1's compat/classloadout/ConquestLoadoutHandler.java) once it is.
    }

    private ClassLoadoutCompat() {}
}
