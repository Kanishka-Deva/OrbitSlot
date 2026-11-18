package orbitslot.catalog;

import orbitslot.io.Assumptions;
import orbitslot.model.Satellite.CommsSat;
import orbitslot.model.Satellite.CubeSat;
import orbitslot.model.Satellite;
import orbitslot.model.Satellite.WeatherSat;

/**
 * Factory: turns a type key plus identity into the right Satellite subclass,
 * filling in the per-type numbers from the assumptions. This is the only
 * place that knows which type key maps to which class.
 */
public final class SatelliteFactory {

    private final Assumptions assumptions;
    private final PriorityAssigner priorities;

    public SatelliteFactory(Assumptions assumptions, PriorityAssigner priorities) {
        this.assumptions = assumptions;
        this.priorities = priorities;
    }

    /** @param typeKey "weather", "comms" or "cubesat" */
    public Satellite create(String typeKey, int noradId, String name) {
        int priority = priorities.priorityFor(typeKey, noradId);
        double rate = assumptions.getDouble(typeKey + ".downlinkKbps");
        double buffer = assumptions.getDouble(typeKey + ".bufferMB");

        return switch (typeKey) {
            case "weather" -> new WeatherSat(noradId, name, priority, rate, buffer);
            case "comms" -> new CommsSat(noradId, name, priority, rate, buffer);
            case "cubesat" -> new CubeSat(noradId, name, priority, rate, buffer);
            default -> throw new IllegalArgumentException("Unknown satellite type: " + typeKey);
        };
    }
}
