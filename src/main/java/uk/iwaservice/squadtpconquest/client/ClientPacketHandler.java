package uk.iwaservice.squadtpconquest.client;

import net.minecraft.client.Minecraft;
import uk.iwaservice.squadtpconquest.client.gui.ConquestScreen;
import uk.iwaservice.squadtpconquest.network.ConquestScoreboardPacket;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;

/** Client-only entry point for the S2C packets. Never classloaded on a dedicated server. */
public final class ClientPacketHandler {

    public static void handleSync(ConquestSyncPacket msg) {
        ConquestClientData.apply(msg.points(), msg.ticketsA(), msg.ticketsB(), msg.active(), msg.state(),
                msg.mode(), msg.yourTeam(), msg.canAdmin());
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

    private ClientPacketHandler() {}
}
