package orbitslot.output;

import orbitslot.model.Pass;

import java.io.PrintStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Prints passes as a fixed-width console table. */
public final class PassTablePrinter {

    private static final String ROW = "%4s  %-19s  %-8s  %5s  %-24s  %-7s  %4s  %5s  %7s  %9s%n";

    private final DateTimeFormatter timeFormat;
    private final DateTimeFormatter shortTime;
    private final String zoneLabel;

    public PassTablePrinter(ZoneId zone) {
        this.timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone);
        this.shortTime = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone);
        this.zoneLabel = zone.getId();
    }

    /** @param limit maximum rows to print; 0 or less means all */
    public void print(List<Pass> passes, int limit, PrintStream out) {
        out.printf(ROW, "#", "AOS (" + zoneLabel + ")", "LOS", "min", "Satellite", "Type",
                "Prio", "MaxEl", "Az", "Data MB");
        out.println("-".repeat(112));

        int shown = (limit > 0) ? Math.min(limit, passes.size()) : passes.size();
        for (int i = 0; i < shown; i++) {
            Pass p = passes.get(i);
            out.printf(Locale.ROOT, ROW,
                    i + 1,
                    timeFormat.format(p.aos()),
                    shortTime.format(p.los()),
                    String.format(Locale.ROOT, "%.1f", p.duration().toSeconds() / 60.0),
                    truncate(p.satellite().name(), 24),
                    p.satellite().typeName(),
                    p.satellite().priority(),
                    String.format(Locale.ROOT, "%.1f", p.maxElevationDeg()),
                    String.format(Locale.ROOT, "%03.0f>%03.0f", p.aosAzimuthDeg(), p.losAzimuthDeg()),
                    String.format(Locale.ROOT, "%.2f", p.dataVolumeMB()));
        }
        if (shown < passes.size()) {
            out.printf("... %d more passes (use --all to show every pass)%n", passes.size() - shown);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }
}
