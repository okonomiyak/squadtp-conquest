package uk.iwaservice.squadtpconquest.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;
import uk.iwaservice.squadtpconquest.SquadTpConquest;
import uk.iwaservice.squadtpconquest.client.gui.ConquestCaptureOverlay;
import uk.iwaservice.squadtpconquest.client.gui.ConquestHudOverlay;
import uk.iwaservice.squadtpconquest.client.gui.KillFeedOverlay;

/** Mod-bus client events: keybind and HUD overlay registration. */
@EventBusSubscriber(modid = SquadTpConquest.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
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

    /** BF-style spot: marks whatever enemy is under the crosshair for the player's team. */
    public static final KeyMapping SPOT = new KeyMapping(
            "key.squadtpconquest.spot",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            "key.categories.squadtpconquest");

    /** Pings whatever block is under the crosshair for the player's team; sneak+press clears it early. */
    public static final KeyMapping PIN = new KeyMapping(
            "key.squadtpconquest.pin",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.squadtpconquest");

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONQUEST_SCREEN);
        event.register(OPEN_SCORE_SCREEN);
        event.register(SPOT);
        event.register(PIN);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(SquadTpConquest.MODID, "conquest_hud"),
                ConquestHudOverlay.INSTANCE);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(SquadTpConquest.MODID, "conquest_capture"),
                ConquestCaptureOverlay.INSTANCE);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(SquadTpConquest.MODID, "conquest_kill_feed"),
                KillFeedOverlay.INSTANCE);
    }

    private ClientModEvents() {}
}
