package uk.iwaservice.squadtpconquest.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.iwaservice.squadtpconquest.SquadTpConquest;
import uk.iwaservice.squadtpconquest.client.gui.ConquestScoreScreen;
import uk.iwaservice.squadtpconquest.client.gui.ConquestScreen;

@Mod.EventBusSubscriber(modid = SquadTpConquest.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    /** Tracks whether the current scoreboard screen was opened by holding the key, for hold-to-open mode. */
    private static boolean scoreboardOpenedByHold;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (ClientModEvents.OPEN_CONQUEST_SCREEN.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new ConquestScreen());
            }
        }

        if (ClientConfig.HOLD_TO_OPEN_SCOREBOARD.get()) {
            tickHoldToOpenScoreboard(mc);
        } else {
            scoreboardOpenedByHold = false;
            while (ClientModEvents.OPEN_SCORE_SCREEN.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(new ConquestScoreScreen());
                }
            }
        }
    }

    /** Like vanilla's Tab player list: opens while held, closes the instant the key is released. */
    private static void tickHoldToOpenScoreboard(Minecraft mc) {
        boolean down = mc.player != null && ClientModEvents.OPEN_SCORE_SCREEN.isDown();
        if (down) {
            // Guards against the key still being held after the player closed the
            // screen manually (e.g. Escape) — don't reopen until they release it.
            if (mc.screen == null && !scoreboardOpenedByHold) {
                mc.setScreen(new ConquestScoreScreen());
                scoreboardOpenedByHold = true;
            }
        } else {
            if (scoreboardOpenedByHold && mc.screen instanceof ConquestScoreScreen) {
                mc.setScreen(null);
            }
            scoreboardOpenedByHold = false;
        }
    }

    private ClientEvents() {}
}
