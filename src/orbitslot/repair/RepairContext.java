package orbitslot.repair;

import orbitslot.constraint.Constraint;
import orbitslot.model.Pass;

import java.time.Instant;
import java.util.List;

/**
 * What a repair strategy knows at the moment of a disruption.
 *
 * @param now         when the disruption became known; passes starting before this are history
 * @param candidates  passes that may still be scheduled (not cancelled, AOS at or after now)
 * @param constraints all constraints, including the outages known so far
 */
public record RepairContext(Instant now, List<Pass> candidates, List<Constraint> constraints) {
}
