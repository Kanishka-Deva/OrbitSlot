package orbitslot.scenario;

import com.github.amsacode.predict4java.GroundStationPosition;
import com.github.amsacode.predict4java.PassPredictor;
import com.github.amsacode.predict4java.SatNotFoundException;
import com.github.amsacode.predict4java.SatPassTime;
import com.github.amsacode.predict4java.TLE;
import orbitslot.catalog.SatelliteCatalog;
import orbitslot.io.OmmToTleConverter;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Satellite;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Real passes predicted by predict4java (SGP4) from the CelesTrak snapshot.
 *
 * predict4java finds each pass at the 0 deg horizon. We then find the true
 * highest point, and trim the pass to the station's minimum elevation by
 * binary search on the satellite's elevation (to 1 s). Passes that never reach
 * the minimum, or whose trimmed AOS is outside [start, end), are left out.
 */
public final class Predict4JavaPassSource implements PassSource {

    private static final long SEARCH_PRECISION_MS = 1_000;

    private final SatelliteCatalog catalog;
    private final OmmToTleConverter converter = new OmmToTleConverter();

    public Predict4JavaPassSource(SatelliteCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public List<Pass> passes(List<Satellite> satellites, GroundStation station, Instant start, Instant end) {
        GroundStationPosition position = new GroundStationPosition(
                station.latitudeDeg(), station.longitudeDeg(), station.altitudeM());
        int hours = (int) Math.ceil(Duration.between(start, end).toMinutes() / 60.0);
        double minEl = station.minElevationDeg();

        List<Pass> result = new ArrayList<>();
        for (Satellite sat : satellites) {
            try {
                TLE tle = new TLE(converter.toTle(catalog.orbitOf(sat)));
                PassPredictor predictor = new PassPredictor(tle, position);
                int number = 0;
                for (SatPassTime p : predictor.getPasses(Date.from(start), hours, false)) {
                    Instant horizonAos = p.getStartTime().toInstant();
                    Instant horizonLos = p.getEndTime().toInstant();
                    Instant peak = highestPoint(predictor, horizonAos, horizonLos);
                    double maxEl = elevationDeg(predictor, peak);
                    if (maxEl < minEl) {
                        continue;
                    }
                    Instant aos = crossing(predictor, horizonAos, peak, minEl, true);
                    Instant los = crossing(predictor, peak, horizonLos, minEl, false);
                    if (aos.isBefore(start) || !aos.isBefore(end) || !los.isAfter(aos)) {
                        continue;
                    }
                    number++;
                    result.add(new Pass(sat.noradId() + "-" + number, sat, aos, los, maxEl,
                            azimuthDeg(predictor, aos), azimuthDeg(predictor, los)));
                }
            } catch (SatNotFoundException e) {
                throw new IllegalStateException("predict4java failed for " + sat.name(), e);
            }
        }
        result.sort(Comparator.comparing(Pass::aos).thenComparing(Pass::id));
        return result;
    }

    /**
     * Binary search for the moment the elevation crosses {@code minEl} between
     * {@code lo} and {@code hi}. Rising: below at lo, above at hi. Falling: the
     * other way round. Returns the side that is above the mask.
     */
    private static Instant crossing(PassPredictor predictor, Instant lo, Instant hi, double minEl, boolean rising)
            throws SatNotFoundException {
        while (Duration.between(lo, hi).toMillis() > SEARCH_PRECISION_MS) {
            Instant mid = lo.plusMillis(Duration.between(lo, hi).toMillis() / 2);
            boolean above = elevationDeg(predictor, mid) >= minEl;
            if (above == rising) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        return rising ? hi : lo;
    }

    /**
     * Ternary search for the time of maximum elevation (elevation rises then
     * falls during a pass). We do this ourselves because predict4java's
     * getMaxEl() underestimates high passes by up to ~12 deg (checked against
     * an independent SGP4 implementation).
     */
    private static Instant highestPoint(PassPredictor predictor, Instant lo, Instant hi)
            throws SatNotFoundException {
        while (Duration.between(lo, hi).toMillis() > SEARCH_PRECISION_MS) {
            long third = Duration.between(lo, hi).toMillis() / 3;
            Instant m1 = lo.plusMillis(third);
            Instant m2 = hi.minusMillis(third);
            if (elevationDeg(predictor, m1) < elevationDeg(predictor, m2)) {
                lo = m1;
            } else {
                hi = m2;
            }
        }
        return lo.plusMillis(Duration.between(lo, hi).toMillis() / 2);
    }

    /** predict4java's SatPos returns radians. */
    private static double elevationDeg(PassPredictor predictor, Instant t) throws SatNotFoundException {
        return Math.toDegrees(predictor.getSatPos(Date.from(t)).getElevation());
    }

    private static double azimuthDeg(PassPredictor predictor, Instant t) throws SatNotFoundException {
        return Math.toDegrees(predictor.getSatPos(Date.from(t)).getAzimuth());
    }

    @Override
    public String description() {
        return "predict4java (CelesTrak snapshot)";
    }
}
