package orbitslot.model;

/**
 * A satellite that can be scheduled for contact with a ground station.
 * Subclasses represent the satellite types we model; the numbers for each
 * type (downlink rate, buffer, priority range) come from assumptions.properties.
 */
public abstract class Satellite {

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
