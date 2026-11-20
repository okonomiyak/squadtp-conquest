package uk.iwaservice.squadtpconquest.compat.ftbchunks;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.client.FTBChunksClientAPI;
import dev.ftb.mods.ftbchunks.api.client.event.MapIconEvent;
import dev.ftb.mods.ftbchunks.api.client.icon.MapIcon;
import dev.ftb.mods.ftbchunks.api.client.icon.MapType;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import uk.iwaservice.squadtpconquest.compat.MapMarkers;

/** Pull-based FTB Chunks map icons: nothing is stored, icons are collected from MapMarkers on demand. */
public final class ConquestFtbMapIcons {

    private static final ResourceLocation TEX = new ResourceLocation("ftbchunks", "textures/waypoint_default.png");
    private static boolean registered;

    public static void refresh() {
        if (!registered) {
            MapIconEvent.MINIMAP.register(ConquestFtbMapIcons::onIcons);
            MapIconEvent.LARGE_MAP.register(ConquestFtbMapIcons::onIcons);
            registered = true;
        }
        FTBChunksClientAPI api = FTBChunksAPI.clientApi();
        if (api != null) {
            api.requestMinimapIconRefresh();
        }
    }

    /** collect() is empty once ConquestClientData is cleared, so a refresh drops the icons. */
    public static void clear() {
        refresh();
    }

    private static void onIcons(MapIconEvent event) {
        ResourceLocation dim = event.getDimension().location();
        for (MapMarkers.Marker m : MapMarkers.collect()) {
            if (m.dimension().equals(dim)) {
                event.add(new MarkerIcon(m));
            }
        }
    }

    private static final class MarkerIcon extends MapIcon.SimpleMapIcon {
        private final String name;

        MarkerIcon(MapMarkers.Marker m) {
            super(Vec3.atCenterOf(m.pos()), Icon.getIcon(TEX).withTint(Color4I.rgb(m.rgb())));
            name = m.name();
        }

        @Override
        public boolean isVisible(MapType type, double distance, boolean outside) {
            return true; // incl. in-world
        }

        @Override
        public boolean isIconOnEdge(MapType type, boolean outside) {
            return type.isMinimap() && outside;
        }

        @Override
        public void addTooltip(TooltipList list) {
            list.string(name);
        }
    }

    private ConquestFtbMapIcons() {}
}
