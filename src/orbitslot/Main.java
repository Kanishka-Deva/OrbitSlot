package orbitslot;

import orbitslot.alert.ConsoleAlertListener;
import orbitslot.benchmark.BenchmarkRunner;
import orbitslot.catalog.PriorityAssigner;
import orbitslot.catalog.SatelliteCatalog;
import orbitslot.catalog.SatelliteFactory;
import orbitslot.constraint.Constraint;
import orbitslot.constraint.MinElevationConstraint;
import orbitslot.constraint.SlewTimeConstraint;
import orbitslot.io.Assumptions;
import orbitslot.io.AssumptionsLoader;
import orbitslot.io.PriorityOverridesLoader;
import orbitslot.io.StationLoader;
import orbitslot.metrics.Metrics;
import orbitslot.metrics.MetricsCalculator;
import orbitslot.model.Disruption;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;
import orbitslot.output.BenchmarkPrinter;
import orbitslot.output.MetricsTablePrinter;
import orbitslot.output.PassTablePrinter;
import orbitslot.output.SchedulePrinter;
import orbitslot.repair.DisruptionResult;
import orbitslot.repair.DisruptionSimulator;
import orbitslot.repair.NoRepair;
import orbitslot.repair.RepairStrategy;
import orbitslot.repair.RollingHorizonRepair;
import orbitslot.scenario.PassSource;
import orbitslot.scenario.Predict4JavaPassSource;
import orbitslot.scenario.ScenarioGenerator;
import orbitslot.scenario.SimulatedPassSource;
import orbitslot.scheduler.ScheduleValidator;
import orbitslot.scheduler.Scheduler;
import orbitslot.scheduler.SchedulerRegistry;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Entry point. Three modes (run with --help):
 * plan (default), --disrupt (replay disruptions with alerts and repair),
 * --benchmark (many seeded scenarios, summary + CSV).
 */
public final class Main {

    private static final Path DATA = Path.of("data");
    private static final Path BENCHMARK_CSV = Path.of("results", "benchmark.csv");
    private static final int DEFAULT_ROW_LIMIT = 25;

    /** Everything the modes share. */
    private record Context(Assumptions assumptions, GroundStation station, Instant start, Instant end,
                           List<Pass> passes, List<Constraint> constraints, MetricsCalculator calculator,
                           ZoneId zone, int rowLimit) { }

    public static void main(String[] args) {
        try {
            run(CliOptions.parse(args));
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println(CliOptions.USAGE);
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Error: " + e);
            System.exit(1);
        }
    }

    private static void run(CliOptions options) throws Exception {
        if (options.help()) {
            System.out.println(CliOptions.USAGE);
            return;
        }
        Assumptions assumptions = new AssumptionsLoader().load(DATA.resolve("assumptions.properties"));
        Instant start = assumptions.getInstant("schedule.start");
        Instant end = start.plusSeconds(3600L * assumptions.getInt("schedule.horizonHours"));

        // Resolve algorithm and repair names first, so a typo fails before the slow prediction.
        List<Constraint> constraints = List.of(new MinElevationConstraint(), new SlewTimeConstraint());
        SchedulerRegistry registry = new SchedulerRegistry(constraints, assumptions);
        List<Scheduler> schedulers = options.algo().equals("all")
                ? new ArrayList<>(registry.all())
                : List.of(registry.get(options.algo()));
        List<RepairStrategy> repairs = repairStrategies(options.repair(), assumptions, start, end);

        Context ctx = load(options, assumptions, start, end, constraints);
        switch (options.mode()) {
            case "disrupt" -> {
                Scheduler planner = options.algo().equals("all") ? registry.get("greedy") : schedulers.get(0);
                runDisrupt(ctx, planner, repairs);
            }
            case "benchmark" -> runBenchmark(ctx, options, schedulers, registry.get("greedy"),
                    repairStrategies("all", assumptions, start, end));
            default -> runPlan(ctx, schedulers);
        }
    }

