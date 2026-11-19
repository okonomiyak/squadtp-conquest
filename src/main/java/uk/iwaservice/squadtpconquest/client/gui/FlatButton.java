package uk.iwaservice.squadtpconquest.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** Vanilla Button logic with the flat card look (dark fill, thin outline, white when hovered) instead of the stone texture; also holds the shared style. */
final class FlatButton extends Button {
    static final int COLOR_TEXT = 0xFFFFFFFF;
    static final int COLOR_TEXT_DIM = 0xFF9AA0A8;
    static final int COLOR_CARD_BG = 0xB0101418;
    static final int COLOR_CARD_LINE = 0x50FFFFFF;

    /** Non-null for the frameless tab variant. */
    private final BooleanSupplier selected;

    FlatButton(Component label, OnPress onPress, int x, int y, int w, int h) {
        this(label, onPress, x, y, w, h, null);
    }

    private FlatButton(Component label, OnPress onPress, int x, int y, int w, int h, BooleanSupplier selected) {
        super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
        this.selected = selected;
    }

    /** Frameless text tab: underlined when {@code selected}, dim otherwise, white when hovered. */
    static FlatButton tab(Component label, OnPress onPress, int x, int y, int w, int h, BooleanSupplier selected) {
        return new FlatButton(label, onPress, x, y, w, h, selected);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        int ty = getY() + (height - 8) / 2;
        if (selected != null) {
            boolean sel = selected.getAsBoolean();
            int color = sel || isHoveredOrFocused() ? COLOR_TEXT : COLOR_TEXT_DIM;
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, ty, color);
            if (sel) {
                int tw = font.width(getMessage());
                int ux = getX() + (width - tw) / 2;
                graphics.fill(ux, ty + 9, ux + tw, ty + 10, COLOR_TEXT);
            }
            return;
        }
        boolean hover = active && isHoveredOrFocused();
        drawCardFrame(graphics, getX(), getY(), width, height, hover);
        graphics.drawCenteredString(font, getMessage(), getX() + width / 2, ty, active ? COLOR_TEXT : COLOR_TEXT_DIM);
    }

    /** Dark translucent fill with a 1px outline, white when {@code hover}. */
    static void drawCardFrame(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        g.fill(x, y, x + w, y + h, COLOR_CARD_BG);
        g.renderOutline(x, y, w, h, hover ? COLOR_TEXT : COLOR_CARD_LINE);
    }

    /** Panel chrome: translucent dark fill plus corner brackets. */
    static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xC0101418);
        drawBrackets(g, x, y, w, h);
    }

    /** Short corner brackets around the rectangle. */
    static void drawBrackets(GuiGraphics g, int x, int y, int w, int h) {
        corner(g, x, y, 1, 1);
        corner(g, x + w, y, -1, 1);
        corner(g, x, y + h, 1, -1);
        corner(g, x + w, y + h, -1, -1);
    }

    /** Short 1px L-shaped bracket at a corner; dx/dy point into the rect. */
    private static void corner(GuiGraphics g, int x, int y, int dx, int dy) {
        int len = 10;
        int x2 = dx > 0 ? x + len : x - len;
        int y2 = dy > 0 ? y + len : y - len;
        g.fill(Math.min(x, x2), dy > 0 ? y : y - 1, Math.max(x, x2), dy > 0 ? y + 1 : y, 0x80FFFFFF);
        g.fill(dx > 0 ? x : x - 1, Math.min(y, y2), dx > 0 ? x + 1 : x, Math.max(y, y2), 0x80FFFFFF);
    }
}
