package uk.iwaservice.squadtpconquest.client;

import net.minecraft.client.Minecraft;
import uk.iwaservice.squadtpconquest.client.gui.ConquestScreen;
import uk.iwaservice.squadtpconquest.compat.JourneyMapCompat;
import uk.iwaservice.squadtpconquest.network.ConquestScoreboardPacket;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;
import uk.iwaservice.squadtpconquest.network.PinPacket;
import uk.iwaservice.squadtpconquest.network.SpotPacket;

/** Client-only entry point for the S2C packets. Never classloaded on a dedicated server. */
public final class ClientPacketHandler {

    public static void handleSync(ConquestSyncPacket msg) {
        ConquestClientData.apply(msg.points(), msg.ticketsA(), msg.ticketsB(), msg.active(), msg.state(),
                msg.mode(), msg.yourTeam(), msg.canAdmin(),
                msg.attackerTeam(), msg.sectorIndex(), msg.sectorCount(),
                msg.attackerTickets(), msg.attackerTicketsMax(), msg.tdmKillLimit(),
                msg.callIns(), msg.availableScore(), msg.joinableSquads());
        JourneyMapCompat.refresh();
        if (msg.openScreen()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new ConquestScreen());
            }
        }
    }

    public static void handleScoreboard(ConquestScoreboardPacket msg) {
        ConquestClientData.applyScoreboard(msg.roundElapsedSeconds(), msg.entries());
    }

    public static void handleSpot(SpotPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0;
        ConquestClientData.addSpot(msg.target(), msg.targetName(), msg.dimension(), msg.pos(), now + msg.durationTicks());
        JourneyMapCompat.refresh();
    }

    public static void handlePin(PinPacket msg) {
        if (msg.cleared()) {
            ConquestClientData.removePin(msg.placer());
        } else {
            Minecraft mc = Minecraft.getInstance();
            long now = mc.level != null ? mc.level.getGameTime() : 0;
            ConquestClientData.addPin(msg.placer(), msg.placerName(), msg.dimension(), msg.pos(), now + msg.durationTicks());
        }
        JourneyMapCompat.refresh();
    }

    private ClientPacketHandler() {}
}
