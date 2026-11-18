package orbitslot.scheduler;

import orbitslot.constraint.Constraint;
import orbitslot.model.Antenna;
import orbitslot.model.Schedule.Assignment;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Shared helpers: checking constraints and placing a pass on the first antenna that allows it. */
final class Placement {

    private Placement() { }

    static Optional<String> firstViolation(Assignment candidate, Schedule schedule, List<Constraint> constraints) {
        for (Constraint c : constraints) {
            Optional<String> v = c.violation(candidate, schedule);
            if (v.isPresent()) {
                return v;
            }
        }
        return Optional.empty();
    }

    /**
     * Tries the antennas in order and adds the pass to the first one allowed.
     * @return empty if placed, otherwise the reasons it could not be placed
     */
    static Optional<String> placeOrExplain(Pass pass, Schedule schedule, List<Constraint> constraints) {
        Set<String> reasons = new LinkedHashSet<>();
        for (Antenna antenna : schedule.station().antennas()) {
            Assignment candidate = new Assignment(pass, antenna);
            Optional<String> violation = firstViolation(candidate, schedule, constraints);
            if (violation.isEmpty()) {
                schedule.add(candidate);
                return Optional.empty();
            }
            reasons.add(violation.get());
        }
        return Optional.of(String.join("; ", reasons));
    }
}
