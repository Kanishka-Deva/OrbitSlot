package orbitslot.scheduler;

import orbitslot.constraint.Constraint;
import orbitslot.model.Antenna;
import orbitslot.model.Assignment;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Shared skeleton of FCFS and Greedy (Template Method pattern):
 * sort the passes, then give each pass to the first antenna where every
 * constraint allows it, or drop it with the reasons. Subclasses only decide
 * the order.
 */
public abstract class OrderedAssignmentScheduler implements Scheduler {

    private final List<Constraint> constraints;

    protected OrderedAssignmentScheduler(List<Constraint> constraints) {
        this.constraints = List.copyOf(constraints);
    }

    /** The order in which passes are considered. */
    protected abstract Comparator<Pass> order();

    @Override
    public final Schedule schedule(List<Pass> passes, GroundStation station) {
        Schedule schedule = new Schedule(station);
        List<Pass> sorted = new ArrayList<>(passes);
        sorted.sort(order());

        for (Pass pass : sorted) {
            Set<String> reasons = new LinkedHashSet<>();
            boolean placed = false;
            for (Antenna antenna : station.antennas()) {
                Assignment candidate = new Assignment(pass, antenna);
                Optional<String> violation = firstViolation(candidate, schedule);
                if (violation.isEmpty()) {
                    schedule.add(candidate);
                    placed = true;
                    break;
                }
                reasons.add(violation.get());
            }
            if (!placed) {
                schedule.drop(pass, String.join("; ", reasons));
            }
        }
        return schedule;
    }

    private Optional<String> firstViolation(Assignment candidate, Schedule schedule) {
        for (Constraint c : constraints) {
            Optional<String> v = c.violation(candidate, schedule);
            if (v.isPresent()) {
                return v;
            }
        }
        return Optional.empty();
    }
}
