package orbitslot.repair;

import orbitslot.model.Schedule;

/** Baseline: accept the losses and change nothing. */
public final class NoRepair implements RepairStrategy {

    @Override
    public String name() {
        return "none";
    }

    @Override
    public Schedule repair(Schedule current, RepairContext context) {
        return current;
    }
}
