package uk.iwaservice.squadtpconquest;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import uk.iwaservice.squadtp.api.RespawnChoiceRegistry;
import uk.iwaservice.squadtpconquest.client.ClientConfig;
import uk.iwaservice.squadtpconquest.conquest.ConquestRespawnChoiceProvider;
import uk.iwaservice.squadtpconquest.network.NetworkHandler;

@Mod(SquadTpConquest.MODID)
public class SquadTpConquest {
    public static final String MODID = "squadtpconquest";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SquadTpConquest(IEventBus modBus, ModContainer modContainer) {
        modBus.addListener(NetworkHandler::register);
        ModRegistry.register(modBus);
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        NeoForge.EVENT_BUS.register(ServerEvents.class);
        NeoForge.EVENT_BUS.register(ScoreEvents.class);
        NeoForge.EVENT_BUS.register(TerrainDestructionEvents.class);
        NeoForge.EVENT_BUS.register(ZoneWandEvents.class);
        NeoForge.EVENT_BUS.register(BlockProtectionEvents.class);
        NeoForge.EVENT_BUS.register(SpawnZoneEvents.class);
        NeoForge.EVENT_BUS.register(MikanEvents.class);
        RespawnChoiceRegistry.register(new ConquestRespawnChoiceProvider());
    }
}
