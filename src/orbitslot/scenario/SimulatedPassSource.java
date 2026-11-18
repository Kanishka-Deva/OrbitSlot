package orbitslot.scenario;

import orbitslot.io.Assumptions;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Satellite;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Generates random but reproducible passes, so scheduling work never has to
 * wait for real orbit prediction.
 *
 * For each satellite the window is cut into N equal slots (N = passes per day,
 * scaled to the window length) and one pass is placed at a random time inside
 * each slot. That way a satellite never overlaps with itself, as in reality.
 * Each satellite has its own random generator seeded with (seed, NORAD id).
 */
public final class SimulatedPassSource implements PassSource {

    private final long seed;
    private final double passesPerDay;
    private final int minDurationMin;
    private final int maxDurationMin;
    private final double minMaxElevation;
    private final double maxMaxElevation;

    public SimulatedPassSource(Assumptions a) {
        this(a.getLong("random.seed"),
             a.getDouble("sim.passesPerSatellitePerDay"),
             a.getInt("sim.minDurationMinutes"),
             a.getInt("sim.maxDurationMinutes"),
             a.getDouble("sim.maxElevationMinDeg"),
             a.getDouble("sim.maxElevationMaxDeg"));
    }

    public SimulatedPassSource(long seed, double passesPerDay, int minDurationMin, int maxDurationMin,
                               double minMaxElevation, double maxMaxElevation) {
        if (minDurationMin <= 0 || maxDurationMin < minDurationMin) {
            throw new IllegalArgumentException("Bad simulated pass duration range");
        }
        this.seed = seed;
        this.passesPerDay = passesPerDay;
        this.minDurationMin = minDurationMin;
        this.maxDurationMin = maxDurationMin;
        this.minMaxElevation = minMaxElevation;
        this.maxMaxElevation = maxMaxElevation;
    }

    @Override
    public List<Pass> passes(List<Satellite> satellites, GroundStation station, Instant start, Instant end) {
        long windowSeconds = Duration.between(start, end).toSeconds();
        int slots = Math.max(1, (int) Math.round(passesPerDay * windowSeconds / 86_400.0));
        long slotSeconds = windowSeconds / slots;
        double lowestElevation = Math.max(minMaxElevation, station.minElevationDeg());

        List<Pass> result = new ArrayList<>();
        for (Satellite sat : satellites) {
            SplittableRandom rnd = new SplittableRandom(seed * 1_000_003L + sat.noradId());
            for (int i = 0; i < slots; i++) {
                long durationSec = 60L * rnd.nextInt(minDurationMin, maxDurationMin + 1);
                long latestOffset = Math.max(0, slotSeconds - durationSec);
                Instant aos = start.plusSeconds(i * slotSeconds + rnd.nextLong(latestOffset + 1));
                Instant los = aos.plusSeconds(durationSec);

                double maxEl = lowestElevation + rnd.nextDouble() * (maxMaxElevation - lowestElevation);
                double aosAz = rnd.nextInt(0, 360);
                // A pass crosses the sky, so LOS is roughly on the other side: 90-270 deg away.
                double losAz = (aosAz + rnd.nextInt(90, 271)) % 360;

                result.add(new Pass(sat.noradId() + "-" + (i + 1), sat, aos, los, maxEl, aosAz, losAz));
            }
        }
        result.sort(Comparator.comparing(Pass::aos).thenComparing(Pass::id));
        return result;
    }

    @Override
    public String description() {
        return "simulated (seed " + seed + ")";
    }
}
