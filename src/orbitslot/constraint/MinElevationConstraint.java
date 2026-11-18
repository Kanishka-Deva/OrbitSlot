package orbitslot.constraint;

import orbitslot.model.Schedule.Assignment;
import orbitslot.model.Schedule;

import java.util.Locale;
import java.util.Optional;

/** A pass must climb at least to the station's minimum elevation. */
public final class MinElevationConstraint implements Constraint {

    @Override
    public Optional<String> violation(Assignment candidate, Schedule current) {
        double min = current.station().minElevationDeg();
        double maxEl = candidate.pass().maxElevationDeg();
        if (maxEl < min) {
            return Optional.of(String.format(Locale.ROOT, "max elevation %.1f deg is below the %.0f deg mask", maxEl, min));
        }
        return Optional.empty();
    }
}
