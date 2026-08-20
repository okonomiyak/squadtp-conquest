package uk.iwaservice.squadtpconquest.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import uk.iwaservice.squadtpconquest.client.ConquestClientData;

import java.util.List;

/**
 * Top-right kill feed: "Attacker → Victim" for each recent kill (see
 * {@link uk.iwaservice.squadtpconquest.network.KillFeedPacket}), newest on top. Entries just
 * disappear once their {@code killFeedDurationSeconds} expires — no fade animation, same as the
 * spot/pin markers this mirrors.
 */
public class KillFeedOverlay implements IGuiOverlay {

    public static final KillFeedOverlay INSTANCE = new KillFeedOverlay();

    private static final int PAD = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int TOP_Y = 4;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        List<ConquestClientData.KillFeedEntry> entries = ConquestClientData.getKillFeed();
        if (entries.isEmpty()) {
            return;
        }
        Font font = mc.font;
        for (int i = 0; i < entries.size(); i++) {
            // Oldest first in the list; render newest (last added) at the top.
            ConquestClientData.KillFeedEntry entry = entries.get(entries.size() - 1 - i);
            Component line = Component.translatable("conquest.hud.kill_feed_entry",
                    entry.attackerName(), entry.victimName());
            int y = TOP_Y + i * LINE_HEIGHT;
            graphics.drawString(font, line, width - PAD - font.width(line), y, 0xFFFFFF);
        }
    }
}
