package orbitslot.scenario;

import orbitslot.io.Assumptions;
import orbitslot.model.Antenna;
import orbitslot.model.AntennaOutage;
import orbitslot.model.Assignment;
import orbitslot.model.Disruption;
import orbitslot.model.Pass;
import orbitslot.model.PassCancellation;
import orbitslot.model.Satellite;
import orbitslot.model.Schedule;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Builds reproducible test scenarios from a seed:
 * - a pass list: the passes of a random subset of satellites from a pool
 *   (the pool is real predicted or simulated passes);
 * - disruptions for a plan: cancellations of planned passes (announced some
 *   minutes before AOS) and antenna outages (noticed when they start).
 * The same seed always gives the same scenario.
 */
public final class ScenarioGenerator {

    private final int satellitesPerScenario;
    private final double cancellationsPerDay;
    private final double outagesPerDay;
    private final int outageMinMinutes;
    private final int outageMaxMinutes;
    private final int noticeMinMinutes;
    private final int noticeMaxMinutes;

    public ScenarioGenerator(Assumptions a) {
        this.satellitesPerScenario = a.getInt("bench.satellitesPerScenario");
        this.cancellationsPerDay = a.getDouble("disrupt.cancellationsPerDay");
        this.outagesPerDay = a.getDouble("disrupt.outagesPerDay");
        this.outageMinMinutes = a.getInt("disrupt.outageMinMinutes");
        this.outageMaxMinutes = a.getInt("disrupt.outageMaxMinutes");
        this.noticeMinMinutes = a.getInt("disrupt.noticeMinMinutes");
        this.noticeMaxMinutes = a.getInt("disrupt.noticeMaxMinutes");
        if (outageMaxMinutes < outageMinMinutes || noticeMaxMinutes < noticeMinMinutes) {
            throw new IllegalStateException("Bad disruption ranges in assumptions.properties");
        }
    }

    /** The passes of a seeded random subset of the satellites in the pool. */
    public List<Pass> passes(List<Pass> pool, long seed) {
        List<Satellite> satellites = new ArrayList<>(pool.stream().map(Pass::satellite).distinct()
                .sorted(Comparator.comparingInt(Satellite::noradId)).toList());
        Collections.shuffle(satellites, new Random(seed));
        Set<Satellite> chosen = new HashSet<>(satellites.subList(0, Math.min(satellitesPerScenario, satellites.size())));
        return pool.stream().filter(p -> chosen.contains(p.satellite())).toList();
    }

    /** Seeded disruptions for a plan over [start, end). Cancellations hit planned passes. */
    public List<Disruption> disruptions(Schedule plan, Instant start, Instant end, long seed) {
        Random rnd = new Random(seed * 31 + 7);
        double days = Duration.between(start, end).toMinutes() / 1440.0;
        List<Disruption> result = new ArrayList<>();

        List<Assignment> planned = new ArrayList<>(plan.assignments());
        planned.sort(Comparator.comparing((Assignment a) -> a.pass().id()));
        Collections.shuffle(planned, rnd);
        int cancellations = Math.min(planned.size(), (int) Math.round(cancellationsPerDay * days));
        for (Assignment a : planned.subList(0, cancellations)) {
            Instant notice = a.pass().aos().minus(Duration.ofMinutes(between(rnd, noticeMinMinutes, noticeMaxMinutes)));
            result.add(new PassCancellation(a.pass(), notice.isBefore(start) ? start : notice));
        }

        List<Antenna> antennas = plan.station().antennas();
        long latestStartMinutes = Math.max(0, Duration.between(start, end).toMinutes() - outageMaxMinutes);
        int outages = (int) Math.round(outagesPerDay * days);
        for (int i = 0; i < outages; i++) {
            Instant from = start.plus(Duration.ofMinutes(between(rnd, 0, (int) latestStartMinutes)));
            Instant to = from.plus(Duration.ofMinutes(between(rnd, outageMinMinutes, outageMaxMinutes)));
            result.add(new AntennaOutage(antennas.get(rnd.nextInt(antennas.size())), from, to));
        }
        result.sort(Comparator.comparing(Disruption::announcedAt));
        return result;
    }

    private static int between(Random rnd, int min, int max) {
        return min + rnd.nextInt(max - min + 1);
    }
}
