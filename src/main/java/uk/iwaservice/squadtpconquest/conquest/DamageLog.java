package uk.iwaservice.squadtpconquest.conquest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rolling per-victim log of recent attackers, used to attribute kill assists.
 * Pure vanilla-event bookkeeping (LivingDamageEvent), independent of squadtp.
 */
public final class DamageLog {
    private record Entry(UUID attacker, long tick) {}

    private static final Map<UUID, List<Entry>> LOG = new HashMap<>();

    public static void record(UUID victim, UUID attacker, long tick) {
        LOG.computeIfAbsent(victim, k -> new ArrayList<>()).add(new Entry(attacker, tick));
    }

    /** Distinct attackers within the window, most recent first, excluding {@code exclude} (the killer, if any). */
    public static List<UUID> recentAttackers(UUID victim, long nowTick, int windowTicks, UUID exclude) {
        List<Entry> entries = LOG.getOrDefault(victim, List.of());
        List<UUID> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            if (nowTick - e.tick() > windowTicks) {
                continue;
            }
            if (!e.attacker().equals(exclude) && seen.add(e.attacker())) {
                result.add(e.attacker());
            }
        }
        return result;
    }

    public static void clear(UUID victim) {
        LOG.remove(victim);
    }

    private DamageLog() {}
}
