package orbitslot.output;

import orbitslot.metrics.Metrics;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;

/** Prints the metrics of one or more algorithms side by side. */
public final class MetricsTablePrinter {

    private static final String ROW = "%-10s  %15s  %7s  %12s  %10s  %13s  %19s%n";

    public void print(List<Metrics> rows, PrintStream out) {
        out.println("=== Metrics ===");
        out.printf(ROW, "Algorithm", "Scheduled", "Dropped", "Data MB", "Data %", "Antenna use %", "High-prio missed");
        for (Metrics m : rows) {
            out.printf(Locale.ROOT, ROW,
                    m.algorithm(),
                    m.scheduledPasses() + " / " + m.totalPasses(),
                    m.droppedPasses(),
                    String.format(Locale.ROOT, "%.1f", m.downlinkedMB()),
                    String.format(Locale.ROOT, "%.1f", m.downlinkedPct()),
                    String.format(Locale.ROOT, "%.1f", m.antennaUtilisationPct()),
                    m.highPriorityMissed() + " / " + m.highPriorityPasses());
        }
    }
}
