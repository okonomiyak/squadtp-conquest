package uk.iwaservice.squadtpconquest;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import uk.iwaservice.squadtpconquest.client.ClientConfig;
import uk.iwaservice.squadtpconquest.network.NetworkHandler;

@Mod(SquadTpConquest.MODID)
public class SquadTpConquest {
    public static final String MODID = "squadtpconquest";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SquadTpConquest() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        ModRegistry.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(ServerEvents.class);
        MinecraftForge.EVENT_BUS.register(ScoreEvents.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::register);
    }
}
