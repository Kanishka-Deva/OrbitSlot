package orbitslot.constraint;

import orbitslot.model.AntennaOutage;
import orbitslot.model.Assignment;
import orbitslot.model.Schedule;

import java.util.List;
import java.util.Optional;

/** No pass may be tracked on an antenna while it is down. */
public final class AntennaOutageConstraint implements Constraint {

    private final List<AntennaOutage> outages;

    public AntennaOutageConstraint(List<AntennaOutage> outages) {
        this.outages = List.copyOf(outages);
    }

    @Override
    public Optional<String> violation(Assignment candidate, Schedule current) {
        for (AntennaOutage o : outages) {
            if (o.blocks(candidate)) {
                return Optional.of(candidate.antenna() + " is down");
            }
        }
        return Optional.empty();
    }
}
