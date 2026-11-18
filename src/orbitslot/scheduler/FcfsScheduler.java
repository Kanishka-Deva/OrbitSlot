package orbitslot.scheduler;

import orbitslot.constraint.Constraint;
import orbitslot.model.Pass;

import java.util.Comparator;
import java.util.List;

/** Baseline: first come, first served. Earliest AOS first, ties by pass id. */
public final class FcfsScheduler extends OrderedAssignmentScheduler {

    public FcfsScheduler(List<Constraint> constraints) {
        super(constraints);
    }

    @Override
    public String name() {
        return "fcfs";
    }

    @Override
    protected Comparator<Pass> order() {
        return Comparator.comparing(Pass::aos).thenComparing(Pass::id);
    }
}
