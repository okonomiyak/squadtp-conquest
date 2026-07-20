package uk.iwaservice.squadtpconquest.conquest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import uk.iwaservice.squadtpconquest.ModRegistry;
import uk.iwaservice.squadtpconquest.block.ConquestFlagBlock;

/**
 * The capture point's 1x3 flag structure (base plate + pole + banner, from
 * flag.json). Built once when the point is set; afterward only its TEAM
 * color is updated to reflect ownership/progress.
 */
public final class FlagPole {

    /** Extra height above the point's ground position the bottom (base) segment floats at. */
    private static final int HEIGHT_OFFSET = 1;
    /** Blocks tall the flag structure is (0=base, 1=mid pole, 2=top+banner). */
    private static final int SEGMENTS = 3;

    /** Places the fixed stack of flag blocks above the point's position. */
    public static void build(ServerLevel level, CapturePoint point) {
        BlockPos base = flagBase(point);
        for (int i = 0; i < SEGMENTS; i++) {
            level.setBlockAndUpdate(base.above(i), ModRegistry.CONQUEST_FLAG.get().defaultBlockState()
                    .setValue(ConquestFlagBlock.SEGMENT, i));
        }
        update(level, point);
    }

    /** Recolors every segment to match the current owner/capturing team. */
    public static void update(ServerLevel level, CapturePoint point) {
        Team color = Team.resolveActive(point.getOwner(), point.getCapturingTeam(), point.getFlagLevel());
        BlockPos base = flagBase(point);
        for (int i = 0; i < SEGMENTS; i++) {
            setTeam(level, base.above(i), i, color);
        }
    }

    /** Clears the flag blocks, e.g. when the point is removed or relocated. */
    public static void remove(ServerLevel level, CapturePoint point) {
        BlockPos base = flagBase(point);
        for (int i = 0; i < SEGMENTS; i++) {
            BlockPos pos = base.above(i);
            if (level.getBlockState(pos).is(ModRegistry.CONQUEST_FLAG.get())) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    private static BlockPos flagBase(CapturePoint point) {
        return point.getPos().above(HEIGHT_OFFSET);
    }

    private static void setTeam(ServerLevel level, BlockPos pos, int segment, Team color) {
        if (level.getBlockState(pos).is(ModRegistry.CONQUEST_FLAG.get())) {
            level.setBlockAndUpdate(pos, ModRegistry.CONQUEST_FLAG.get().defaultBlockState()
                    .setValue(ConquestFlagBlock.SEGMENT, segment).setValue(ConquestFlagBlock.TEAM, color));
        }
    }

    private FlagPole() {}
}
