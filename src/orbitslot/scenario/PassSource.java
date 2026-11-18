package orbitslot.scenario;

import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Satellite;

import java.time.Instant;
import java.util.List;

/**
 * Anything that can produce pass windows for a station over a time window.
 * Implementations: {@link SimulatedPassSource} (seeded fake passes) and
 * {@link Predict4JavaPassSource} (real predicted passes). The schedulers only
 * ever see passes, never the source, so the two are interchangeable.
 */
public interface PassSource {

    /**
     * @return passes whose AOS lies in [start, end), sorted by AOS,
     *         with max elevation at or above the station's minimum
     */
    List<Pass> passes(List<Satellite> satellites, GroundStation station, Instant start, Instant end);

    /** Short name for console output, e.g. "simulated (seed 42)". */
    String description();
}
