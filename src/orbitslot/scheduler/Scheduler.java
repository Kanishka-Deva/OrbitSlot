package orbitslot.scheduler;

import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;

import java.util.List;

/** A scheduling algorithm (Strategy pattern): passes in, schedule out. */
public interface Scheduler {

    /** Short name used on the command line, e.g. "greedy". */
    String name();

    Schedule schedule(List<Pass> passes, GroundStation station);
}
