package uk.iwaservice.squadtpconquest.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import uk.iwaservice.squadtpconquest.SquadTpConquest;

/**
 * Sole gateway into the classloadout integration. Classes under {@code compat.classloadout}
 * reference classloadout API types and are only classloaded behind the {@code isLoaded} check
 * below, so the mod works unchanged when classloadout is not installed. Mirrors this mod's own
 * {@code compat.JourneyMapCompat} (classloadout is not modified by this).
 */
public final class ClassLoadoutCompat {

    private static Boolean loaded;
    private static boolean broken;

    private static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("classloadout");
        }
        return loaded && !broken;
    }

    /**
     * Equips the player's saved personal loadout into their hotbar (same as classloadout's own
     * {@code /class select}/loadout station "apply now" path), granting per-slot ammo too.
     * No-op if classloadout isn't installed or the player never set a personal loadout.
     */
    public static void equip(ServerPlayer player) {
        if (!isLoaded()) {
            return;
        }
        try {
            uk.iwaservice.squadtpconquest.compat.classloadout.ConquestLoadoutHandler.equip(player);
        } catch (Throwable t) {
            broken = true;
            SquadTpConquest.LOGGER.error("classloadout integration failed, disabling it for this session", t);
        }
    }

    private ClassLoadoutCompat() {}
}
