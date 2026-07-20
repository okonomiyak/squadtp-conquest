package uk.iwaservice.squadtpconquest.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import uk.iwaservice.squadtpconquest.SquadTpConquest;
import uk.iwaservice.squadtpconquest.client.gui.ConquestCaptureOverlay;
import uk.iwaservice.squadtpconquest.client.gui.ConquestHudOverlay;

/** Mod-bus client events: keybind and HUD overlay registration. */
@Mod.EventBusSubscriber(modid = SquadTpConquest.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {

    public static final KeyMapping OPEN_CONQUEST_SCREEN = new KeyMapping(
            "key.squadtpconquest.open_conquest_screen",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L,
            "key.categories.squadtpconquest");

    /** Deliberately not Tab — vanilla's player-list overlay keeps that binding untouched. */
    public static final KeyMapping OPEN_SCORE_SCREEN = new KeyMapping(
            "key.squadtpconquest.open_score_screen",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_ALT,
            "key.categories.squadtpconquest");

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONQUEST_SCREEN);
        event.register(OPEN_SCORE_SCREEN);
    }

    @SubscribeEvent
    public static void onRegisterGuiOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("conquest_hud", ConquestHudOverlay.INSTANCE);
        event.registerAboveAll("conquest_capture", ConquestCaptureOverlay.INSTANCE);
    }

    private ClientModEvents() {}
}
