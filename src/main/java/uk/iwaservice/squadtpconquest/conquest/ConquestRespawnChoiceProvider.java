package uk.iwaservice.squadtpconquest.conquest;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import uk.iwaservice.squadtp.api.RespawnChoiceEntry;
import uk.iwaservice.squadtp.api.RespawnChoiceProvider;
import uk.iwaservice.squadtpconquest.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * Offers the team respawn beacon and (in conquest, if enabled) each owned capture point as
 * player-picked options in squadtp's respawn chooser, instead of squadtp-conquest silently
 * picking one automatically.
 */
public final class ConquestRespawnChoiceProvider implements RespawnChoiceProvider {

    private static final String BEACON_CHOICE = "beacon";
    private static final String POINT_PREFIX = "point_";

    @Override
    public String id() {
        return "squadtpconquest";
    }

    @Override
    public List<RespawnChoiceEntry> getChoices(ServerPlayer player) {
        ConquestManager manager = ConquestManager.get(player.server);
        if (manager.getState() != RoundState.IN_PROGRESS) {
            return List.of();
        }
        Team team = manager.teamOf(player.getUUID());
        if (!team.isCombatant()) {
            return List.of();
        }

        List<RespawnChoiceEntry> choices = new ArrayList<>();
        var beaconDim = manager.getTeamBeaconDim(team);
        var beaconPos = manager.getTeamBeaconPos(team);
        if (beaconDim != null && beaconPos != null) {
            choices.add(new RespawnChoiceEntry(BEACON_CHOICE,
                    Component.translatable("conquest.gui.respawn_choice_beacon"), beaconDim.location(), beaconPos));
        }
        if (manager.getMode() == GameMode.CONQUEST && Config.SPAWN_AT_OWNED_POINTS_ENABLED.get()) {
            for (CapturePoint point : manager.getPoints()) {
                if (point.getOwner() == team && manager.isPointSpawnSafe(player.server, point)) {
                    choices.add(new RespawnChoiceEntry(POINT_PREFIX + point.getName(),
                            Component.literal(point.getName()), point.getDimension().location(), point.getPos()));
                }
            }
        }
        return choices;
    }

    @Override
    public boolean onChosen(ServerPlayer player, String choiceId) {
        ConquestManager manager = ConquestManager.get(player.server);
        Team team = manager.teamOf(player.getUUID());
        if (choiceId.equals(BEACON_CHOICE)) {
            return manager.teleportToTeamBeacon(player, team);
        }
        if (choiceId.startsWith(POINT_PREFIX)) {
            String name = choiceId.substring(POINT_PREFIX.length());
            for (CapturePoint point : manager.getPoints()) {
                if (point.getName().equals(name) && point.getOwner() == team
                        && manager.isPointSpawnSafe(player.server, point)) {
                    return manager.teleportToPoint(player, point);
                }
            }
        }
        return false;
    }
}
