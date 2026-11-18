package orbitslot.benchmark;

import orbitslot.io.Assumptions;
import orbitslot.metrics.Metrics;
import orbitslot.metrics.MetricsCalculator;
import orbitslot.model.Disruption;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Satellite;
import orbitslot.model.Schedule;
import orbitslot.repair.DisruptionResult;
import orbitslot.repair.DisruptionSimulator;
import orbitslot.repair.RepairStrategy;
import orbitslot.scheduler.ScheduleValidator;
import orbitslot.scheduler.Scheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Runs every scheduler, and every repair strategy, on the same N seeded
 * scenarios. A scenario is the passes of a seeded random subset of the
 * satellites, plus the disruptions {@link DisruptionSimulator} generates for
 * it. Each schedule is validated; an invalid one stops the benchmark.
 *
 * Disruption part: the plan to disrupt is made by {@code planAlgorithm}, and
 * all repair strategies get the same plan and the same disruptions.
 */
public final class BenchmarkRunner {

    /** One scheduler on one scenario. */
    public record PlanRow(long seed, Metrics metrics, long runtimeMs) { }

    /** One repair strategy on one scenario. */
    public record RepairRow(long seed, Metrics metrics, int forcedLosses, int planChanges) { }

    public record Results(List<PlanRow> plans, List<RepairRow> repairs) { }

    private final Assumptions assumptions;
    private final MetricsCalculator calculator;
    private final ScheduleValidator validator = new ScheduleValidator();
    private final DisruptionSimulator simulator;

    public BenchmarkRunner(Assumptions assumptions, MetricsCalculator calculator, DisruptionSimulator simulator) {
        this.assumptions = assumptions;
        this.calculator = calculator;
        this.simulator = simulator;
    }

    public Results run(List<Pass> pool, GroundStation station, Instant start, Instant end, int scenarios,
                       long firstSeed, List<Scheduler> schedulers, Scheduler planAlgorithm,
                       List<RepairStrategy> repairs) {
        List<PlanRow> planRows = new ArrayList<>();
        List<RepairRow> repairRows = new ArrayList<>();

        for (long seed = firstSeed; seed < firstSeed + scenarios; seed++) {
            List<Pass> passes = subset(pool, seed);

            for (Scheduler scheduler : schedulers) {
                long t0 = System.nanoTime();
                Schedule schedule = scheduler.schedule(passes, station);
                long ms = (System.nanoTime() - t0) / 1_000_000;
                check(scheduler.name(), seed, validator.validate(schedule, passes));
                planRows.add(new PlanRow(seed, calculator.calculate(scheduler.name(), passes, schedule, start, end), ms));
            }

            Schedule plan = planAlgorithm.schedule(passes, station);
            List<Disruption> disruptions = simulator.generate(plan, start, end, seed, assumptions);
            for (RepairStrategy repair : repairs) {
                DisruptionResult r = simulator.run(plan, passes, disruptions, repair);
                check(repair.name(), seed, validator.validate(r.schedule(), passes, disruptions));
                repairRows.add(new RepairRow(seed, calculator.calculate(repair.name(), passes, r.schedule(), start, end),
                        r.forcedLosses(), r.planChanges()));
            }
        }
        return new Results(planRows, repairRows);
    }

    /** The passes of a seeded random subset of the satellites in the pool. */
    private List<Pass> subset(List<Pass> pool, long seed) {
        List<Satellite> satellites = new ArrayList<>(pool.stream().map(Pass::satellite).distinct()
                .sorted(Comparator.comparingInt(Satellite::noradId)).toList());
        Collections.shuffle(satellites, new Random(seed));
        int n = Math.min(assumptions.getInt("bench.satellitesPerScenario"), satellites.size());
        Set<Satellite> chosen = new HashSet<>(satellites.subList(0, n));
        return pool.stream().filter(p -> chosen.contains(p.satellite())).toList();
    }

    private static void check(String name, long seed, List<String> problems) {
        if (!problems.isEmpty()) {
            problems.forEach(p -> System.err.println("INVALID " + name + " seed " + seed + ": " + p));
            throw new IllegalStateException(name + " produced an invalid schedule for seed " + seed);
        }
    }
}
