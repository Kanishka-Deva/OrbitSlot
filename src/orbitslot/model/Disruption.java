package orbitslot.model;

import orbitslot.model.Schedule.Assignment;

import java.time.Instant;

/**
 * Something that goes wrong after the plan was made. It becomes known at
 * {@link #announcedAt()}; from then on the schedule has to cope with it.
 * Sealed: the two kinds below are the only ones, so a switch over them
 * needs no default branch.
 */
public sealed interface Disruption {

    Instant announcedAt();

    /** A pass can no longer be used (e.g. the satellite went into safe mode). */
    record PassCancellation(Pass pass, Instant announcedAt) implements Disruption { }

    /**
     * An antenna is unusable from {@code from} to {@code to} (fault, rain fade).
     * It is noticed when it starts, so it is announced at {@code from}.
     */
    record AntennaOutage(Antenna antenna, Instant from, Instant to) implements Disruption {

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
}