    private static Context load(CliOptions options, Assumptions assumptions, Instant start, Instant end,
                                List<Constraint> constraints) throws Exception {
        String stationId = options.station() != null ? options.station() : assumptions.getString("station.id");
        GroundStation station = new StationLoader().load(DATA.resolve("stations.csv"), assumptions, stationId);

        Map<Integer, Integer> overrides = new PriorityOverridesLoader().load(DATA.resolve("priorities.csv"));
        SatelliteFactory factory = new SatelliteFactory(assumptions, new PriorityAssigner(assumptions, overrides));
        SatelliteCatalog catalog = SatelliteCatalog.load(DATA.resolve("celestrak"), assumptions, factory);

        PassSource source = options.source().equals("sim")
                ? new SimulatedPassSource(assumptions)
                : new Predict4JavaPassSource(catalog);
        List<Pass> passes = source.passes(catalog.satellites(), station, start, end);

        ZoneId zone = assumptions.getZone("display.timezone");
        int rowLimit = options.showAll() ? 0 : DEFAULT_ROW_LIMIT;

        System.out.println("OrbitSlot - satellite pass scheduler (" + options.mode() + ")");
        System.out.println("Station   : " + station);
        System.out.println("Window    : " + start + " to " + end);
        System.out.println("Passes    : " + passes.size() + " from " + source.description());
        System.out.println("Satellites: " + catalog.satellites().size() + " loaded, "
                + catalog.excluded().size() + " excluded (not LEO or not TLE-compatible), "
                + overrides.size() + " priority override(s)");
        System.out.println();
        if (options.showPasses()) {
            new PassTablePrinter(zone).print(passes, rowLimit, System.out);
            System.out.println();
        }
        MetricsCalculator calculator = new MetricsCalculator(assumptions.getInt("metrics.highPriorityMin"));
        return new Context(assumptions, station, start, end, passes, constraints, calculator, zone, rowLimit);
    }

    // --- plan ----------------------------------------------------------------

    private static void runPlan(Context ctx, List<Scheduler> schedulers) {
        ScheduleValidator validator = new ScheduleValidator();
        SchedulePrinter printer = new SchedulePrinter(ctx.zone(), ctx.rowLimit());
        List<Metrics> results = new ArrayList<>();

        for (Scheduler scheduler : schedulers) {
            Schedule schedule = scheduler.schedule(ctx.passes(), ctx.station());
            requireValid(scheduler.name(), validator.validate(schedule, ctx.passes()));
            printer.print(scheduler.name(), schedule, ctx.start(), ctx.end(), System.out);
            results.add(ctx.calculator().calculate(scheduler.name(), ctx.passes(), schedule, ctx.start(), ctx.end()));
        }
        new MetricsTablePrinter().print("Metrics", results, System.out);
        System.out.println("All schedules passed validation (no overlaps, turnaround respected).");
        printFootnote(ctx);
    }

    // --- disrupt -------------------------------------------------------------

    private static void runDisrupt(Context ctx, Scheduler planner, List<RepairStrategy> repairs) {
        ScheduleValidator validator = new ScheduleValidator();
        Schedule plan = planner.schedule(ctx.passes(), ctx.station());
        requireValid(planner.name(), validator.validate(plan, ctx.passes()));

        long seed = ctx.assumptions().getLong("random.seed");
        List<Disruption> disruptions = new ScenarioGenerator(ctx.assumptions())
                .disruptions(plan, ctx.start(), ctx.end(), seed);
        System.out.println("Plan made with '" + planner.name() + "'; " + disruptions.size()
                + " seeded disruptions (seed " + seed + ").");

        DisruptionSimulator simulator = new DisruptionSimulator(ctx.constraints());
        simulator.addListener(new ConsoleAlertListener(ctx.zone(),
                ctx.assumptions().getInt("metrics.highPriorityMin"), System.out));

        List<Metrics> metrics = new ArrayList<>();
        metrics.add(ctx.calculator().calculate("plan", ctx.passes(), plan, ctx.start(), ctx.end()));
        List<DisruptionResult> results = new ArrayList<>();
        for (RepairStrategy repair : repairs) {
            System.out.println();
            System.out.println("=== Replaying disruptions with repair '" + repair.name() + "' ===");
            DisruptionResult r = simulator.run(plan, ctx.passes(), disruptions, repair);
            requireValid(repair.name(), validator.validate(r.schedule(), ctx.passes(), disruptions));
            results.add(r);
            metrics.add(ctx.calculator().calculate(repair.name(), ctx.passes(), r.schedule(), ctx.start(), ctx.end()));
        }
        if (repairs.size() == 1) {
            System.out.println();
            new SchedulePrinter(ctx.zone(), ctx.rowLimit())
                    .print("after repair '" + repairs.get(0).name() + "'", results.get(0).schedule(),
                            ctx.start(), ctx.end(), System.out);
        }

        System.out.println();
        new MetricsTablePrinter().print("Metrics (plan = before disruptions)", metrics, System.out);
        System.out.println();
        System.out.printf("%-10s  %13s  %12s%n", "Repair", "Forced losses", "Plan changes");
        for (DisruptionResult r : results) {
            System.out.printf("%-10s  %13d  %12d%n", r.strategy(), r.forcedLosses(), r.planChanges());
        }
        System.out.println("All repaired schedules passed validation (no cancelled pass kept, nothing during outages).");
        printFootnote(ctx);
    }

