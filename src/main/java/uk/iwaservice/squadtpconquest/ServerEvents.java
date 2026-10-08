package uk.iwaservice.squadtpconquest;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import uk.iwaservice.squadtpconquest.command.ConquestCommand;
import uk.iwaservice.squadtpconquest.conquest.ConquestManager;
import uk.iwaservice.squadtpconquest.conquest.Team;

/** Forge-bus event handlers: command registration, respawn ticket cost and the game loop. */
public final class ServerEvents {

    private static int tickCounter;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ConquestCommand.register(event.getDispatcher());
    }

    /** Battlefield-style: every respawn (including after squadtp's downed-timeout death) costs a ticket. */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ConquestManager.get(player.server).onRespawn(player);
        }
    }

    /**
     * LOWEST priority so this runs after every other mod's respawn hook — in particular
     * classloadout's own "equip loadout on every respawn" logic, which fires unconditionally and
     * knows nothing about conquest teams. Without this, a waiting-team player who dies (e.g. to
     * fall/void damage, which isn't blocked like PvP is) gets re-armed on respawn even though
     * {@code waiting} is meant to stay unarmed/neutral.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerRespawnClearWaiting(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ConquestManager.get(player.server).teamOf(player.getUUID()) == Team.WAITING) {
            player.getInventory().clearContent();
        }
    }

    /** Squad Deathmatch: a player returning mid-round without a squad number gets one (see {@link ConquestManager#sdmAssignLateJoiner}). */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ConquestManager.get(player.server).onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        ConquestManager manager = ConquestManager.get(server);
        if (++tickCounter % 20 == 0) {
            manager.tickSecond(server);
        }
    }

    /** Forgets what a player was last sent, so a rejoin starts from fresh packets. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ConquestManager.get(player.server).forgetSent(player.getUUID());
        }
    }

    private ServerEvents() {}
}
