package orbitslot;

import orbitslot.alert.ConsoleAlertListener;
import orbitslot.benchmark.BenchmarkRunner;
import orbitslot.catalog.PriorityAssigner;
import orbitslot.catalog.SatelliteCatalog;
import orbitslot.catalog.SatelliteFactory;
import orbitslot.chart.ChartWriter;
import orbitslot.chart.GanttChart;
import orbitslot.constraint.Constraint;
import orbitslot.constraint.MinElevationConstraint;
import orbitslot.constraint.SlewTimeConstraint;
import orbitslot.io.Assumptions;
import orbitslot.io.AssumptionsLoader;
import orbitslot.io.PriorityOverridesLoader;
import orbitslot.io.StationLoader;
import orbitslot.metrics.Metrics;
import orbitslot.metrics.MetricsCalculator;
import orbitslot.model.Schedule.Assignment;
import orbitslot.model.Disruption;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;
import orbitslot.output.BenchmarkPrinter;
import orbitslot.output.ConsolePrinter;
import orbitslot.repair.DisruptionResult;
import orbitslot.repair.DisruptionSimulator;
import orbitslot.repair.NoRepair;
import orbitslot.repair.RepairStrategy;
import orbitslot.repair.RollingHorizonRepair;
import orbitslot.scenario.PassSource;
import orbitslot.scenario.Predict4JavaPassSource;
import orbitslot.scenario.SimulatedPassSource;
import orbitslot.scheduler.ScheduleValidator;
import orbitslot.scheduler.Scheduler;
import orbitslot.scheduler.SchedulerRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entry point. Three modes (run with --help):
 * plan (default), --disrupt (replay disruptions with alerts and repair),
 * --benchmark (many seeded scenarios, summary + CSV).
 */
public final class Main {

    private static final Path DATA = Path.of("data");
    private static final Path BENCHMARK_CSV = Path.of("results", "benchmark.csv");
    private static final Path CHARTS = Path.of("results", "charts");
    private static final int DEFAULT_ROW_LIMIT = 25;

