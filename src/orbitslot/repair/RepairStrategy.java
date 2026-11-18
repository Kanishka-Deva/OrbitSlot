package orbitslot.repair;

import orbitslot.model.Schedule;

/**
 * How to fix a schedule after a disruption (Strategy pattern).
 * The affected passes have already been removed from {@code current}.
 * A strategy returns the repaired schedule and may only change passes that
 * start at or after {@link RepairContext#now()}.
 */
public interface RepairStrategy {

    String name();

    Schedule repair(Schedule current, RepairContext context);
}
