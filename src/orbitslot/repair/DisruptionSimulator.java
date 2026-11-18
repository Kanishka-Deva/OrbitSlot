package orbitslot.repair;

import orbitslot.alert.ScheduleListener;
import orbitslot.constraint.AntennaOutageConstraint;
import orbitslot.constraint.Constraint;
import orbitslot.io.Assumptions;
import orbitslot.model.Antenna;
import orbitslot.model.Disruption.AntennaOutage;
import orbitslot.model.Schedule.Assignment;
import orbitslot.model.Disruption;
import orbitslot.model.Pass;
import orbitslot.model.Disruption.PassCancellation;
import orbitslot.model.Schedule;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * The disruption simulation, in two steps:
 * <ol>
 *   <li>{@link #generate}: seeded disruptions for a plan - cancellations of
 *       planned passes (announced some minutes before AOS) and antenna outages
 *       (noticed when they start). The same seed always gives the same list.</li>
 *   <li>{@link #run}: plays them against the plan in the order they become
 *       known. For each one it removes the passes that can no longer be
 *       tracked, tells the listeners (Observer pattern), and lets the repair
 *       strategy fix the future.</li>
 * </ol>
 */
public final class DisruptionSimulator {

    private final List<Constraint> baseConstraints;
    private final List<ScheduleListener> listeners = new ArrayList<>();

    public DisruptionSimulator(List<Constraint> baseConstraints) {
        this.baseConstraints = List.copyOf(baseConstraints);
    }

    public void addListener(ScheduleListener listener) {
        listeners.add(listener);
    }

    /**
     * Seeded disruptions for a plan over [start, end), sorted by announcement time.
     * Rates and ranges come from the disrupt.* keys in assumptions.properties.
     */
    public List<Disruption> generate(Schedule plan, Instant start, Instant end, long seed, Assumptions a) {
        int noticeMin = a.getInt("disrupt.noticeMinMinutes");
        int noticeMax = a.getInt("disrupt.noticeMaxMinutes");
        int outageMin = a.getInt("disrupt.outageMinMinutes");
        int outageMax = a.getInt("disrupt.outageMaxMinutes");
        if (outageMax < outageMin || noticeMax < noticeMin) {
            throw new IllegalStateException("Bad disruption ranges in assumptions.properties");
        }
        Random rnd = new Random(seed * 31 + 7);
        double days = Duration.between(start, end).toMinutes() / 1440.0;
        List<Disruption> result = new ArrayList<>();

        List<Assignment> planned = new ArrayList<>(plan.assignments());
        planned.sort(Comparator.comparing((Assignment x) -> x.pass().id()));
        Collections.shuffle(planned, rnd);
        int cancellations = Math.min(planned.size(),
                (int) Math.round(a.getDouble("disrupt.cancellationsPerDay") * days));
        for (Assignment x : planned.subList(0, cancellations)) {
            Instant notice = x.pass().aos().minus(Duration.ofMinutes(between(rnd, noticeMin, noticeMax)));
            result.add(new PassCancellation(x.pass(), notice.isBefore(start) ? start : notice));
        }

        List<Antenna> antennas = plan.station().antennas();
        long latestStartMinutes = Math.max(0, Duration.between(start, end).toMinutes() - outageMax);
        int outages = (int) Math.round(a.getDouble("disrupt.outagesPerDay") * days);
        for (int i = 0; i < outages; i++) {
            Instant from = start.plus(Duration.ofMinutes(between(rnd, 0, (int) latestStartMinutes)));
            Instant to = from.plus(Duration.ofMinutes(between(rnd, outageMin, outageMax)));
            result.add(new AntennaOutage(antennas.get(rnd.nextInt(antennas.size())), from, to));
        }
        result.sort(Comparator.comparing(Disruption::announcedAt));
        return result;
    }

    public DisruptionResult run(Schedule plan, List<Pass> allPasses, List<Disruption> disruptions,
                                RepairStrategy repair) {
        Schedule schedule = plan.copy();
        Set<String> initial = keys(plan);
        Set<String> forced = new HashSet<>();
        Set<String> cancelled = new HashSet<>();
        List<AntennaOutage> outages = new ArrayList<>();

        List<Disruption> ordered = new ArrayList<>(disruptions);
        ordered.sort(Comparator.comparing(Disruption::announcedAt));

        for (Disruption d : ordered) {
            listeners.forEach(l -> l.disruptionAnnounced(d));

            switch (d) {
                case PassCancellation c -> {
                    cancelled.add(c.pass().id());
                    Optional<Assignment> planned = schedule.assignmentOf(c.pass());
                    if (planned.isPresent()) {
                        lose(schedule, planned.get(), "cancelled", forced);
                    }
                    schedule.drop(c.pass(), "cancelled");
                }
                case AntennaOutage o -> {
                    outages.add(o);
                    for (Assignment a : schedule.assignmentsFor(o.antenna())) {
                        if (o.blocks(a)) {
                            lose(schedule, a, o.antenna() + " outage", forced);
                        }
                    }
                }
            }

            List<Pass> candidates = allPasses.stream()
                    .filter(p -> !cancelled.contains(p.id()) && !p.aos().isBefore(d.announcedAt()))
                    .toList();
            List<Constraint> constraints = new ArrayList<>(baseConstraints);
            constraints.add(new AntennaOutageConstraint(outages));

            Set<String> before = keys(schedule);
            schedule = repair.repair(schedule, new RepairContext(d.announcedAt(), candidates, constraints));
            Set<String> after = keys(schedule);
            int added = difference(after, before).size();
            int removed = difference(before, after).size();
            if (added + removed > 0) {
                listeners.forEach(l -> l.repaired(repair.name(), d.announcedAt(), added, removed));
            }
        }

        Set<String> finalKeys = keys(schedule);
        Set<String> removedOnPurpose = difference(difference(initial, finalKeys), forced);
        int changes = difference(finalKeys, initial).size() + removedOnPurpose.size();
        return new DisruptionResult(repair.name(), schedule, forced.size(), changes);
    }

    private void lose(Schedule schedule, Assignment a, String reason, Set<String> forced) {
        schedule.remove(a.pass());
        schedule.drop(a.pass(), "lost: " + reason);
        forced.add(key(a));
        listeners.forEach(l -> l.passLost(a.pass(), reason));
    }

    /** An assignment's identity for change counting: pass id + antenna. */
    private static String key(Assignment a) {
        return a.pass().id() + "@" + a.antenna().number();
    }

    private static Set<String> keys(Schedule s) {
        Set<String> keys = new HashSet<>();
        s.assignments().forEach(a -> keys.add(key(a)));
        return keys;
    }

    private static Set<String> difference(Set<String> a, Set<String> b) {
        Set<String> d = new HashSet<>(a);
        d.removeAll(b);
        return d;
    }

    private static int between(Random rnd, int min, int max) {
        return min + rnd.nextInt(max - min + 1);
    }
}