    // --- benchmark -----------------------------------------------------------

    private static void runBenchmark(Context ctx, CliOptions options, List<Scheduler> schedulers,
                                     Scheduler planner, List<RepairStrategy> repairs) throws Exception {
        Assumptions a = ctx.assumptions();
        int scenarios = options.scenarios() != null ? options.scenarios() : a.getInt("bench.scenarios");
        long firstSeed = a.getLong("random.seed");
        System.out.printf("Benchmark: %d scenarios (seeds %d-%d), %d of the satellites each; "
                        + "disruptions applied to the '%s' plan.%n%n",
                scenarios, firstSeed, firstSeed + scenarios - 1, a.getInt("bench.satellitesPerScenario"),
                planner.name());

        BenchmarkRunner runner = new BenchmarkRunner(new ScenarioGenerator(a), ctx.calculator(),
                new DisruptionSimulator(ctx.constraints()));
        BenchmarkRunner.Results results = runner.run(ctx.passes(), ctx.station(), ctx.start(), ctx.end(),
                scenarios, firstSeed, schedulers, planner, repairs);

        BenchmarkPrinter printer = new BenchmarkPrinter();
        printer.printPlans(results.plans(), System.out);
        System.out.println();
        printer.printRepairs(results.repairs(), System.out);
        printer.writeCsv(results, BENCHMARK_CSV);
        System.out.println();
        System.out.println("All " + (results.plans().size() + results.repairs().size())
                + " schedules passed validation. Raw rows: " + BENCHMARK_CSV);
        printFootnote(ctx);
    }

    // --- helpers -------------------------------------------------------------

    private static List<RepairStrategy> repairStrategies(String name, Assumptions a, Instant start, Instant end) {
        RepairStrategy none = new NoRepair();
        RepairStrategy rolling = new RollingHorizonRepair("rolling", Duration.ofHours(a.getInt("repair.horizonHours")));
        RepairStrategy full = new RollingHorizonRepair("full", Duration.between(start, end).plusDays(1));
        return switch (name) {
            case "none" -> List.of(none);
            case "rolling" -> List.of(rolling);
            case "full" -> List.of(full);
            case "all" -> List.of(none, rolling, full);
            default -> throw new IllegalArgumentException("Unknown repair '" + name + "'. Available: none, rolling, full, all");
        };
    }

    private static void requireValid(String name, List<String> problems) {
        if (!problems.isEmpty()) {
            problems.forEach(p -> System.err.println("INVALID " + name + ": " + p));
            throw new IllegalStateException(name + " produced an invalid schedule");
        }
    }

    private static void printFootnote(Context ctx) {
        System.out.println(String.format(Locale.ROOT,
                "High priority = priority >= %d. Data volume = downlink rate x pass duration. "
                        + "Score = sum of priority x MB. All numbers rest on data/assumptions.properties.",
                ctx.assumptions().getInt("metrics.highPriorityMin")));
    }
}
