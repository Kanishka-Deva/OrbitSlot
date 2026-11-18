package orbitslot.output;

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
 * Console view of one schedule: a table per antenna, an ASCII timeline per
 * antenna, and the dropped passes with their reasons.
 */
public final class SchedulePrinter {

    private static final int TIMELINE_WIDTH = 96;
    private static final String ROW = "  %-19s  %-8s  %5s  %-24s  %-7s  %4s  %9s%n";

    private final DateTimeFormatter timeFormat;
    private final DateTimeFormatter shortTime;
    private final String zoneLabel;
    private final int rowLimit;

    /** @param rowLimit maximum rows per list; 0 or less means all */
    public SchedulePrinter(ZoneId zone, int rowLimit) {
        this.timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone);
        this.shortTime = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone);
        this.zoneLabel = zone.getId();
        this.rowLimit = rowLimit;
    }

    public void print(String algorithm, Schedule schedule, Instant start, Instant end, PrintStream out) {
        out.println("=== Schedule: " + algorithm + " ===");
        for (Antenna antenna : schedule.station().antennas()) {
            List<Assignment> list = schedule.assignmentsFor(antenna);
            out.println();
            out.println(antenna + " - " + list.size() + " passes");
            out.printf(ROW, "AOS (" + zoneLabel + ")", "LOS", "min", "Satellite", "Type", "Prio", "Data MB");
            int shown = limit(list.size());
            for (int i = 0; i < shown; i++) {
                printRow(list.get(i).pass(), out);
            }
            more(list.size() - shown, "passes", out);
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
        more(dropped.size() - shown, "dropped passes", out);
        out.println();
    }

    private void printRow(Pass p, PrintStream out) {
        out.printf(Locale.ROOT, ROW,
                timeFormat.format(p.aos()),
                shortTime.format(p.los()),
                String.format(Locale.ROOT, "%.1f", p.duration().toSeconds() / 60.0),
                truncate(p.satellite().name(), 24),
                p.satellite().typeName(),
                p.satellite().priority(),
                String.format(Locale.ROOT, "%.2f", p.dataVolumeMB()));
    }

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

    private static void more(int hidden, String what, PrintStream out) {
        if (hidden > 0) {
            out.printf("  ... %d more %s (use --all to show everything)%n", hidden, what);
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }
}
