package orbitslot;

/** Command-line options; see {@link #USAGE}. */
public record CliOptions(String mode, String source, String algo, String repair, String station,
                         Integer scenarios, boolean showPasses, boolean showAll, boolean help) {

    public static final String USAGE = """
            Usage: run.ps1 [mode] [options]
            Modes:
              (none)                       plan with the chosen algorithm(s) and print schedules + metrics
              --disrupt                    plan, then replay seeded disruptions with alerts and repair
              --benchmark                  compare algorithms and repairs over many seeded scenarios
            Options:
              --source celestrak|sim       real predicted passes (default) or simulated ones
              --algo fcfs|greedy|sa|all    scheduler(s) (default all; --disrupt plans with greedy unless set)
              --repair none|rolling|full|all   repair strategy for --disrupt (default rolling)
              --scenarios N                number of benchmark scenarios (default bench.scenarios)
              --station ID                 station id from data/stations.csv (default from assumptions)
              --passes                     also print the input pass list
              --all                        print every row (no limits)
              --help                       show this text""";

    public static CliOptions parse(String[] args) {
        String mode = "plan";
        String source = "celestrak";
        String algo = "all";
        String repair = "rolling";
        String station = null;
        Integer scenarios = null;
        boolean showPasses = false, showAll = false, help = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--disrupt" -> mode = "disrupt";
                case "--benchmark" -> mode = "benchmark";
                case "--source" -> source = value(args, ++i, "--source");
                case "--algo" -> algo = value(args, ++i, "--algo");
                case "--repair" -> repair = value(args, ++i, "--repair");
                case "--station" -> station = value(args, ++i, "--station");
                case "--scenarios" -> scenarios = positiveInt(value(args, ++i, "--scenarios"));
                case "--passes" -> showPasses = true;
                case "--all" -> showAll = true;
                case "--help", "-h" -> help = true;
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        if (!source.equals("celestrak") && !source.equals("sim")) {
            throw new IllegalArgumentException("--source must be celestrak or sim, got " + source);
        }
        return new CliOptions(mode, source, algo, repair, station, scenarios, showPasses, showAll, help);
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length || args[i].startsWith("--")) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[i];
    }

    private static int positiveInt(String s) {
        try {
            int n = Integer.parseInt(s);
            if (n > 0) {
                return n;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw new IllegalArgumentException("--scenarios needs a positive whole number, got " + s);
    }
}
