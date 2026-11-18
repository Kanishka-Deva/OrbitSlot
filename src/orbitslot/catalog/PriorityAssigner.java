package orbitslot.catalog;

import orbitslot.io.Assumptions;
import orbitslot.model.Satellite;

import java.util.Map;
import java.util.SplittableRandom;

/**
 * Decides each satellite's priority:
 * 1. an explicit value from data/priorities.csv, if present;
 * 2. otherwise a value inside the type's range (e.g. weather 7-10), drawn
 *    from a random generator seeded with (global seed, NORAD id).
 *
 * Seeding per satellite means a satellite always gets the same priority for
 * the same seed, no matter which other satellites are loaded.
 */
public final class PriorityAssigner {

    private final Assumptions assumptions;
    private final Map<Integer, Integer> overrides;
    private final long seed;

    public PriorityAssigner(Assumptions assumptions, Map<Integer, Integer> overrides) {
        this.assumptions = assumptions;
        this.overrides = Map.copyOf(overrides);
        this.seed = assumptions.getLong("random.seed");
    }

    /** @param typeKey "weather", "comms" or "cubesat" (prefix used in assumptions.properties) */
    public int priorityFor(String typeKey, int noradId) {
        Integer pinned = overrides.get(noradId);
        if (pinned != null) {
            return pinned;
        }
        int min = assumptions.getInt(typeKey + ".priorityMin");
        int max = assumptions.getInt(typeKey + ".priorityMax");
        if (min < Satellite.MIN_PRIORITY || max > Satellite.MAX_PRIORITY || min > max) {
            throw new IllegalStateException("Bad priority range for " + typeKey + ": " + min + "-" + max);
        }
        return new SplittableRandom(seed * 1_000_003L + noradId).nextInt(min, max + 1);
    }
}
