package orbitslot.chart;

import orbitslot.benchmark.BenchmarkRunner.PlanRow;
import orbitslot.benchmark.BenchmarkRunner.RepairRow;
import orbitslot.benchmark.BenchmarkRunner.Results;
import orbitslot.metrics.Metrics;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Turns metrics and benchmark results into PNG charts in one folder.
 * Each algorithm / repair strategy keeps the same colour in every chart,
 * whichever subset was run (see {@link #slotOf(String)}).
 */
public final class ChartWriter {

    /** One metric to chart: file name, title, unit, value format, how to read it. */
    private record MetricSpec(String file, String title, String unit, String format, ToDoubleFunction<Metrics> value) {
        /** Counts are whole numbers in a single run; only means over scenarios need a decimal. */
        String singleRunFormat() {
            return unit.equals("passes") ? "%.0f" : format;
        }
    }

    private static final List<MetricSpec> PLAN_METRICS = List.of(
            new MetricSpec("score", "Score (sum of priority x MB, higher is better)", "score", "%.0f", Metrics::score),
            new MetricSpec("data-mb", "Data downlinked", "MB", "%.0f", Metrics::downlinkedMB),
            new MetricSpec("data-pct", "Share of offered data downlinked", "%", "%.1f", Metrics::downlinkedPct),
            new MetricSpec("high-prio-missed", "High-priority passes missed (lower is better)", "passes", "%.1f",
                    m -> m.highPriorityMissed()),
            new MetricSpec("scheduled", "Passes scheduled", "passes", "%.0f", m -> m.scheduledPasses()),
            new MetricSpec("antenna-use", "Antenna utilisation", "%", "%.1f", Metrics::antennaUtilisationPct));

    private final Path dir;
    private final List<Path> written = new ArrayList<>();

    public ChartWriter(Path dir) {
        this.dir = dir;
    }

    /** Files written so far, in order. */
    public List<Path> written() {
        return List.copyOf(written);
    }

    public void write(String name, BufferedImage image) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(name + ".png");
        ImageIO.write(image, "png", file.toFile());
        written.add(file);
    }

    /** One bar chart per metric for a single run (one bar per algorithm or repair). */
    public void singleRunMetrics(String prefix, String subtitle, List<Metrics> metrics) throws IOException {
        BarChart chart = new BarChart();
        List<Integer> slots = metrics.stream().map(m -> slotOf(m.algorithm())).toList();
        for (MetricSpec spec : PLAN_METRICS) {
            List<BarChart.Bar> bars = metrics.stream().map(m -> {
                double v = spec.value().applyAsDouble(m);
                return new BarChart.Bar(m.algorithm(), v, v, v);
            }).toList();
            write(prefix + "-" + spec.file(), chart.render(spec.title(), subtitle, spec.unit(), bars,
                    spec.singleRunFormat(), slots));
        }
    }

    /** Charts over all benchmark scenarios: planning metrics, runtime, repair metrics, score gain per scenario. */
    public void benchmark(Results results, String subtitle) throws IOException {
        BarChart chart = new BarChart();

        Map<String, List<PlanRow>> plans = group(results.plans(), r -> r.metrics().algorithm());
        List<Integer> planSlots = slots(plans.keySet());
        for (MetricSpec spec : PLAN_METRICS) {
            write("bench-plan-" + spec.file(), chart.render(spec.title(), subtitle, spec.unit(),
                    bars(plans, r -> spec.value().applyAsDouble(r.metrics())), spec.format(), planSlots));
        }
        write("bench-plan-runtime", chart.render("Run time per scenario", subtitle, "ms",
                bars(plans, r -> r.runtimeMs()), "%.0f", planSlots));

        Map<String, List<RepairRow>> repairs = group(results.repairs(), r -> r.metrics().algorithm());
        List<Integer> repairSlots = slots(repairs.keySet());
        String repairSub = subtitle + "; same plan + disruptions per scenario";
        write("bench-repair-score", chart.render("Score after disruptions, by repair strategy", repairSub, "score",
                bars(repairs, r -> r.metrics().score()), "%.0f", repairSlots));
        write("bench-repair-high-prio-missed", chart.render("High-priority passes missed after disruptions",
                repairSub, "passes", bars(repairs, r -> r.metrics().highPriorityMissed()), "%.1f", repairSlots));
        write("bench-repair-plan-changes", chart.render("Plan changes made by the repair (lower = more stable)",
                repairSub, "changes", bars(repairs, r -> r.planChanges()), "%.1f", repairSlots));

        scoreGainPerScenario(plans, subtitle);
    }

    /** Score of every algorithm except the first (the baseline) relative to it, per scenario seed. */
    private void scoreGainPerScenario(Map<String, List<PlanRow>> plans, String subtitle) throws IOException {
        List<String> names = new ArrayList<>(plans.keySet());
        if (names.size() < 2) {
            return;
        }
        List<PlanRow> baseline = plans.get(names.get(0));
        List<String> seeds = baseline.stream().map(r -> Long.toString(r.seed())).toList();
        List<GroupedBarChart.Series> series = new ArrayList<>();
        for (int i = 1; i < names.size(); i++) {
            List<PlanRow> rows = plans.get(names.get(i));
            List<Double> gains = new ArrayList<>();
            for (int k = 0; k < rows.size(); k++) {
                double base = baseline.get(k).metrics().score();
                gains.add(base == 0 ? 0 : 100.0 * (rows.get(k).metrics().score() - base) / base);
            }
            series.add(new GroupedBarChart.Series(names.get(i), slotOf(names.get(i)), gains));
        }
        write("bench-plan-score-gain", new GroupedBarChart().render(
                "Score gain over " + names.get(0) + " in each scenario", subtitle, "% vs " + names.get(0),
                "scenario seed", seeds, series));
    }

    private static <T> List<BarChart.Bar> bars(Map<String, List<T>> groups, ToDoubleFunction<T> f) {
        List<BarChart.Bar> bars = new ArrayList<>();
        for (Map.Entry<String, List<T>> e : groups.entrySet()) {
            DoubleSummaryStatistics s = e.getValue().stream().mapToDouble(f).summaryStatistics();
            bars.add(new BarChart.Bar(e.getKey(), s.getAverage(), s.getMin(), s.getMax()));
        }
        return bars;
    }

    private static <T> Map<String, List<T>> group(List<T> rows, java.util.function.Function<T, String> key) {
        Map<String, List<T>> map = new LinkedHashMap<>();
        rows.forEach(r -> map.computeIfAbsent(key.apply(r), k -> new ArrayList<>()).add(r));
        return map;
    }

    /** Fixed colour slots by name; algorithms and repairs never share a chart, so their slots may overlap. */
    private static final List<String> ALGORITHM_ORDER = List.of("fcfs", "greedy", "sa");
    private static final List<String> REPAIR_ORDER = List.of("plan", "none", "rolling", "full");

    static int slotOf(String name) {
        int i = ALGORITHM_ORDER.indexOf(name);
        if (i < 0) {
            i = REPAIR_ORDER.indexOf(name);
        }
        return i >= 0 ? i : 4 + Math.floorMod(name.hashCode(), 4);   // unknown name: still stable
    }

    private static List<Integer> slots(java.util.Collection<String> names) {
        return names.stream().map(ChartWriter::slotOf).toList();
    }
}
