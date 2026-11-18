package orbitslot;

import orbitslot.catalog.PriorityAssigner;
import orbitslot.catalog.SatelliteCatalog;
import orbitslot.catalog.SatelliteFactory;
import orbitslot.io.Assumptions;
import orbitslot.io.AssumptionsLoader;
import orbitslot.io.PriorityOverridesLoader;
import orbitslot.io.StationLoader;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Satellite;
import orbitslot.output.PassTablePrinter;
import orbitslot.scenario.PassSource;
import orbitslot.scenario.SimulatedPassSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Entry point.
 * Stage 1: load assumptions, station and satellites, generate simulated
 * passes and print them as a sorted table.
 *
 * Options: --all   print every pass instead of the first 50
 */
public final class Main {

    private static final Path DATA = Path.of("data");
    private static final int DEFAULT_ROW_LIMIT = 50;

    public static void main(String[] args) throws Exception {
        boolean showAll = Arrays.asList(args).contains("--all");

        Assumptions assumptions = new AssumptionsLoader().load(DATA.resolve("assumptions.properties"));
        GroundStation station = new StationLoader().load(DATA.resolve("stations.csv"), assumptions);

        Map<Integer, Integer> overrides = new PriorityOverridesLoader().load(DATA.resolve("priorities.csv"));
        SatelliteFactory factory = new SatelliteFactory(assumptions, new PriorityAssigner(assumptions, overrides));
        SatelliteCatalog catalog = SatelliteCatalog.load(DATA.resolve("celestrak"), assumptions, factory);

        Instant start = assumptions.getInstant("schedule.start");
        Instant end = start.plusSeconds(3600L * assumptions.getInt("schedule.horizonHours"));
        PassSource source = new SimulatedPassSource(assumptions);
        List<Pass> passes = source.passes(catalog.satellites(), station, start, end);

        System.out.println("OrbitSlot - Stage 1 (simulated passes)");
        System.out.println("Station : " + station);
        System.out.println("Window  : " + start + " to " + end);
        System.out.println("Source  : " + source.description());
        System.out.println("Satellites: " + catalog.satellites().size() + " loaded " + countByType(catalog.satellites())
                + ", " + catalog.excluded().size() + " excluded, " + overrides.size() + " priority override(s)");
        for (SatelliteCatalog.Excluded x : catalog.excluded()) {
            System.out.printf("  excluded: %-24s %6d  %s%n", x.name(), x.noradId(), x.reason());
        }
        System.out.println();

        new PassTablePrinter(assumptions.getZone("display.timezone"))
                .print(passes, showAll ? 0 : DEFAULT_ROW_LIMIT, System.out);

        double totalMB = passes.stream().mapToDouble(Pass::dataVolumeMB).sum();
        System.out.printf("%nTotal: %d passes, %.1f MB if every pass could be tracked%n", passes.size(), totalMB);
    }

    private static Map<String, Long> countByType(List<Satellite> satellites) {
        return satellites.stream()
                .collect(Collectors.groupingBy(Satellite::typeName, TreeMap::new, Collectors.counting()));
    }
}
