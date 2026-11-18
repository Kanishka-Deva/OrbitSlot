package orbitslot;

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
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;
import orbitslot.output.MetricsTablePrinter;
import orbitslot.output.PassTablePrinter;
import orbitslot.output.SchedulePrinter;
import orbitslot.scenario.PassSource;
import orbitslot.scenario.Predict4JavaPassSource;
import orbitslot.scenario.SimulatedPassSource;
import orbitslot.scheduler.ScheduleValidator;
import orbitslot.scheduler.Scheduler;
import orbitslot.scheduler.SchedulerRegistry;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Entry point: load data, get passes, run the chosen scheduler(s), validate
 * each schedule, and print schedules plus a metrics comparison.
 * Run with --help for the options.
 */
public final class Main {

    private static final Path DATA = Path.of("data");
    private static final int DEFAULT_ROW_LIMIT = 25;

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

        // Pick the scheduler(s) first, so a typo fails before the slow prediction.
        List<Constraint> constraints = List.of(new MinElevationConstraint(), new SlewTimeConstraint());
        SchedulerRegistry registry = new SchedulerRegistry(constraints);
        List<Scheduler> chosen = options.algo().equals("all")
                ? new ArrayList<>(registry.all())
                : List.of(registry.get(options.algo()));

        // --- Input -----------------------------------------------------------
        Assumptions assumptions = new AssumptionsLoader().load(DATA.resolve("assumptions.properties"));
        String stationId = options.station() != null ? options.station() : assumptions.getString("station.id");
        GroundStation station = new StationLoader().load(DATA.resolve("stations.csv"), assumptions, stationId);

        Map<Integer, Integer> overrides = new PriorityOverridesLoader().load(DATA.resolve("priorities.csv"));
        SatelliteFactory factory = new SatelliteFactory(assumptions, new PriorityAssigner(assumptions, overrides));
        SatelliteCatalog catalog = SatelliteCatalog.load(DATA.resolve("celestrak"), assumptions, factory);

        Instant start = assumptions.getInstant("schedule.start");
        Instant end = start.plusSeconds(3600L * assumptions.getInt("schedule.horizonHours"));
        PassSource source = options.source().equals("sim")
                ? new SimulatedPassSource(assumptions)
                : new Predict4JavaPassSource(catalog);
        List<Pass> passes = source.passes(catalog.satellites(), station, start, end);

        ZoneId zone = assumptions.getZone("display.timezone");
        int rowLimit = options.showAll() ? 0 : DEFAULT_ROW_LIMIT;

        System.out.println("OrbitSlot - satellite pass scheduler");
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

        // --- Scheduling --------------------------------------------------------
        MetricsCalculator calculator = new MetricsCalculator(assumptions.getInt("metrics.highPriorityMin"));
        ScheduleValidator validator = new ScheduleValidator();
        SchedulePrinter printer = new SchedulePrinter(zone, rowLimit);
        List<Metrics> results = new ArrayList<>();

        for (Scheduler scheduler : chosen) {
            Schedule schedule = scheduler.schedule(passes, station);
            List<String> problems = validator.validate(schedule, passes);
            if (!problems.isEmpty()) {
                problems.forEach(p -> System.err.println("INVALID " + scheduler.name() + ": " + p));
                throw new IllegalStateException(scheduler.name() + " produced an invalid schedule");
            }
            printer.print(scheduler.name(), schedule, start, end, System.out);
            results.add(calculator.calculate(scheduler.name(), passes, schedule, start, end));
        }

        // --- Output ------------------------------------------------------------
        new MetricsTablePrinter().print(results, System.out);
        System.out.println("All schedules passed validation (no overlaps, turnaround respected).");
        System.out.println("High priority = priority >= " + assumptions.getInt("metrics.highPriorityMin")
                + ". Data volume = downlink rate x pass duration (see data/assumptions.properties).");
    }
}
