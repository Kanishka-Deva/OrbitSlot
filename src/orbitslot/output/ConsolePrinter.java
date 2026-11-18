package orbitslot.output;

import orbitslot.metrics.Metrics;
import orbitslot.model.Antenna;
import orbitslot.model.Assignment;
import orbitslot.model.DroppedPass;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;

import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * All fixed-width console tables: the input pass list, one schedule (a table
 * and an ASCII timeline per antenna, plus the dropped passes with their
 * reasons), and metrics of several schedules side by side.
 */
public final class ConsolePrinter {

    private static final int TIMELINE_WIDTH = 96;
    private static final String PASS_ROW = "%4s  %-19s  %-8s  %5s  %-24s  %-7s  %4s  %5s  %7s  %9s%n";
    private static final String SCHEDULE_ROW = "  %-19s  %-8s  %5s  %-24s  %-7s  %4s  %9s%n";
    private static final String METRICS_ROW = "%-10s  %11s  %7s  %10s  %7s  %9s  %11s  %16s%n";

    private final DateTimeFormatter timeFormat;
    private final DateTimeFormatter shortTime;
    private final String zoneLabel;
    private final int rowLimit;
    private final PrintStream out;

    /** @param rowLimit maximum rows per list; 0 or less means all */
    public ConsolePrinter(ZoneId zone, int rowLimit, PrintStream out) {
        this.timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone);
        this.shortTime = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone);
        this.zoneLabel = zone.getId();
        this.rowLimit = rowLimit;
        this.out = out;
    }

    // --- input passes ----------------------------------------------------------

    public void printPasses(List<Pass> passes) {
        out.printf(PASS_ROW, "#", "AOS (" + zoneLabel + ")", "LOS", "min", "Satellite", "Type",
                "Prio", "MaxEl", "Az", "Data MB");
        out.println("-".repeat(112));

        int shown = limit(passes.size());
        for (int i = 0; i < shown; i++) {
            Pass p = passes.get(i);
            out.printf(Locale.ROOT, PASS_ROW,
                    i + 1,
                    timeFormat.format(p.aos()),
                    shortTime.format(p.los()),
                    minutes(p),
                    truncate(p.satellite().name(), 24),
                    p.satellite().typeName(),
                    p.satellite().priority(),
                    String.format(Locale.ROOT, "%.1f", p.maxElevationDeg()),
                    String.format(Locale.ROOT, "%03.0f>%03.0f", p.aosAzimuthDeg(), p.losAzimuthDeg()),
                    String.format(Locale.ROOT, "%.2f", p.dataVolumeMB()));
        }
        more(passes.size() - shown, "passes");
    }

    // --- one schedule ----------------------------------------------------------

    public void printSchedule(String title, Schedule schedule, Instant start, Instant end) {
        out.println("=== Schedule: " + title + " ===");
        for (Antenna antenna : schedule.station().antennas()) {
            List<Assignment> list = schedule.assignmentsFor(antenna);
            out.println();
            out.println(antenna + " - " + list.size() + " passes");
            out.printf(SCHEDULE_ROW, "AOS (" + zoneLabel + ")", "LOS", "min", "Satellite", "Type", "Prio", "Data MB");
            int shown = limit(list.size());
            for (int i = 0; i < shown; i++) {
                Pass p = list.get(i).pass();
                out.printf(Locale.ROOT, SCHEDULE_ROW,
                        timeFormat.format(p.aos()),
                        shortTime.format(p.los()),
                        minutes(p),
                        truncate(p.satellite().name(), 24),
                        p.satellite().typeName(),
                        p.satellite().priority(),
                        String.format(Locale.ROOT, "%.2f", p.dataVolumeMB()));
            }
            more(list.size() - shown, "passes");
        }

        out.println();
        out.println("Timeline (" + zoneLabel + ", one character = "
                + Duration.between(start, end).dividedBy(TIMELINE_WIDTH).toMinutes() + " min, # = tracking)");
        out.println("        " + ruler(start, end));
        for (Antenna antenna : schedule.station().antennas()) {
            out.printf("  %-5s |%s|%n", antenna.label(), timeline(schedule.assignmentsFor(antenna), start, end));
        }

        List<DroppedPass> dropped = schedule.dropped();
        out.println();
        out.println("Dropped passes: " + dropped.size());
        int shown = limit(dropped.size());
        for (int i = 0; i < shown; i++) {
            DroppedPass d = dropped.get(i);
            out.printf(Locale.ROOT, "  %s  %-24s  prio %2d  %s%n", timeFormat.format(d.pass().aos()),
                    truncate(d.pass().satellite().name(), 24), d.pass().satellite().priority(), d.reason());
        }
        more(dropped.size() - shown, "dropped passes");
        out.println();
    }

    // --- metrics ---------------------------------------------------------------

    public void printMetrics(String title, List<Metrics> rows) {
        out.println("=== " + title + " ===");
        out.printf(METRICS_ROW, "Name", "Scheduled", "Dropped", "Data MB", "Data %", "Ant. use%", "Score",
                "High-prio missed");
        for (Metrics m : rows) {
            out.printf(Locale.ROOT, METRICS_ROW,
                    m.algorithm(),
                    m.scheduledPasses() + "/" + m.totalPasses(),
                    m.droppedPasses(),
                    String.format(Locale.ROOT, "%.1f", m.downlinkedMB()),
                    String.format(Locale.ROOT, "%.1f", m.downlinkedPct()),
                    String.format(Locale.ROOT, "%.1f", m.antennaUtilisationPct()),
                    String.format(Locale.ROOT, "%.0f", m.score()),
                    m.highPriorityMissed() + " / " + m.highPriorityPasses());
        }
    }

    // --- helpers ---------------------------------------------------------------

    /** One character per time slice; '#' if any tracked pass touches it. */
    private static String timeline(List<Assignment> list, Instant start, Instant end) {
        long total = Duration.between(start, end).toSeconds();
        char[] line = ".".repeat(TIMELINE_WIDTH).toCharArray();
        for (Assignment a : list) {
            long from = Duration.between(start, a.pass().aos()).toSeconds();
            long to = Duration.between(start, a.pass().los()).toSeconds();
            int first = (int) Math.max(0, from * TIMELINE_WIDTH / total);
            int last = (int) Math.min(TIMELINE_WIDTH - 1, (to - 1) * TIMELINE_WIDTH / total);
            for (int i = first; i <= last; i++) {
                line[i] = '#';
            }
        }
        return new String(line);
    }

    /** Hour labels above the timeline, e.g. "00   06   12   18". */
    private String ruler(Instant start, Instant end) {
        long hours = Duration.between(start, end).toHours();
        long step = Math.max(1, hours / 4);
        char[] line = " ".repeat(TIMELINE_WIDTH + 2).toCharArray();
        DateTimeFormatter hourFormat = DateTimeFormatter.ofPattern("HH").withZone(timeFormat.getZone());
        for (long h = 0; h < hours; h += step) {
            int pos = 1 + (int) (h * TIMELINE_WIDTH / hours);
            String label = hourFormat.format(start.plusSeconds(h * 3600));
            for (int i = 0; i < label.length() && pos + i < line.length; i++) {
                line[pos + i] = label.charAt(i);
            }
        }
        return new String(line).stripTrailing();
    }

    private int limit(int size) {
        return rowLimit > 0 ? Math.min(rowLimit, size) : size;
    }

    private void more(int hidden, String what) {
        if (hidden > 0) {
            out.printf("  ... %d more %s (use --all to show everything)%n", hidden, what);
        }
    }

    private static String minutes(Pass p) {
        return String.format(Locale.ROOT, "%.1f", p.duration().toSeconds() / 60.0);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }
}
