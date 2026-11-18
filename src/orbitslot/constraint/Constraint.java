package orbitslot.constraint;

import orbitslot.model.Schedule.Assignment;
import orbitslot.model.Schedule;

import java.util.Optional;

/**
 * A rule a candidate assignment must satisfy given the schedule so far.
 * Schedulers take a list of constraints, so rules can be switched on or off
 * without touching any algorithm.
 */
public interface Constraint {

    /** @return empty if the candidate is allowed, otherwise the reason it is not */
    Optional<String> violation(Assignment candidate, Schedule current);
}
