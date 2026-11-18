package orbitslot.chart;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Grouped bar chart: one group per category (e.g. scenario seed), one bar per
 * series in each group (e.g. algorithm). Used to show a metric per scenario.
 */
public final class GroupedBarChart {

    /** One series: a name, its colour slot and one value per category. */
    public record Series(String name, int slot, List<Double> values) { }

    private static final int HEIGHT = 460;
    private static final int LEFT = 78, RIGHT = 28, TOP = 84, BOTTOM = 64;

    /** @param minWidth the image is at least this wide (wider if there are many categories) */
    public BufferedImage render(String title, String subtitle, String unit, String categoryAxis,
                                List<String> categories, List<Series> series, int minWidth) {
        int width = Math.max(minWidth, LEFT + RIGHT + categories.size() * (series.size() * 14 + 16));
        BufferedImage img = Canvas.image(width, HEIGHT);
        Graphics2D g = Canvas.graphics(img);
        Canvas.title(g, title, subtitle, LEFT - 50);

        double lo = 0, hi = 0;
        for (Series s : series) {
            for (double v : s.values()) {
                lo = Math.min(lo, v);
                hi = Math.max(hi, v);
            }
        }
        List<Double> ticks = Canvas.ticks(lo, hi * 1.05, 5);
        double axisMin = ticks.get(0), axisMax = ticks.get(ticks.size() - 1);
        int plotTop = TOP, plotBottom = HEIGHT - BOTTOM, plotLeft = LEFT, plotRight = width - RIGHT;
        DoubleUnaryOperator y = v -> plotBottom - (v - axisMin) / (axisMax - axisMin) * (plotBottom - plotTop);

        g.setFont(Canvas.SMALL);
        for (double t : ticks) {
            int ty = (int) Math.round(y.applyAsDouble(t));
            g.setColor(t == 0 ? Canvas.AXIS : Canvas.GRID);
            Canvas.stroke(g, 1);
            g.drawLine(plotLeft, ty, plotRight, ty);
            g.setColor(Canvas.TEXT_SECONDARY);
            Canvas.right(g, Canvas.compact(t), plotLeft - 8, ty + 4);
        }
        g.drawString(unit, plotLeft - 50, plotTop - 12);

        // legend, top right
        int lx = plotRight - series.stream().mapToInt(s -> 40 + g.getFontMetrics().stringWidth(s.name())).sum();
        for (Series s : series) {
            lx = Canvas.legendEntry(g, lx, 62, Canvas.series(s.slot()), s.name());
        }

        double group = (double) (plotRight - plotLeft) / categories.size();
        double barWidth = Math.min(22, (group - 10) / series.size() - 2);
        double base = y.applyAsDouble(0);
        for (int c = 0; c < categories.size(); c++) {
            double groupLeft = plotLeft + group * c + (group - series.size() * (barWidth + 2)) / 2;
            for (int s = 0; s < series.size(); s++) {
                double x = groupLeft + s * (barWidth + 2);    // 2 px surface gap between bars
                Canvas.bar(g, x, barWidth, base, y.applyAsDouble(series.get(s).values().get(c)),
                        Canvas.series(series.get(s).slot()));
            }
            g.setColor(Canvas.TEXT_SECONDARY);
            g.setFont(Canvas.SMALL);
            Canvas.centred(g, categories.get(c), plotLeft + group * (c + 0.5), plotBottom + 18);
        }
        g.setColor(Canvas.TEXT_SECONDARY);
        Canvas.centred(g, categoryAxis, (plotLeft + plotRight) / 2.0, plotBottom + 42);
        g.dispose();
        return img;
    }
}
