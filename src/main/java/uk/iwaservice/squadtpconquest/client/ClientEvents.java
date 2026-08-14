package uk.iwaservice.squadtpconquest.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.iwaservice.squadtpconquest.Config;
import uk.iwaservice.squadtpconquest.SquadTpConquest;
import uk.iwaservice.squadtpconquest.client.gui.ConquestScoreScreen;
import uk.iwaservice.squadtpconquest.client.gui.ConquestScreen;
import uk.iwaservice.squadtpconquest.compat.JourneyMapCompat;

@Mod.EventBusSubscriber(modid = SquadTpConquest.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    /** Tracks whether the current scoreboard screen was opened by holding the key, for hold-to-open mode. */
    private static boolean scoreboardOpenedByHold;

    /** Clears stale capture point waypoints so they don't linger after leaving the server. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ConquestClientData.clearSpots();
        ConquestClientData.clearPins();
        JourneyMapCompat.clear();
    }

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

        while (ClientModEvents.SPOT.consumeClick()) {
            trySpot(mc);
        }
        while (ClientModEvents.PIN.consumeClick()) {
            tryPin(mc);
        }
        boolean expired = mc.level != null && ConquestClientData.pruneExpiredSpots(mc.level.getGameTime());
        expired |= mc.level != null && ConquestClientData.pruneExpiredPins(mc.level.getGameTime());
        if (expired) {
            JourneyMapCompat.refresh();
        }
    }

    /**
     * Raycasts from the crosshair for an enemy player within {@code spotRangeBlocks}, blocked by
     * line of sight, and sends {@code /conquest spot <target>} if one is found. The server
     * re-validates everything (round state, teams, alive) — this is purely "don't bother sending
     * a command that couldn't possibly do anything."
     */
    private static void trySpot(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        double range = Config.SPOT_RANGE_BLOCKS.get();
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));

        BlockHitResult blockHit = mc.level.clip(
                new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double clearDistance = blockHit.getType() == HitResult.Type.MISS
                ? range : eye.distanceTo(blockHit.getLocation());

        Team myTeam = player.getTeam();
        if (myTeam == null) {
            return;
        }
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, searchBox,
                candidate -> candidate instanceof Player target && target != player && !target.isSpectator()
                        && target.getTeam() != null && target.getTeam() != myTeam,
                clearDistance * clearDistance);
        if (hit == null) {
            return;
        }
        Entity target = hit.getEntity();
        if (eye.distanceTo(hit.getLocation()) > clearDistance) {
            return; // a wall was in the way before the entity was reached
        }
        player.connection.sendCommand("conquest spot " + target.getName().getString());
    }

    /**
     * Sneaking sends {@code /conquest pin clear} (removes the player's own active pin early).
     * Otherwise raycasts from the crosshair for a block within {@code pinRangeBlocks} and sends
     * {@code /conquest pin <pos>} if one is hit. The server re-validates everything (round state,
     * team) — same "don't bother sending a command that couldn't possibly do anything" convention
     * as {@link #trySpot}.
     */
    private static void tryPin(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (player.isShiftKeyDown()) {
            player.connection.sendCommand("conquest pin clear");
            return;
        }
        double range = Config.PIN_RANGE_BLOCKS.get();
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(range));

        BlockHitResult blockHit = mc.level.clip(
                new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() == HitResult.Type.MISS) {
            return;
        }
        var pos = blockHit.getBlockPos();
        player.connection.sendCommand("conquest pin " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
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
