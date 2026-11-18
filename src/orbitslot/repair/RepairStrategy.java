package orbitslot.repair;

import orbitslot.model.Schedule;

/**
 * How to fix a schedule after a disruption (Strategy pattern).
 * The affected passes have already been removed from {@code current}.
 * A strategy returns the repaired schedule and may only change passes that
 * start at or after {@link RepairContext#now()}.
 */
public interface RepairStrategy {

    /** Short name used on the command line and in output, e.g. "rolling". */
    String name();

    /**
     * @param current the schedule after the disruption's losses were removed
     *                (may be changed or returned as is)
     * @param context time of the disruption, passes still available, constraints
     * @return the schedule to continue with
     */
    Schedule repair(Schedule current, RepairContext context);
}
