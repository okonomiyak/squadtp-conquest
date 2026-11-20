package uk.iwaservice.squadtpconquest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import uk.iwaservice.squadtpconquest.SquadTpConquest;
import uk.iwaservice.squadtpconquest.conquest.GameMode;
import uk.iwaservice.squadtpconquest.network.ConquestScoreboardPacket;

import java.util.UUID;

/**
 * Green "Ally: name" label over allied placed devices (e.g. SuperbWarfare claymores/C4/mines). Works
 * through vanilla {@link OwnableEntity} (non-living only, so tamed pets are excluded), so it needs no
 * dependency on the device mod. Enemy devices get nothing, so this can't be used as recon.
 */
@Mod.EventBusSubscriber(modid = SquadTpConquest.MODID, value = Dist.CLIENT)
public final class AlliedDeviceLabels {

    // ponytail: fixed label range, make it a client config if anyone asks
    private static final double RANGE = 24.0;
    private static final int COLOR = 0x55FF55;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof OwnableEntity ownable) || e instanceof LivingEntity || ownable.getOwnerUUID() == null
                    || e.distanceToSqr(cam) > RANGE * RANGE) {
                continue;
            }
            Component label = labelFor(mc, ownable.getOwnerUUID());
            if (label == null) {
                continue;
            }
            float pt = event.getPartialTick();
            double x = Mth.lerp(pt, e.xo, e.getX()) - cam.x;
            double y = Mth.lerp(pt, e.yo, e.getY()) + e.getBbHeight() + 0.5 - cam.y;
            double z = Mth.lerp(pt, e.zo, e.getZ()) - cam.z;
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.mulPose(event.getCamera().rotation());
            poseStack.scale(-0.025F, -0.025F, 0.025F);
            Matrix4f matrix = poseStack.last().pose();
            int bg = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
            float tx = -mc.font.width(label) / 2.0F;
            mc.font.drawInBatch(label, tx, 0, 0x20000000 | COLOR, false, matrix, buffers,
                    net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, bg, 15728880);
            mc.font.drawInBatch(label, tx, 0, 0xFF000000 | COLOR, false, matrix, buffers,
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
            poseStack.popPose();
            drew = true;
        }
        if (drew) {
            buffers.endBatch();
        }
    }

    /** The label for a device owned by {@code owner}, or null if it isn't ours/an ally's (or can't be told). */
    private static Component labelFor(Minecraft mc, UUID owner) {
        UUID self = mc.player.getUUID();
        if (owner.equals(self)) {
            return Component.translatable("conquest.device.own").withStyle(s -> s.withColor(COLOR));
        }
        ConquestScoreboardPacket.Entry selfEntry = null;
        ConquestScoreboardPacket.Entry ownerEntry = null;
        for (ConquestScoreboardPacket.Entry en : ConquestClientData.getScoreboard()) {
            if (en.uuid().equals(self)) {
                selfEntry = en;
            } else if (en.uuid().equals(owner)) {
                ownerEntry = en;
            }
        }
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(owner);
        String name = ownerEntry != null ? ownerEntry.name() : info != null ? info.getProfile().getName() : "?";
        boolean ally;
        if (selfEntry != null && ownerEntry != null) {
            if (ConquestClientData.getMode() == GameMode.SQUAD_DM) {
                ally = selfEntry.sdmSquad() != 0 && selfEntry.sdmSquad() == ownerEntry.sdmSquad();
            } else {
                ally = selfEntry.team() == ownerEntry.team() && selfEntry.team().isCombatant();
            }
        } else {
            ally = info != null && mc.player.getTeam() != null
                    && mc.player.getTeam().isAlliedTo(mc.level.getScoreboard().getPlayersTeam(info.getProfile().getName()));
        }
        return ally ? Component.translatable("conquest.device.ally", name).withStyle(s -> s.withColor(COLOR)) : null;
    }

    private AlliedDeviceLabels() {}
}
