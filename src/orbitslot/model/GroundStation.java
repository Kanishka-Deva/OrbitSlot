package orbitslot.model;

import java.util.List;

/**
 * A ground station: where it is, its antennas, and the limits that apply to
 * every antenna there.
 */
public record GroundStation(
        String id,
        String name,
        double latitudeDeg,
        double longitudeDeg,
        double altitudeM,
        double minElevationDeg,
        double slewSpeedDegPerSec,
        int setupSeconds,
        List<Antenna> antennas) {

    public GroundStation {
        if (antennas.isEmpty()) {
            throw new IllegalArgumentException("A station needs at least one antenna");
        }
        if (slewSpeedDegPerSec <= 0 || setupSeconds < 0) {
            throw new IllegalArgumentException("Slew speed must be positive and setup time not negative");
        }
        antennas = List.copyOf(antennas);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT,
                "%s (#%s) at %.3f, %.3f, %.0f m, %d antenna(s), min elevation %.0f deg, "
                        + "slew %.1f deg/s, setup %d s",
                name, id, latitudeDeg, longitudeDeg, altitudeM, antennas.size(), minElevationDeg,
                slewSpeedDegPerSec, setupSeconds);
    }
}
