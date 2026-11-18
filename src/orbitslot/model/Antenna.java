package orbitslot.model;

/** One antenna at a ground station. It can track one satellite at a time. */
public record Antenna(int number) {

    public String label() {
        return "ANT-" + number;
    }

    @Override
    public String toString() {
        return label();
    }
}
