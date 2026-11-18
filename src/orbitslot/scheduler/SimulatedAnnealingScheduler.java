package orbitslot.scheduler;

import orbitslot.constraint.Constraint;
import orbitslot.constraint.Turnaround;
import orbitslot.model.Antenna;
import orbitslot.model.Schedule.Assignment;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * Local search by simulated annealing, starting from the Greedy schedule and
 * maximising {@link Objective} (priority x data).
 *
 * Each step picks a random pass:
 * - not scheduled: "insert" it on a random antenna, removing the passes that
 *   clash with it there and trying to move those to another antenna;
 * - scheduled: "move" it to another antenna (score unchanged, frees room).
 * Better results are always kept; worse ones are kept with probability
 * exp(delta / T), where the temperature T falls geometrically. This lets the
 * search escape local optima early on. The best schedule seen is returned,
 * so the result is never worse than Greedy. Seeded, so runs are repeatable.
 */
public final class SimulatedAnnealingScheduler implements Scheduler {

    private final List<Constraint> constraints;
    private final long seed;
    private final int iterations;
    private final double startTemperatureFraction;
    private final double endTemperatureFraction;

    /**
     * @param startTemperatureFraction start temperature as a fraction of the mean pass score
     * @param endTemperatureFraction   end temperature as a fraction of the mean pass score
     */
    public SimulatedAnnealingScheduler(List<Constraint> constraints, long seed, int iterations,
                                       double startTemperatureFraction, double endTemperatureFraction) {
        if (iterations < 0 || startTemperatureFraction <= 0 || endTemperatureFraction <= 0) {
            throw new IllegalArgumentException("Bad simulated annealing settings");
        }
        this.constraints = List.copyOf(constraints);
        this.seed = seed;
        this.iterations = iterations;
        this.startTemperatureFraction = startTemperatureFraction;
        this.endTemperatureFraction = endTemperatureFraction;
    }

    @Override
    public String name() {
        return "sa";
    }

    @Override
    public Schedule schedule(List<Pass> passes, GroundStation station) {
        GreedyPriorityScheduler greedy = new GreedyPriorityScheduler(constraints);
        Schedule current = greedy.schedule(passes, station);
        if (passes.isEmpty()) {
            return current;
        }

        SplittableRandom rnd = new SplittableRandom(seed);
        double meanScore = passes.stream().mapToDouble(Objective::score).average().orElse(1);
        double temperature = startTemperatureFraction * meanScore;
        double cooling = Math.pow(endTemperatureFraction / startTemperatureFraction, 1.0 / Math.max(1, iterations));

        double score = Objective.score(current);
        double bestScore = score;
        List<Assignment> best = current.assignments();

        for (int i = 0; i < iterations; i++, temperature *= cooling) {
            Pass pass = passes.get(rnd.nextInt(passes.size()));
            Optional<Assignment> existing = current.assignmentOf(pass);
            if (existing.isPresent()) {
                move(current, existing.get(), rnd);
                continue;
            }
            List<Antenna> antennas = station.antennas();
            Double delta = insert(current, pass, antennas.get(rnd.nextInt(antennas.size())), temperature, rnd);
            if (delta != null) {
                score += delta;
                if (score > bestScore + 1e-9) {
                    bestScore = score;
                    best = current.assignments();
                }
            }
        }
        return finish(best, passes, station, greedy);
    }

    /**
     * Puts {@code pass} on {@code antenna}, evicting clashing passes and trying
     * to re-home them on other antennas. Accepted by the annealing rule or undone.
     * @return the score change if accepted, or null if the move was undone
     */
    private Double insert(Schedule s, Pass pass, Antenna antenna, double temperature, SplittableRandom rnd) {
        GroundStation station = s.station();
        List<Assignment> evicted = s.assignmentsFor(antenna).stream()
                .filter(e -> clashes(e.pass(), pass, station))
                .toList();
        evicted.forEach(e -> s.remove(e.pass()));

        Assignment candidate = new Assignment(pass, antenna);
        if (Placement.firstViolation(candidate, s, constraints).isPresent()) {
            evicted.forEach(s::add);
            return null;
        }
        s.add(candidate);
        double delta = Objective.score(pass);

        List<Assignment> rehomed = new ArrayList<>();
        for (Assignment e : evicted) {
            delta -= Objective.score(e.pass());
            for (Antenna other : station.antennas()) {
                Assignment alt = new Assignment(e.pass(), other);
                if (!other.equals(antenna) && Placement.firstViolation(alt, s, constraints).isEmpty()) {
                    s.add(alt);
                    rehomed.add(alt);
                    delta += Objective.score(e.pass());
                    break;
                }
            }
        }

        if (delta >= 0 || rnd.nextDouble() < Math.exp(delta / temperature)) {
            return delta;
        }
        rehomed.forEach(r -> s.remove(r.pass()));    // undo
        s.remove(pass);
        evicted.forEach(s::add);
        return null;
    }

    /** Moves a scheduled pass to another random antenna if the constraints allow it. */
    private void move(Schedule s, Assignment existing, SplittableRandom rnd) {
        List<Antenna> antennas = s.station().antennas();
        if (antennas.size() < 2) {
            return;
        }
        Antenna target = antennas.get(rnd.nextInt(antennas.size()));
        if (target.equals(existing.antenna())) {
            return;
        }
        s.remove(existing.pass());
        Assignment moved = new Assignment(existing.pass(), target);
        s.add(Placement.firstViolation(moved, s, constraints).isEmpty() ? moved : existing);
    }

    /** Two passes clash on one antenna if neither can follow the other (slew + setup). */
    private static boolean clashes(Pass a, Pass b, GroundStation station) {
        return !(Turnaround.fits(a, b, station) || Turnaround.fits(b, a, station));
    }

    /** Rebuilds the best schedule, adds any pass that still fits for free, drops the rest with reasons. */
    private Schedule finish(List<Assignment> best, List<Pass> passes, GroundStation station,
                            GreedyPriorityScheduler greedy) {
        Schedule result = new Schedule(station);
        best.forEach(result::add);
        greedy.extend(result, passes);
        return result;
    }
}
