package orbitslot.io;

import orbitslot.model.Antenna;
import orbitslot.model.GroundStation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads one station from data/stations.csv
 * (columns: id,name,latitude_deg,longitude_deg,altitude_m,source).
 * Antenna count, minimum elevation and slew speed come from the assumptions.
 */
public final class StationLoader {

    /** Loads the station named by station.id in the assumptions. */
    public GroundStation load(Path csv, Assumptions assumptions) throws IOException {
        return load(csv, assumptions, assumptions.getString("station.id"));
    }

    public GroundStation load(Path csv, Assumptions assumptions, String wantedId) throws IOException {
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);

        for (String line : lines.subList(1, lines.size())) {   // skip header
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            if (f.length < 5) {
                throw new IllegalStateException("Bad line in " + csv + ": " + line);
            }
            if (f[0].trim().equals(wantedId)) {
                return new GroundStation(
                        f[0].trim(),
                        f[1].trim(),
                        Double.parseDouble(f[2].trim()),
                        Double.parseDouble(f[3].trim()),
                        Double.parseDouble(f[4].trim()),
                        assumptions.getDouble("station.minElevationDeg"),
                        assumptions.getDouble("station.slewSpeedDegPerSec"),
                        assumptions.getInt("station.setupSeconds"),
                        antennas(assumptions.getInt("station.antennas")));
            }
        }
        throw new IllegalStateException("Station id " + wantedId + " not found in " + csv);
    }

    private static List<Antenna> antennas(int count) {
        List<Antenna> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(new Antenna(i));
        }
        return list;
    }
}
