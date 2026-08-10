package uk.iwaservice.squadtpconquest;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import uk.iwaservice.squadtpconquest.block.ConquestFlagBlock;
import uk.iwaservice.squadtpconquest.item.MikanItem;
import uk.iwaservice.squadtpconquest.item.TeamBeaconItem;
import uk.iwaservice.squadtpconquest.item.ZoneWandItem;

public final class ModRegistry {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SquadTpConquest.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SquadTpConquest.MODID);

    /** Indestructible like a command block: placed/recolored only by ConquestManager. */
    public static final RegistryObject<Block> CONQUEST_FLAG = BLOCKS.register("conquest_flag",
            () -> new ConquestFlagBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW).strength(-1.0F, 3600000.0F).noLootTable().noOcclusion()));

    /** Admin selection tool for zone/protectzone commands' no-coordinates overloads; obtained via /give only. */
    public static final RegistryObject<Item> ZONE_WAND = ITEMS.register("zone_wand",
            () -> new ZoneWandItem(new Item.Properties().stacksTo(1)));

    /** Deployable team-shared temporary respawn point; obtained via /give or a /conquest callin. */
    public static final RegistryObject<Item> TEAM_BEACON = ITEMS.register("team_beacon",
            () -> new TeamBeaconItem(new Item.Properties()));

    /**
     * Right-click throws and breaks the block it hits (see {@link MikanEvents}), subject to the
     * same indestructible/protected checks as ordinary breaking. Sneak + right-click eats it
     * instead, which is always lethal (see {@link MikanItem}). Obtained via /give only.
     */
    public static final RegistryObject<Item> MIKAN = ITEMS.register("mikan",
            () -> new MikanItem(new Item.Properties()));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    private ModRegistry() {}
}
