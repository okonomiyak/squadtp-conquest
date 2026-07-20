package uk.iwaservice.squadtpconquest.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import uk.iwaservice.squadtpconquest.conquest.ConquestManager;
import uk.iwaservice.squadtpconquest.conquest.Team;
import uk.iwaservice.squadtpconquest.network.NetworkHandler;

/**
 * The capture point flag: a fixed 1x3 base-plate/pole/banner structure
 * (user-authored geometry from flag.json) whose banner color reflects the
 * current owner/capturing team; the stone base and pole don't change color.
 * SEGMENT: 0 = base plate + pole, 1 = pure pole passthrough, 2 = pole + banner.
 * Placed and recolored exclusively by
 * {@link uk.iwaservice.squadtpconquest.conquest.FlagPole}, so it never needs
 * to be player-placeable; right-clicking any segment opens the conquest GUI.
 */
public class ConquestFlagBlock extends Block {
    public static final IntegerProperty SEGMENT = IntegerProperty.create("segment", 0, 2);
    public static final EnumProperty<Team> TEAM = EnumProperty.create("team", Team.class);

    private static final VoxelShape BASE_SHAPE =
            Shapes.or(Block.box(0, 0, 4, 5, 1, 10), Block.box(1, 1, 5, 4, 16, 9));
    private static final VoxelShape MID_SHAPE = Block.box(1, 0, 5, 4, 16, 9);
    private static final VoxelShape TOP_SHAPE =
            Shapes.or(Block.box(1, 0, 5, 4, 16, 9), Block.box(4, 4, 6, 16, 15, 8));

    public ConquestFlagBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SEGMENT, 0).setValue(TEAM, Team.NEUTRAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SEGMENT, TEAM);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(SEGMENT)) {
            case 0 -> BASE_SHAPE;
            case 1 -> MID_SHAPE;
            default -> TOP_SHAPE;
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        MinecraftServer server = serverPlayer.server;
        ConquestManager manager = ConquestManager.get(server);
        if (!manager.hasPoints()) {
            return InteractionResult.PASS;
        }
        NetworkHandler.send(serverPlayer, manager.buildSyncPacketForOpen(server, serverPlayer));
        return InteractionResult.CONSUME;
    }
}
