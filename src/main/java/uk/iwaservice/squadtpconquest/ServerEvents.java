package uk.iwaservice.squadtpconquest;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import uk.iwaservice.squadtpconquest.command.ConquestCommand;
import uk.iwaservice.squadtpconquest.conquest.CapturePoint;
import uk.iwaservice.squadtpconquest.conquest.CaptureZoneVisualizer;
import uk.iwaservice.squadtpconquest.conquest.ConquestManager;
import uk.iwaservice.squadtpconquest.conquest.ProtectZone;
import uk.iwaservice.squadtpconquest.conquest.Team;

/** Forge-bus event handlers: command registration, respawn ticket cost and the game loop. */
public final class ServerEvents {

    private static final int ZONE_VISUAL_INTERVAL_TICKS = 10;

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
        if (tickCounter % ZONE_VISUAL_INTERVAL_TICKS == 0) {
            for (CapturePoint point : manager.getPoints()) {
                ServerLevel level = server.getLevel(point.getDimension());
                if (level != null) {
                    CaptureZoneVisualizer.render(level, point);
                }
            }
            for (Team team : new Team[]{Team.A, Team.B}) {
                ResourceKey<Level> dim = manager.getZoneDim(team);
                BlockPos min = manager.getZoneMin(team);
                BlockPos max = manager.getZoneMax(team);
                if (dim != null && min != null && max != null) {
                    ServerLevel level = server.getLevel(dim);
                    if (level != null) {
                        CaptureZoneVisualizer.renderBox(level, min, max, team);
                    }
                }
            }
            for (ProtectZone zone : manager.getProtectZones()) {
                ServerLevel level = server.getLevel(zone.getDim());
                if (level != null) {
                    CaptureZoneVisualizer.renderBox(level, zone.getMin(), zone.getMax(), Team.NEUTRAL);
                }
            }
        }
    }

    private ServerEvents() {}
}
