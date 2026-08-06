package uk.iwaservice.squadtpconquest.compat.journeymap;

import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.event.ClientEvent;
import uk.iwaservice.squadtpconquest.SquadTpConquest;

import javax.annotation.Nullable;

/**
 * Discovered and instantiated by JourneyMap itself (via the {@link ClientPlugin}
 * annotation), so this class never loads when JourneyMap is absent.
 */
@ClientPlugin
public class ConquestJmPlugin implements IClientPlugin {

    @Nullable
    private static IClientAPI api;

    @Override
    public void initialize(IClientAPI jmClientApi) {
        api = jmClientApi;
        SquadTpConquest.LOGGER.info("JourneyMap integration initialized");
        ConquestJmWaypointHandler.refresh();
    }

    @Override
    public String getModId() {
        return SquadTpConquest.MODID;
    }

    @Override
    public void onEvent(ClientEvent event) {
    }

    @Nullable
    static IClientAPI api() {
        return api;
    }
}
