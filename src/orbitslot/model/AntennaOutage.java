package orbitslot.model;

import java.time.Instant;

/**
 * An antenna is unusable from {@code from} to {@code to} (fault, rain fade).
 * It is noticed when it starts, so it is announced at {@code from}.
 */
public record AntennaOutage(Antenna antenna, Instant from, Instant to) implements Disruption {

    public AntennaOutage {
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("Outage must end after it starts");
        }
    }

    @Override
    public Instant announcedAt() {
        return from;
    }

    /** True if the pass, tracked on this antenna, would overlap the outage. */
    public boolean blocks(Assignment assignment) {
        Pass p = assignment.pass();
        return assignment.antenna().equals(antenna) && p.aos().isBefore(to) && from.isBefore(p.los());
    }
}
