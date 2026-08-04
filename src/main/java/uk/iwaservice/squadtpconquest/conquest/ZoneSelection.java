package uk.iwaservice.squadtpconquest.conquest;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player, in-memory two-corner selection set by the zone wand item (left-click = corner 1,
 * right-click = corner 2), read by the zone/protectzone commands' no-coordinates overloads so an
 * admin doesn't have to type out BlockPos arguments or stand at each corner running a command.
 * Not persisted (a wand selection is a UI convenience, not conquest state) and reset the instant
 * either corner is set in a different dimension than the other.
 */
public final class ZoneSelection {
    private record Corners(ResourceKey<Level> dim, @Nullable BlockPos pos1, @Nullable BlockPos pos2) {}

    private static final Map<UUID, Corners> selections = new HashMap<>();

    public static void setCorner1(ServerPlayer player, BlockPos pos) {
        set(player, pos, true);
    }

    public static void setCorner2(ServerPlayer player, BlockPos pos) {
        set(player, pos, false);
    }

    private static void set(ServerPlayer player, BlockPos pos, boolean corner1) {
        ResourceKey<Level> dim = player.level().dimension();
        Corners existing = selections.get(player.getUUID());
        boolean sameDim = existing != null && existing.dim().equals(dim);
        BlockPos pos1 = corner1 ? pos.immutable() : (sameDim ? existing.pos1() : null);
        BlockPos pos2 = corner1 ? (sameDim ? existing.pos2() : null) : pos.immutable();
        selections.put(player.getUUID(), new Corners(dim, pos1, pos2));
        player.displayClientMessage(Component.translatable(
                corner1 ? "conquest.msg.wand_corner1_set" : "conquest.msg.wand_corner2_set",
                pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GOLD), true);
    }

    /** Both corners of the player's current selection, or null if either is unset or they've since changed dimension. */
    @Nullable
    public static BlockPos[] get(ServerPlayer player) {
        Corners corners = selections.get(player.getUUID());
        if (corners == null || corners.pos1() == null || corners.pos2() == null
                || !corners.dim().equals(player.level().dimension())) {
            return null;
        }
        return new BlockPos[]{corners.pos1(), corners.pos2()};
    }

    private ZoneSelection() {}
}