    /** Everything the modes share. */
    private record Context(Assumptions assumptions, GroundStation station, Instant start, Instant end,
                           List<Pass> passes, List<Constraint> constraints, MetricsCalculator calculator,
                           ZoneId zone, ConsolePrinter printer) { }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");   // charts are drawn off-screen
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
                runDisrupt(ctx, planner, repairs, options.charts());
            }
            case "benchmark" -> runBenchmark(ctx, options, schedulers, registry.get("greedy"),
                    repairStrategies("all", assumptions, start, end));
            default -> runPlan(ctx, schedulers, options.charts());
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
        ConsolePrinter printer = new ConsolePrinter(zone, options.showAll() ? 0 : DEFAULT_ROW_LIMIT, System.out);

        System.out.println("OrbitSlot - satellite pass scheduler (" + options.mode() + ")");
        System.out.println("Station   : " + station);
        System.out.println("Window    : " + start + " to " + end);
        System.out.println("Passes    : " + passes.size() + " from " + source.description());
        System.out.println("Satellites: " + catalog.satellites().size() + " loaded, "
                + catalog.excluded().size() + " excluded (not LEO or not TLE-compatible), "
                + overrides.size() + " priority override(s)");
        System.out.println();
        if (options.showPasses()) {
            printer.printPasses(passes);
            System.out.println();
        }
        MetricsCalculator calculator = new MetricsCalculator(assumptions.getInt("metrics.highPriorityMin"));
        return new Context(assumptions, station, start, end, passes, constraints, calculator, zone, printer);
    }

    // --- plan ----------------------------------------------------------------

    private static void runPlan(Context ctx, List<Scheduler> schedulers, boolean charts) throws IOException {
        ScheduleValidator validator = new ScheduleValidator();
        List<Metrics> results = new ArrayList<>();
        List<GanttChart.Panel> panels = new ArrayList<>();

        for (Scheduler scheduler : schedulers) {
            Schedule schedule = scheduler.schedule(ctx.passes(), ctx.station());
            panels.add(new GanttChart.Panel(scheduler.name(), schedule));
            requireValid(scheduler.name(), validator.validate(schedule, ctx.passes()));
            ctx.printer().printSchedule(scheduler.name(), schedule, ctx.start(), ctx.end());
            results.add(ctx.calculator().calculate(scheduler.name(), ctx.passes(), schedule, ctx.start(), ctx.end()));
        }
        ctx.printer().printMetrics("Metrics", results);
        System.out.println("All schedules passed validation (no overlaps, turnaround respected).");
        if (charts) {
            ChartWriter writer = new ChartWriter(CHARTS);
            writer.write("gantt-plan", planGantt(ctx, panels, GanttChart.DEFAULT_WIDTH));
            printCharts(writer);
        }
        printFootnote(ctx);
    }

    // --- disrupt -------------------------------------------------------------

    private static void runDisrupt(Context ctx, Scheduler planner, List<RepairStrategy> repairs, boolean charts)
            throws IOException {
        ScheduleValidator validator = new ScheduleValidator();
        Schedule plan = planner.schedule(ctx.passes(), ctx.station());
        requireValid(planner.name(), validator.validate(plan, ctx.passes()));

        long seed = ctx.assumptions().getLong("random.seed");
        DisruptionSimulator simulator = new DisruptionSimulator(ctx.constraints());
        List<Disruption> disruptions = simulator.generate(plan, ctx.start(), ctx.end(), seed, ctx.assumptions());
        System.out.println("Plan made with '" + planner.name() + "'; " + disruptions.size()
                + " seeded disruptions (seed " + seed + ").");

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
            ctx.printer().printSchedule("after repair '" + repairs.get(0).name() + "'", results.get(0).schedule(),
                    ctx.start(), ctx.end());
        }

        System.out.println();
        ctx.printer().printMetrics("Metrics (plan = before disruptions)", metrics);
        System.out.println();
        System.out.printf("%-10s  %13s  %12s%n", "Repair", "Forced losses", "Plan changes");
        for (DisruptionResult r : results) {
            System.out.printf("%-10s  %13d  %12d%n", r.strategy(), r.forcedLosses(), r.planChanges());
        }
        System.out.println("All repaired schedules passed validation (no cancelled pass kept, nothing during outages).");
        if (charts) {
            // One timeline: the plan, then what was executed with each repair strategy.
            List<GanttChart.Panel> panels = new ArrayList<>();
            panels.add(new GanttChart.Panel("Plan (" + planner.name() + ", before disruptions)", plan,
                    disruptions, Set.of()));
            for (DisruptionResult r : results) {
                panels.add(new GanttChart.Panel("Executed with repair '" + r.strategy() + "'", r.schedule(),
                        disruptions, changedBy(plan, r.schedule())));
            }
            ChartWriter writer = new ChartWriter(CHARTS);
            writer.write("gantt-disrupt", gantt(ctx, GanttChart.DEFAULT_WIDTH).render(
                    "Disruptions: plan vs repair strategies",
                    subtitle(ctx) + "; " + disruptions.size() + " seeded disruptions", panels, ctx.start(), ctx.end()));
            printCharts(writer);
        }
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

        BenchmarkRunner runner = new BenchmarkRunner(a, ctx.calculator(),
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
        if (options.charts()) {
            // The summary also shows each algorithm's schedule for the full day (all satellites).
            ScheduleValidator validator = new ScheduleValidator();
            List<GanttChart.Panel> panels = new ArrayList<>();
            for (Scheduler scheduler : schedulers) {
                Schedule schedule = scheduler.schedule(ctx.passes(), ctx.station());
                requireValid(scheduler.name(), validator.validate(schedule, ctx.passes()));
                panels.add(new GanttChart.Panel(scheduler.name(), schedule));
            }
            java.awt.image.BufferedImage gantt = planGantt(ctx, panels, ChartWriter.SUMMARY_CONTENT_WIDTH);

            ChartWriter writer = new ChartWriter(CHARTS);
            writer.benchmark(results, String.format(Locale.ROOT, "%d scenarios (seeds %d-%d), %d satellites each, %s",
                    scenarios, firstSeed, firstSeed + scenarios - 1, a.getInt("bench.satellitesPerScenario"),
                    ctx.station().name()), gantt);
            printCharts(writer);
        }
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

    private static GanttChart gantt(Context ctx, int width) {
        return new GanttChart(ctx.zone(), ctx.assumptions().getInt("metrics.highPriorityMin"), width);
    }

    /** Full-day timeline with one panel per algorithm. */
    private static java.awt.image.BufferedImage planGantt(Context ctx, List<GanttChart.Panel> panels, int width) {
        String title = panels.size() == 1 ? "Schedule: " + panels.get(0).title()
                : "Schedules of the algorithms for the full day";
        return gantt(ctx, width).render(title, subtitle(ctx), panels, ctx.start(), ctx.end());
    }

    private static String subtitle(Context ctx) {
        return String.format(Locale.ROOT, "%s, %d antenna(s), %s to %s, %d passes offered",
                ctx.station().name(), ctx.station().antennas().size(), ctx.start(), ctx.end(), ctx.passes().size());
    }

    /** Ids of passes in {@code after} that were not planned, or were planned on another antenna. */
    private static Set<String> changedBy(Schedule plan, Schedule after) {
        return after.assignments().stream()
                .filter(a -> {
                    Optional<Assignment> before = plan.assignmentOf(a.pass());
                    return before.isEmpty() || !before.get().antenna().equals(a.antenna());
                })
                .map(a -> a.pass().id())
                .collect(Collectors.toSet());
    }

    private static void printCharts(ChartWriter writer) {
        System.out.println("Charts written (" + writer.written().size() + "):");
        writer.written().forEach(p -> System.out.println("  " + p));
    }

    private static void printFootnote(Context ctx) {
        System.out.println(String.format(Locale.ROOT,
                "High priority = priority >= %d. Data volume = downlink rate x pass duration. "
                        + "Score = sum of priority x MB. All numbers rest on data/assumptions.properties.",
                ctx.assumptions().getInt("metrics.highPriorityMin")));
    }
}
