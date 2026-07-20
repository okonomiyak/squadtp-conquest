package uk.iwaservice.squadtpconquest;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import uk.iwaservice.squadtpconquest.block.ConquestFlagBlock;

public final class ModRegistry {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SquadTpConquest.MODID);

    /** Indestructible like a command block: placed/recolored only by ConquestManager. */
    public static final RegistryObject<Block> CONQUEST_FLAG = BLOCKS.register("conquest_flag",
            () -> new ConquestFlagBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW).strength(-1.0F, 3600000.0F).noLootTable().noOcclusion()));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }

    private ModRegistry() {}
}
