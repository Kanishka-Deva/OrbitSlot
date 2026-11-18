package orbitslot.scheduler;

import orbitslot.constraint.Constraint;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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
        extend(schedule, passes);
        return schedule;
    }

    /**
     * Adds the candidate passes to an existing schedule, keeping what is
     * already there. Used for a fresh schedule and by rolling-horizon repair.
     */
    public final void extend(Schedule schedule, List<Pass> candidates) {
        List<Pass> sorted = new ArrayList<>(candidates);
        sorted.sort(order());
        for (Pass pass : sorted) {
            if (schedule.assignmentOf(pass).isPresent()) {
                continue;
            }
            Optional<String> reason = Placement.placeOrExplain(pass, schedule, constraints);
            reason.ifPresent(r -> schedule.drop(pass, r));
        }
    }
}
