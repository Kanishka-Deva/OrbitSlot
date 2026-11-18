package orbitslot.model;

import java.time.Instant;

/** A pass can no longer be used (e.g. the satellite went into safe mode). */
public record PassCancellation(Pass pass, Instant announcedAt) implements Disruption {
}
