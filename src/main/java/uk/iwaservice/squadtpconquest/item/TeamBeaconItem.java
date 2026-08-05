package uk.iwaservice.squadtpconquest.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import uk.iwaservice.squadtpconquest.conquest.ConquestManager;
import uk.iwaservice.squadtpconquest.conquest.RoundState;
import uk.iwaservice.squadtpconquest.conquest.Team;

/**
 * Deployable team-shared respawn point: right-click a block face to place it (adjacent to the
 * clicked face) for the placer's team. Any teammate who dies while it's active respawns there
 * instead of the usual spawn point, with no limit on how many times, until it expires after
 * teamBeaconLifetimeSeconds (see {@link ConquestManager#placeTeamBeacon}). Placing a new one
 * replaces the team's existing beacon. Consumed 1 per placement.
 */
public class TeamBeaconItem extends Item {
    public TeamBeaconItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        ServerLevel level = (ServerLevel) context.getLevel();
        ConquestManager manager = ConquestManager.get(level.getServer());
        if (manager.getState() != RoundState.IN_PROGRESS) {
            player.displayClientMessage(Component.translatable("conquest.msg.not_active"), true);
            return InteractionResult.FAIL;
        }
        Team team = manager.teamOf(player.getUUID());
        if (!team.isCombatant()) {
            player.displayClientMessage(Component.translatable("conquest.msg.unknown_team"), true);
            return InteractionResult.FAIL;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        manager.placeTeamBeacon(team, level, pos);
        player.displayClientMessage(Component.translatable("conquest.msg.beacon_placed", team.display()), true);
        context.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }
}
