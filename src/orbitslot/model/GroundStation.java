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
        List<Antenna> antennas) {

    public GroundStation {
        if (antennas.isEmpty()) {
            throw new IllegalArgumentException("A station needs at least one antenna");
        }
        antennas = List.copyOf(antennas);
    }

    @Override
    public String toString() {
        return String.format("%s (#%s) at %.3f, %.3f, %.0f m, %d antenna(s), min elevation %.0f deg",
                name, id, latitudeDeg, longitudeDeg, altitudeM, antennas.size(), minElevationDeg);
    }
}
