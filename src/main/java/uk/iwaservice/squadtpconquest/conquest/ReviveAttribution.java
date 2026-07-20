package uk.iwaservice.squadtpconquest.conquest;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks which player most recently right-clicked a downed teammate, so a
 * completed revive can be credited to its reviver for scoring.
 *
 * squadtp does not expose the reviver's identity when a revive completes (no
 * event is fired, and the completion method is private), so this
 * independently observes the same public signal squadtp itself relies on:
 * right-click interaction with a player for whom {@code ReviveSystem.isDowned()}
 * is true. ConquestManager polls {@code ReviveSystem.isDowned()} once per
 * second and, on a downed-to-alive transition, looks up the last recorded
 * reviver here.
 */
public final class ReviveAttribution {
    private static final Map<UUID, UUID> LAST_REVIVER = new HashMap<>();

    public static void note(UUID target, UUID reviver) {
        LAST_REVIVER.put(target, reviver);
    }

    @Nullable
    public static UUID take(UUID target) {
        return LAST_REVIVER.remove(target);
    }

    private ReviveAttribution() {}
}
