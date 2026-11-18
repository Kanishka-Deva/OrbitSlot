package orbitslot.catalog;

import orbitslot.io.Assumptions;
import orbitslot.io.OmmJsonReader;
import orbitslot.io.OmmRecord;
import orbitslot.io.OmmToTleConverter;
import orbitslot.model.Satellite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the CelesTrak snapshot and keeps only satellites we can schedule:
 * LEO (period below the configured limit) and representable as a TLE.
 * Everything left out is kept in {@link #excluded()} with a reason, so the
 * program can report it instead of hiding it.
 */
public final class SatelliteCatalog {

    /** Snapshot file name (in data/celestrak/) mapped to its satellite type key. */
    private static final Map<String, String> FILES = new LinkedHashMap<>();
    static {
        FILES.put("weather.json", "weather");
        FILES.put("iridium.json", "comms");
        FILES.put("cubesat.json", "cubesat");
    }

    public record Excluded(String name, int noradId, String reason) { }

    private final List<Satellite> satellites = new ArrayList<>();
    private final List<Excluded> excluded = new ArrayList<>();
    /** Orbit data of each kept satellite, by NORAD id (used by real pass prediction). */
    private final Map<Integer, OmmRecord> orbits = new HashMap<>();

    private SatelliteCatalog() { }

    public static SatelliteCatalog load(Path celestrakDir, Assumptions assumptions,
                                        SatelliteFactory factory) throws IOException {
        SatelliteCatalog catalog = new SatelliteCatalog();
        OmmJsonReader reader = new OmmJsonReader();
        OmmToTleConverter converter = new OmmToTleConverter();
        double maxPeriod = assumptions.getDouble("schedule.leoMaxPeriodMinutes");

        for (Map.Entry<String, String> file : FILES.entrySet()) {
            for (OmmRecord r : reader.read(celestrakDir.resolve(file.getKey()))) {
                String reason = null;
                if (r.periodMinutes() >= maxPeriod) {
                    reason = String.format("not LEO (period %.0f min)", r.periodMinutes());
                } else if (!converter.canConvert(r)) {
                    reason = "catalog number too large for TLE format";
                } else if (catalog.orbits.containsKey(r.noradCatId())) {
                    reason = "duplicate (already loaded from another file)";
                }

                if (reason != null) {
                    catalog.excluded.add(new Excluded(r.objectName(), r.noradCatId(), reason));
                } else {
                    catalog.satellites.add(factory.create(file.getValue(), r.noradCatId(), r.objectName()));
                    catalog.orbits.put(r.noradCatId(), r);
                }
            }
        }
        return catalog;
    }

    public List<Satellite> satellites() {
        return Collections.unmodifiableList(satellites);
    }

    public List<Excluded> excluded() {
        return Collections.unmodifiableList(excluded);
    }

    public OmmRecord orbitOf(Satellite satellite) {
        return orbits.get(satellite.noradId());
    }
}
