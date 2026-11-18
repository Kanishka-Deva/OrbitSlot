package orbitslot.chart;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;

/**
 * Bar chart of one metric: one bar per algorithm (or repair strategy).
 * The bar is the mean; a whisker shows min - max over the scenarios when
 * they differ. The value is printed above each bar (there are only a few).
 */
public final class BarChart {

    /** One bar. For a single run, min = max = value and no whisker is drawn. */
    public record Bar(String label, double value, double min, double max) { }

    static final int WIDTH = 760;
    static final int HEIGHT = 480;
    private static final int LEFT = 78, RIGHT = 28, TOP = 98, BOTTOM = 58;

    /**
     * @param valueFormat printf format for the value labels, e.g. "%.0f"
     * @param slots       colour slot per bar, so an algorithm keeps its colour across charts
     */
    public BufferedImage render(String title, String subtitle, String unit, List<Bar> bars,
                                String valueFormat, List<Integer> slots) {
        BufferedImage img = Canvas.image(WIDTH, HEIGHT);
        Graphics2D g = Canvas.graphics(img);
        Canvas.title(g, title, subtitle, LEFT - 50);

        double lo = 0, hi = 0;
        for (Bar b : bars) {
            lo = Math.min(lo, Math.min(b.min(), b.value()));
            hi = Math.max(hi, Math.max(b.max(), b.value()));
        }
        List<Double> ticks = Canvas.ticks(lo, hi * 1.08, 5);
        double axisMin = ticks.get(0), axisMax = ticks.get(ticks.size() - 1);
        int plotTop = TOP, plotBottom = HEIGHT - BOTTOM, plotLeft = LEFT, plotRight = WIDTH - RIGHT;
        java.util.function.DoubleUnaryOperator y =
                v -> plotBottom - (v - axisMin) / (axisMax - axisMin) * (plotBottom - plotTop);

        // grid + y labels
        g.setFont(Canvas.SMALL);
        for (double t : ticks) {
            int ty = (int) Math.round(y.applyAsDouble(t));
            g.setColor(t == 0 ? Canvas.AXIS : Canvas.GRID);
            Canvas.stroke(g, 1);
            g.drawLine(plotLeft, ty, plotRight, ty);
            g.setColor(Canvas.TEXT_SECONDARY);
            Canvas.right(g, Canvas.compact(t), plotLeft - 8, ty + 4);
        }
        if (unit != null && !unit.isEmpty()) {
            g.setColor(Canvas.TEXT_SECONDARY);
            g.drawString(unit, plotLeft - 50, plotTop - 12);
        }

        // bars
        double slot = (double) (plotRight - plotLeft) / bars.size();
        double barWidth = Math.min(96, slot * 0.55);
        double base = y.applyAsDouble(0);
        for (int i = 0; i < bars.size(); i++) {
            Bar b = bars.get(i);
            double cx = plotLeft + slot * (i + 0.5);
            Color color = Canvas.series(slots.get(i));
            Canvas.bar(g, cx - barWidth / 2, barWidth, base, y.applyAsDouble(b.value()), color);

            double labelY = y.applyAsDouble(Math.max(b.value(), 0)) - 8;
            if (b.max() > b.min()) {
                double yMin = y.applyAsDouble(b.min()), yMax = y.applyAsDouble(b.max());
                g.setColor(Canvas.TEXT);
                Canvas.stroke(g, 1.5f);
                g.draw(new java.awt.geom.Line2D.Double(cx, yMin, cx, yMax));
                g.draw(new java.awt.geom.Line2D.Double(cx - 8, yMin, cx + 8, yMin));
                g.draw(new java.awt.geom.Line2D.Double(cx - 8, yMax, cx + 8, yMax));
            }
            g.setColor(Canvas.TEXT);
            g.setFont(Canvas.BOLD);
            String label = String.format(Locale.ROOT, valueFormat, b.value());
            if (b.max() > b.min()) {
                // beside the bar at the mean, so it never collides with the whisker
                g.drawString(label, (float) (cx + barWidth / 2 + 6), (float) (y.applyAsDouble(b.value()) + 5));
            } else {
                Canvas.centred(g, label, cx, labelY);
            }

            g.setFont(Canvas.BODY);
            g.setColor(Canvas.TEXT);
            Canvas.centred(g, b.label(), cx, plotBottom + 22);
        }
        if (bars.stream().anyMatch(b -> b.max() > b.min())) {
            g.setFont(Canvas.SMALL);
            g.setColor(Canvas.TEXT_SECONDARY);
            Canvas.right(g, "bar = mean, whisker = min - max over scenarios", plotRight, HEIGHT - 14);
        }
        g.dispose();
        return img;
    }
}
