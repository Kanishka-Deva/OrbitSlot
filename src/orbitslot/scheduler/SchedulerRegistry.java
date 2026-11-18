package orbitslot.scheduler;

import orbitslot.constraint.Constraint;
import orbitslot.io.Assumptions;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps command-line names to schedulers. Adding an algorithm means writing
 * one class and adding one register(...) line here.
 */
public final class SchedulerRegistry {

    private final Map<String, Scheduler> byName = new LinkedHashMap<>();

    public SchedulerRegistry(List<Constraint> constraints, Assumptions a) {
        register(new FcfsScheduler(constraints));
        register(new GreedyPriorityScheduler(constraints));
        register(new SimulatedAnnealingScheduler(constraints,
                a.getLong("random.seed"),
                a.getInt("sa.iterations"),
                a.getDouble("sa.startTemperatureFraction"),
                a.getDouble("sa.endTemperatureFraction")));
    }

    private void register(Scheduler scheduler) {
        byName.put(scheduler.name(), scheduler);
    }

    public Scheduler get(String name) {
        Scheduler s = byName.get(name);
        if (s == null) {
            throw new IllegalArgumentException("Unknown algorithm '" + name + "'. Available: "
                    + String.join(", ", byName.keySet()) + ", all");
        }
        return s;
    }

    public Collection<Scheduler> all() {
        return byName.values();
    }
}
