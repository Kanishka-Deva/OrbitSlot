package orbitslot.model;

import java.time.Instant;

/**
 * Something that goes wrong after the plan was made. It becomes known at
 * {@link #announcedAt()}; from then on the schedule has to cope with it.
 */
public sealed interface Disruption permits PassCancellation, AntennaOutage {

    Instant announcedAt();
}
