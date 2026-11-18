package orbitslot.chart;

import orbitslot.benchmark.BenchmarkRunner.PlanRow;
import orbitslot.benchmark.BenchmarkRunner.RepairRow;
import orbitslot.benchmark.BenchmarkRunner.Results;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Writes the comparison charts as PNG files into one folder, plus a summary
 * sheet that puts them all on one image. Only charts that compare algorithms
 * (or repair strategies) with each other are made.
 * Each algorithm / repair keeps the same colour in every chart.
 */
public final class ChartWriter {

    private static final int GAP = 16, MARGIN = 24, HEADER = 84;
    private static final int COLUMNS = 3;
    /** Width of the summary content area: three bar charts side by side. */
    public static final int SUMMARY_CONTENT_WIDTH = COLUMNS * BarChart.WIDTH + (COLUMNS - 1) * GAP;

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

    /**
     * The benchmark charts (one PNG each) and {@code summary.png}, which puts
     * them in a grid with the given Gantt timeline at the bottom.
     *
     * @param gantt full-day schedules of all algorithms, or null to leave it out
     */
    public void benchmark(Results results, String subtitle, BufferedImage gantt) throws IOException {
        BarChart bar = new BarChart();
        Map<String, List<PlanRow>> plans = group(results.plans(), r -> r.metrics().algorithm());
        Map<String, List<RepairRow>> repairs = group(results.repairs(), r -> r.metrics().algorithm());
        List<Integer> planSlots = slots(plans.keySet());
        List<Integer> repairSlots = slots(repairs.keySet());
        String repairSub = "Disruptions replayed on the greedy plan; same disruptions for every repair";

        Map<String, BufferedImage> grid = new LinkedHashMap<>();
        grid.put("bench-score", bar.render("Score (priority x MB, higher is better)", subtitle, "score",
                bars(plans, r -> r.metrics().score()), "%.0f", planSlots));
        grid.put("bench-data", bar.render("Share of offered data downlinked", subtitle, "%",
                bars(plans, r -> r.metrics().downlinkedPct()), "%.1f", planSlots));
        grid.put("bench-high-prio-missed", bar.render("High-priority passes missed (lower is better)", subtitle,
                "passes", bars(plans, r -> r.metrics().highPriorityMissed()), "%.1f", planSlots));
        grid.put("bench-runtime", bar.render("Run time per scenario", subtitle, "ms",
                bars(plans, r -> r.runtimeMs()), "%.0f", planSlots));
        grid.put("bench-repair-score", bar.render("Repair: score after disruptions", repairSub, "score",
                bars(repairs, r -> r.metrics().score()), "%.0f", repairSlots));
        grid.put("bench-repair-changes", bar.render("Repair: plan changes (lower = more stable)", repairSub,
                "changes", bars(repairs, r -> r.planChanges()), "%.1f", repairSlots));
        BufferedImage gain = scoreGainPerScenario(plans, subtitle);

        for (Map.Entry<String, BufferedImage> e : grid.entrySet()) {
            write(e.getKey(), e.getValue());
        }
        if (gain != null) {
            write("bench-score-gain", gain);
        }
        write("summary", summary(subtitle, new ArrayList<>(grid.values()), gain, gantt));
    }

    /** Header, the bar charts in rows of three, then the wide charts below each other. */
    private static BufferedImage summary(String subtitle, List<BufferedImage> cells, BufferedImage gain,
                                         BufferedImage gantt) {
        List<BufferedImage> wide = new ArrayList<>();
        if (gain != null) {
            wide.add(gain);
        }
        if (gantt != null) {
            wide.add(gantt);
        }
        int rows = (cells.size() + COLUMNS - 1) / COLUMNS;
        int contentWidth = Math.max(SUMMARY_CONTENT_WIDTH,
                wide.stream().mapToInt(BufferedImage::getWidth).max().orElse(0));
        int height = HEADER + rows * (BarChart.HEIGHT + GAP)
                + wide.stream().mapToInt(i -> i.getHeight() + GAP).sum() + MARGIN;

        BufferedImage img = Canvas.image(MARGIN * 2 + contentWidth, height);
        Graphics2D g = Canvas.graphics(img);
        g.setColor(Canvas.TEXT);
        g.setFont(Canvas.TITLE.deriveFont(24f));
        g.drawString("OrbitSlot: comparison of scheduling algorithms and repair strategies", MARGIN, 40);
        g.setColor(Canvas.TEXT_SECONDARY);
        g.setFont(Canvas.BODY);
        g.drawString(subtitle + ". All numbers rest on data/assumptions.properties.", MARGIN, 66);

        int y = HEADER;
        for (int i = 0; i < cells.size(); i++) {
            int x = MARGIN + (i % COLUMNS) * (BarChart.WIDTH + GAP);
            panel(g, cells.get(i), x, y);
            if (i % COLUMNS == COLUMNS - 1 || i == cells.size() - 1) {
                y += BarChart.HEIGHT + GAP;
            }
        }
        for (BufferedImage w : wide) {
            panel(g, w, MARGIN, y);
            y += w.getHeight() + GAP;
        }
        g.dispose();
        return img;
    }

    /** Draws one chart with a thin frame, so the panels read as separate charts. */
    private static void panel(Graphics2D g, BufferedImage chart, int x, int y) {
        g.drawImage(chart, x, y, null);
        g.setColor(Canvas.GRID);
        Canvas.stroke(g, 1);
        g.drawRect(x, y, chart.getWidth() - 1, chart.getHeight() - 1);
    }

    /** Score of every algorithm except the first (the baseline) relative to it, per scenario seed. */
    private static BufferedImage scoreGainPerScenario(Map<String, List<PlanRow>> plans, String subtitle) {
        List<String> names = new ArrayList<>(plans.keySet());
        if (names.size() < 2) {
            return null;
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
        return new GroupedBarChart().render("Score gain over " + names.get(0) + " in each scenario", subtitle,
                "% vs " + names.get(0), "scenario seed", seeds, series, SUMMARY_CONTENT_WIDTH);
    }

    private static <T> List<BarChart.Bar> bars(Map<String, List<T>> groups, ToDoubleFunction<T> f) {
        List<BarChart.Bar> bars = new ArrayList<>();
        for (Map.Entry<String, List<T>> e : groups.entrySet()) {
            DoubleSummaryStatistics s = e.getValue().stream().mapToDouble(f).summaryStatistics();
            bars.add(new BarChart.Bar(e.getKey(), s.getAverage(), s.getMin(), s.getMax()));
        }
        return bars;
    }

    private static <T> Map<String, List<T>> group(List<T> rows, Function<T, String> key) {
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

    private static List<Integer> slots(Collection<String> names) {
        return names.stream().map(ChartWriter::slotOf).toList();
    }
}
