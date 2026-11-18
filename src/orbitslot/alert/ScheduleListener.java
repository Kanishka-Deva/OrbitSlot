package orbitslot.alert;

import orbitslot.model.Disruption;
import orbitslot.model.Pass;

import java.time.Instant;

/**
 * Observer pattern: gets told what happens to a schedule while disruptions
 * are played out. All methods have empty defaults, so a listener only
 * implements what it cares about.
 */
public interface ScheduleListener {

    /** A disruption became known. */
    default void disruptionAnnounced(Disruption disruption) { }

    /** A planned pass can no longer be tracked (before any repair). */
    default void passLost(Pass pass, String reason) { }

    /** A repair strategy changed the schedule. */
    default void repaired(String strategy, Instant at, int passesAdded, int passesRemoved) { }
}
