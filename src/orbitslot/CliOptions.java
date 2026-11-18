package orbitslot;

/** Command-line options; see {@link #USAGE}. */
public record CliOptions(String source, String algo, String station, boolean showPasses,
                         boolean showAll, boolean help) {

    public static final String USAGE = """
            Usage: run.ps1 [options]
              --source sim             simulated passes (default; real data comes in Stage 3)
              --algo fcfs|greedy|all   which scheduler(s) to run (default all)
              --station ID             station id from data/stations.csv (default from assumptions)
              --passes                 also print the input pass list
              --all                    print every row (no limits)
              --help                   show this text""";

    public static CliOptions parse(String[] args) {
        String source = "sim";
        String algo = "all";
        String station = null;
        boolean showPasses = false, showAll = false, help = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--source" -> source = value(args, ++i, "--source");
                case "--algo" -> algo = value(args, ++i, "--algo");
                case "--station" -> station = value(args, ++i, "--station");
                case "--passes" -> showPasses = true;
                case "--all" -> showAll = true;
                case "--help", "-h" -> help = true;
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        if (!source.equals("sim")) {
            throw new IllegalArgumentException("--source must be sim, got " + source);
        }
        return new CliOptions(source, algo, station, showPasses, showAll, help);
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length || args[i].startsWith("--")) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[i];
    }
}
