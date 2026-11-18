package orbitslot.tools;

import com.github.amsacode.predict4java.GroundStationPosition;
import com.github.amsacode.predict4java.PassPredictor;
import com.github.amsacode.predict4java.SatPassTime;
import com.github.amsacode.predict4java.TLE;
import orbitslot.io.OmmJsonReader;
import orbitslot.io.OmmRecord;
import orbitslot.io.OmmToTleConverter;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Stage 0 sanity check: converts every OMM record to a TLE, parses it with
 * predict4java, checks the values survived the round trip, then predicts a
 * day of passes for one satellite.
 *
 * Run with: .\run.ps1 -Main orbitslot.tools.Predict4JavaCheck
 */
public final class Predict4JavaCheck {

    public static void main(String[] args) throws Exception {
        OmmJsonReader reader = new OmmJsonReader();
        OmmToTleConverter converter = new OmmToTleConverter();
        int ok = 0, skipped = 0, mismatched = 0;
        TLE example = null;

        for (String group : List.of("weather", "iridium", "cubesat")) {
            for (OmmRecord r : reader.read(Path.of("data/celestrak/" + group + ".json"))) {
                if (!converter.canConvert(r)) {
                    System.out.println("skip (6-digit id): " + r.objectName() + " " + r.noradCatId());
                    skipped++;
                    continue;
                }
                TLE tle = new TLE(converter.toTle(r));
                String problem = compare(r, tle);
                if (problem != null) {
                    System.out.println("MISMATCH " + r.objectName() + ": " + problem);
                    mismatched++;
                } else {
                    ok++;
                }
                if (r.noradCatId() == 28054) {
                    example = tle;
                }
            }
        }
        System.out.printf("Round trip: %d ok, %d mismatched, %d skipped%n", ok, mismatched, skipped);

        if (example != null) {
            // Same coordinates as data/stations.csv (SatNOGS #1492, Archenhold).
            GroundStationPosition station = new GroundStationPosition(52.487, 13.475, 23);
            PassPredictor predictor = new PassPredictor(example, station);
            Date start = Date.from(Instant.parse("2026-11-18T00:00:00Z"));
            List<SatPassTime> passes = predictor.getPasses(start, 24, false);
            System.out.println("predict4java passes for " + example.getName() + " in 24 h:");
            for (SatPassTime p : passes) {
                System.out.printf("  AOS %s  LOS %s  maxEl %5.1f  az %3d -> %3d%n",
                        p.getStartTime().toInstant(), p.getEndTime().toInstant(),
                        p.getMaxEl(), p.getAosAzimuth(), p.getLosAzimuth());
            }
        }
        if (mismatched > 0) {
            System.exit(1);
        }
    }

    /** Returns null if predict4java parsed the same values we put in. */
    private static String compare(OmmRecord r, TLE t) {
        if (t.getCatnum() != r.noradCatId()) return "catnum " + t.getCatnum();
        if (!close(t.getIncl(), r.inclination(), 1e-4)) return "incl " + t.getIncl();
        if (!close(t.getRaan(), r.raOfAscNode(), 1e-4)) return "raan " + t.getRaan();
        if (!close(t.getEccn(), r.eccentricity(), 1e-7)) return "ecc " + t.getEccn();
        if (!close(t.getArgper(), r.argOfPericenter(), 1e-4)) return "argp " + t.getArgper();
        if (!close(t.getMeanan(), r.meanAnomaly(), 1e-4)) return "ma " + t.getMeanan();
        if (!close(t.getMeanmo(), r.meanMotion(), 1e-8)) return "mm " + t.getMeanmo();
        if (!close(t.getBstar(), r.bstar(), Math.max(1e-12, Math.abs(r.bstar()) * 1e-4)))
            return "bstar " + t.getBstar() + " vs " + r.bstar();
        if (!close(t.getDrag(), r.meanMotionDot(), 1e-8))
            return "ndot " + t.getDrag() + " vs " + r.meanMotionDot();
        return null;
    }

    private static boolean close(double a, double b, double tol) {
        return Math.abs(a - b) <= tol;
    }
}
