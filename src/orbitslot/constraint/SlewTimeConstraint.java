package orbitslot.constraint;

import orbitslot.model.Assignment;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;

import java.util.Optional;

/**
 * An antenna tracks one pass at a time, and needs slew + setup time
 * ({@link Turnaround}) between passes. Checked against every pass already
 * on the same antenna, whether it comes before or after the candidate.
 */
public final class SlewTimeConstraint implements Constraint {

    @Override
    public Optional<String> violation(Assignment candidate, Schedule current) {
        GroundStation station = current.station();
        Pass next = candidate.pass();
        for (Assignment existing : current.assignmentsFor(candidate.antenna())) {
            Pass other = existing.pass();
            boolean ok = other.aos().isAfter(next.aos())
                    ? Turnaround.fits(next, other, station)
                    : Turnaround.fits(other, next, station);
            if (!ok) {
                return Optional.of(candidate.antenna() + " busy with " + other.satellite().name());
            }
        }
        return Optional.empty();
    }
}
