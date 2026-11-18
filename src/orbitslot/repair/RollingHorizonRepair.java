package orbitslot.repair;

import orbitslot.model.Pass;
import orbitslot.model.Schedule;
import orbitslot.scheduler.GreedyPriorityScheduler;
import orbitslot.scheduler.Objective;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Rolling-horizon local repair: after a disruption at time t, only the window
 * [t, t + horizon) is touched. Everything before t (already done) and after
 * the window (so the rest of the plan stays stable) is kept.
 *
 * Two candidates are built for the window and the better one (by Objective)
 * is returned:
 * 1. re-plan: clear the window and fill it again with Greedy;
 * 2. fill:    keep the window as it is and only add passes that now fit.
 * So repairing is never worse than just keeping what is left.
 * With a very long horizon this becomes a full re-plan from t.
 */
public final class RollingHorizonRepair implements RepairStrategy {

    private final String name;
    private final Duration horizon;

    public RollingHorizonRepair(String name, Duration horizon) {
        this.name = name;
        this.horizon = horizon;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Schedule repair(Schedule current, RepairContext context) {
        Instant until = context.now().plus(horizon);
        List<Pass> window = context.candidates().stream()
                .filter(p -> p.aos().isBefore(until))
                .toList();
        GreedyPriorityScheduler greedy = new GreedyPriorityScheduler(context.constraints());

        Schedule replanned = current.copy();
        window.forEach(replanned::remove);
        greedy.extend(replanned, window);

        Schedule filled = current.copy();
        greedy.extend(filled, window);

        return Objective.score(replanned) > Objective.score(filled) ? replanned : filled;
    }
}
