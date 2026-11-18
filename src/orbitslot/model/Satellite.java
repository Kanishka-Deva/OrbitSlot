package orbitslot.model;

/**
 * A satellite that can be scheduled for contact with a ground station.
 * The nested subclasses are the satellite types we model (sealed: there are
 * no others); the numbers for each type (downlink rate, buffer, priority
 * range) come from assumptions.properties.
 */
public abstract sealed class Satellite {

    /** Earth-observation / weather satellite: high data rate, usually high priority. */
    public static final class WeatherSat extends Satellite {
        public WeatherSat(int noradId, String name, int priority, double downlinkKbps, double bufferMB) {
            super(noradId, name, priority, downlinkKbps, bufferMB);
        }

        @Override
        public String typeName() {
            return "Weather";
        }
    }

    /** Communications satellite (our data: the Iridium constellation). */
    public static final class CommsSat extends Satellite {
        public CommsSat(int noradId, String name, int priority, double downlinkKbps, double bufferMB) {
            super(noradId, name, priority, downlinkKbps, bufferMB);
        }

        @Override
        public String typeName() {
            return "Comms";
        }
    }

    /** Small satellite, typically a low-rate UHF/VHF downlink and a small buffer. */
    public static final class CubeSat extends Satellite {
        public CubeSat(int noradId, String name, int priority, double downlinkKbps, double bufferMB) {
            super(noradId, name, priority, downlinkKbps, bufferMB);
        }

        @Override
        public String typeName() {
            return "CubeSat";
        }
    }

    public static final int MIN_PRIORITY = 1;
    public static final int MAX_PRIORITY = 10;

    private final int noradId;
    private final String name;
    private final int priority;
    private final double downlinkKbps;
    private final double bufferMB;

    protected Satellite(int noradId, String name, int priority, double downlinkKbps, double bufferMB) {
        if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
            throw new IllegalArgumentException("Priority must be 1-10, got " + priority + " for " + name);
        }
        if (downlinkKbps <= 0 || bufferMB <= 0) {
            throw new IllegalArgumentException("Downlink rate and buffer must be positive for " + name);
        }
        this.noradId = noradId;
        this.name = name;
        this.priority = priority;
        this.downlinkKbps = downlinkKbps;
        this.bufferMB = bufferMB;
    }

    /** Human-readable type name, e.g. "Weather". */
    public abstract String typeName();

    public int noradId() { return noradId; }
    public String name() { return name; }
    /** 1 = lowest, 10 = highest. */
    public int priority() { return priority; }
    public double downlinkKbps() { return downlinkKbps; }
    public double bufferMB() { return bufferMB; }

    @Override
    public String toString() {
        return typeName() + " " + name + " (" + noradId + ", prio " + priority + ")";
    }
}
