package orbitslot.output;

import orbitslot.metrics.Metrics;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;

/** Prints the metrics of one or more schedules side by side. */
public final class MetricsTablePrinter {

    private static final String ROW = "%-10s  %11s  %7s  %10s  %7s  %9s  %11s  %16s%n";

    public void print(String title, List<Metrics> rows, PrintStream out) {
        out.println("=== " + title + " ===");
        out.printf(ROW, "Name", "Scheduled", "Dropped", "Data MB", "Data %", "Ant. use%", "Score", "High-prio missed");
        for (Metrics m : rows) {
            out.printf(Locale.ROOT, ROW,
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
}
