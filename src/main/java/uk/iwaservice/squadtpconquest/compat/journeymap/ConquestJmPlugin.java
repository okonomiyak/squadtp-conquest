package uk.iwaservice.squadtpconquest.compat.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;
import uk.iwaservice.squadtpconquest.SquadTpConquest;

import javax.annotation.Nullable;

/**
 * Discovered and instantiated by JourneyMap itself (via the {@link JourneyMapPlugin}
 * annotation), so this class never loads when JourneyMap is absent.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class ConquestJmPlugin implements IClientPlugin {

    @Nullable
    private static IClientAPI api;

    @Override
    public void initialize(IClientAPI jmClientApi) {
        api = jmClientApi;
        SquadTpConquest.LOGGER.info("JourneyMap integration initialized");
        ClientEventRegistry.ENTITY_RADAR_UPDATE_EVENT.subscribe(SquadTpConquest.MODID, ConquestJmRadarEvents::onEntityRadarUpdate);
        ConquestJmWaypointHandler.refresh();
    }

    @Override
    public String getModId() {
        return SquadTpConquest.MODID;
    }

    @Nullable
    static IClientAPI api() {
        return api;
    }
}
