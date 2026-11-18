package orbitslot.scheduler;

import orbitslot.model.Assignment;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;

/**
 * The single number simulated annealing maximises, also reported for every
 * algorithm: priority x data volume (MB), summed over scheduled passes.
 */
public final class Objective {

    private Objective() { }

    public static double score(Pass pass) {
        return pass.satellite().priority() * pass.dataVolumeMB();
    }

    public static double score(Schedule schedule) {
        double total = 0;
        for (Assignment a : schedule.assignments()) {
            total += score(a.pass());
        }
        return total;
    }
}
