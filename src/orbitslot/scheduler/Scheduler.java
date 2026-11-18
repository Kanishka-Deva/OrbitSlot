package orbitslot.scheduler;

import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;

import java.util.List;

/** A scheduling algorithm (Strategy pattern): passes in, schedule out. */
public interface Scheduler {

    /** Short name used on the command line, e.g. "greedy". */
    String name();

    /**
     * Builds a schedule from scratch. Every input pass ends up either assigned
     * to an antenna or dropped with a reason; the result must satisfy every
     * constraint the scheduler was created with.
     */
    Schedule schedule(List<Pass> passes, GroundStation station);
}
