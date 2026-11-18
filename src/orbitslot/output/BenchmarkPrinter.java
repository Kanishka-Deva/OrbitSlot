package orbitslot.output;

import orbitslot.benchmark.BenchmarkRunner.PlanRow;
import orbitslot.benchmark.BenchmarkRunner.RepairRow;
import orbitslot.benchmark.BenchmarkRunner.Results;
import orbitslot.metrics.Metrics;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** Prints benchmark summaries (mean, min-max per algorithm) and writes the raw rows as CSV. */
public final class BenchmarkPrinter {

    public void printPlans(List<PlanRow> rows, PrintStream out) {
        out.println("=== Planning: mean [min - max] over scenarios ===");
        String f = "%-8s  %22s  %20s  %20s  %18s  %10s%n";
        out.printf(f, "Algo", "Score", "Data MB", "Data %", "High-prio missed", "Time ms");
        for (Map.Entry<String, List<PlanRow>> e : byName(rows, r -> r.metrics().algorithm()).entrySet()) {
            List<PlanRow> g = e.getValue();
            out.printf(Locale.ROOT, f, e.getKey(),
                    stat(g, r -> r.metrics().score(), "%.0f"),
                    stat(g, r -> r.metrics().downlinkedMB(), "%.0f"),
                    stat(g, r -> r.metrics().downlinkedPct(), "%.1f"),
                    stat(g, r -> r.metrics().highPriorityMissed(), "%.1f"),
                    String.format(Locale.ROOT, "%.0f", mean(g, PlanRow::runtimeMs)));
        }
    }

    public void printRepairs(List<RepairRow> rows, PrintStream out) {
        out.println("=== Disruptions: mean [min - max] over scenarios (same plan + disruptions per scenario) ===");
        String f = "%-8s  %20s  %20s  %18s  %13s  %18s%n";
        out.printf(f, "Repair", "Score", "Data MB", "High-prio missed", "Forced losses", "Plan changes");
        for (Map.Entry<String, List<RepairRow>> e : byName(rows, r -> r.metrics().algorithm()).entrySet()) {
            List<RepairRow> g = e.getValue();
            out.printf(Locale.ROOT, f, e.getKey(),
                    stat(g, r -> r.metrics().score(), "%.0f"),
                    stat(g, r -> r.metrics().downlinkedMB(), "%.0f"),
                    stat(g, r -> r.metrics().highPriorityMissed(), "%.1f"),
                    String.format(Locale.ROOT, "%.1f", mean(g, RepairRow::forcedLosses)),
                    stat(g, RepairRow::planChanges, "%.1f"));
        }
    }

    /** One line per scheduler or repair run, for spreadsheets and charts. */
    public void writeCsv(Results results, Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("kind,seed,name,passes,scheduled,dropped,data_mb,data_pct,score,high_prio_missed,"
                + "high_prio_total,antenna_use_pct,runtime_ms,forced_losses,plan_changes");
        for (PlanRow r : results.plans()) {
            lines.add(csv("plan", r.seed(), r.metrics(), r.runtimeMs(), "", ""));
        }
        for (RepairRow r : results.repairs()) {
            lines.add(csv("repair", r.seed(), r.metrics(), "", r.forcedLosses(), r.planChanges()));
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static String csv(String kind, long seed, Metrics m, Object runtime, Object forced, Object changes) {
        return String.format(Locale.ROOT, "%s,%d,%s,%d,%d,%d,%.2f,%.2f,%.1f,%d,%d,%.2f,%s,%s,%s",
                kind, seed, m.algorithm(), m.totalPasses(), m.scheduledPasses(), m.droppedPasses(),
                m.downlinkedMB(), m.downlinkedPct(), m.score(), m.highPriorityMissed(), m.highPriorityPasses(),
                m.antennaUtilisationPct(), runtime, forced, changes);
    }

    private static <T> Map<String, List<T>> byName(List<T> rows, java.util.function.Function<T, String> name) {
        Map<String, List<T>> map = new LinkedHashMap<>();
        for (T r : rows) {
            map.computeIfAbsent(name.apply(r), k -> new ArrayList<>()).add(r);
        }
        return map;
    }

    private static <T> String stat(List<T> rows, ToDoubleFunction<T> f, String fmt) {
        DoubleSummaryStatistics s = rows.stream().mapToDouble(f).summaryStatistics();
        return String.format(Locale.ROOT, fmt + " [" + fmt + " - " + fmt + "]", s.getAverage(), s.getMin(), s.getMax());
    }

    private static <T> double mean(List<T> rows, ToDoubleFunction<T> f) {
        return rows.stream().mapToDouble(f).average().orElse(0);
    }
}
