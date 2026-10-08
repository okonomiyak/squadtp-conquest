package uk.iwaservice.squadtpconquest.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import uk.iwaservice.squadtpconquest.client.ClientModEvents;
import uk.iwaservice.squadtpconquest.client.GuiBlurFix;
import uk.iwaservice.squadtpconquest.client.ConquestClientData;
import uk.iwaservice.squadtpconquest.network.ConquestSyncPacket;

import java.util.List;

/**
 * Hold-to-open radial menu for call-ins (MineMenu style). Closes when the opening key is
 * released, using the highlighted wedge if it is affordable.
 */
public class CallInRadialScreen extends Screen {

    private static final int R_INNER = 30;
    private static final int R_OUTER = 105;
    private static final int DEAD_ZONE = 22;

    private final List<ConquestSyncPacket.CallInStatus> callIns = ConquestClientData.getCallIns();
    private final int score = ConquestClientData.getAvailableScore();
    private final boolean usable = ConquestClientData.isActive()
            && ConquestClientData.getYourTeam().isCombatant() && !callIns.isEmpty();
    private int hovered = -1;
    private int savedBlur = -1;

    public CallInRadialScreen() {
        super(Component.translatable("key.squadtpconquest.callin_menu"));
    }

    @Override
    public void removed() {
        if (savedBlur >= 0) {
            GuiBlurFix.restore(savedBlur);
            savedBlur = -1;
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        if (savedBlur < 0) savedBlur = GuiBlurFix.suppress();
        GLFW.glfwSetCursorPos(this.minecraft.getWindow().getWindow(),
                this.minecraft.getWindow().getScreenWidth() / 2.0,
                this.minecraft.getWindow().getScreenHeight() / 2.0);
    }

    private boolean menuKeyDown() {
        long window = this.minecraft.getWindow().getWindow();
        InputConstants.Key key = ClientModEvents.CALLIN_MENU.getKey();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, key.getValue());
    }

    @Override
    public void tick() {
        if (!menuKeyDown()) {
            finish();
        }
    }

    private void finish() {
        int pick = hovered;
        this.onClose();
        if (pick >= 0 && this.minecraft.player != null) {
            this.minecraft.player.connection.sendCommand("conquest callin use " + callIns.get(pick).name());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            finish();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int hoverIndex(double mouseX, double mouseY) {
        double dx = mouseX - this.width / 2.0;
        double dy = mouseY - this.height / 2.0;
        if (!usable || dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
            return -1;
        }
        double step = 2 * Math.PI / callIns.size();
        double angle = Math.atan2(dx, -dy); // clockwise from straight up
        if (angle < 0) {
            angle += 2 * Math.PI;
        }
        return (int) ((angle + step / 2) / step) % callIns.size();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No renderBackground: keep the world visible.
        int cx = this.width / 2;
        int cy = this.height / 2;
        if (!usable) {
            graphics.drawCenteredString(this.font, Component.translatable("conquest.gui.callin_none"),
                    cx, cy - 4, 0xFFFFFFFF);
            return;
        }
        int n = callIns.size();
        hovered = hoverIndex(mouseX, mouseY);
        if (hovered >= 0 && score < callIns.get(hovered).scoreCost()) {
            hovered = -1;
        }
        double step = 2 * Math.PI / n;
        for (int i = 0; i < n; i++) {
            boolean affordable = score >= callIns.get(i).scoreCost();
            int color = !affordable ? 0x802A2A2A : i == hovered ? 0xC0E0A030 : 0x90101010;
            double mid = i * step;
            drawWedge(graphics, cx, cy, mid - step / 2 + 0.01, mid + step / 2 - 0.01, color);
        }
        for (int i = 0; i < n; i++) {
            ConquestSyncPacket.CallInStatus c = callIns.get(i);
            boolean affordable = score >= c.scoreCost();
            double mid = i * step;
            double r = (R_INNER + R_OUTER) / 2.0;
            int x = cx + (int) Math.round(Math.sin(mid) * r);
            int y = cy - (int) Math.round(Math.cos(mid) * r);
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(c.itemId()));
            graphics.renderItem(stack, x - 8, y - 14);
            int text = affordable ? 0xFFFFFFFF : 0xFF777777;
            graphics.drawCenteredString(this.font, c.name(), x, y + 4, text);
            graphics.drawCenteredString(this.font, Component.translatable("conquest.gui.callin_cost", c.scoreCost()),
                    x, y + 14, affordable ? 0xFFFFD060 : 0xFF777777);
            if (c.count() > 1) {
                graphics.drawString(this.font, "x" + c.count(), x + 9, y - 6, text);
            }
        }
        graphics.drawCenteredString(this.font, Component.translatable("conquest.gui.callin_cost", score),
                cx, cy - 4, 0xFFFFFFFF);
    }

    /** Annular sector as a triangle strip; ponytail: fixed ~3 degree resolution. */
    private static void drawWedge(GuiGraphics graphics, int cx, int cy, double a0, double a1, int argb) {
        Matrix4f m = graphics.pose().last().pose();
        int segs = Math.max(1, (int) Math.ceil((a1 - a0) / 0.05));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int a = argb >>> 24 & 255, r = argb >> 16 & 255, g = argb >> 8 & 255, bl = argb & 255;
        for (int s = 0; s <= segs; s++) {
            double ang = a0 + (a1 - a0) * s / segs;
            float sx = (float) Math.sin(ang), sy = (float) -Math.cos(ang);
            b.addVertex(m, cx + sx * R_INNER, cy + sy * R_INNER, 0).setColor(r, g, bl, a);
            b.addVertex(m, cx + sx * R_OUTER, cy + sy * R_OUTER, 0).setColor(r, g, bl, a);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
