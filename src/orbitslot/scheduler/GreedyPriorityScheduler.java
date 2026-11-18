package orbitslot.scheduler;

import orbitslot.constraint.Constraint;
import orbitslot.model.Pass;

import java.util.Comparator;
import java.util.List;

/**
 * Greedy by priority: highest satellite priority first, ties broken by larger
 * data volume, then earliest AOS, then pass id.
 */
public final class GreedyPriorityScheduler extends OrderedAssignmentScheduler {

    public GreedyPriorityScheduler(List<Constraint> constraints) {
        super(constraints);
    }

    @Override
    public String name() {
        return "greedy";
    }

    @Override
    protected Comparator<Pass> order() {
        return Comparator.comparingInt((Pass p) -> p.satellite().priority()).reversed()
                .thenComparing(Comparator.comparingDouble(Pass::dataVolumeMB).reversed())
                .thenComparing(Pass::aos)
                .thenComparing(Pass::id);
    }
}
