package orbitslot.chart;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared drawing helpers and colours for all charts (plain Java2D, no chart
 * library). Colours are a validated colour-blind-safe categorical palette;
 * text never uses a series colour.
 */
final class Canvas {

    static final Color SURFACE = new Color(0xfcfcfb);
    static final Color TEXT = new Color(0x0b0b0b);
    static final Color TEXT_SECONDARY = new Color(0x52514e);
    static final Color GRID = new Color(0xe4e3de);
    static final Color AXIS = new Color(0xb5b4ad);
    /** Categorical slots in fixed order; slot n always means the same entity within a chart set. */
    static final Color[] SERIES = {
            new Color(0x2a78d6), new Color(0xeb6834), new Color(0x1baf7a), new Color(0xeda100),
            new Color(0xe87ba4), new Color(0x008300), new Color(0x6250d6), new Color(0xe34948)};
    /** Status colour, used only for "something went wrong" (missed / cancelled). */
    static final Color CRITICAL = new Color(0xd03b3b);

    static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 17);
    static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    static final Font BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 13);

    private Canvas() { }

    static Color series(int slot) {
        if (slot >= SERIES.length) {
            throw new IllegalArgumentException("At most " + SERIES.length + " series per chart");
        }
        return SERIES[slot];
    }

    /** A new image with the surface painted and anti-aliasing on. */
    static BufferedImage image(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(SURFACE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return img;
    }

    static Graphics2D graphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    static void title(Graphics2D g, String title, String subtitle, int x) {
        g.setColor(TEXT);
        g.setFont(TITLE);
        g.drawString(title, x, 30);
        if (subtitle != null && !subtitle.isEmpty()) {
            g.setColor(TEXT_SECONDARY);
            g.setFont(SMALL);
            g.drawString(subtitle, x, 50);
        }
    }

    /** Text centred on x. */
    static void centred(Graphics2D g, String text, double x, double y) {
        int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, (float) (x - w / 2.0), (float) y);
    }

    /** Text right-aligned at x. */
    static void right(Graphics2D g, String text, double x, double y) {
        int w = g.getFontMetrics().stringWidth(text);
        g.drawString(text, (float) (x - w), (float) y);
    }

    /**
     * A vertical bar from the baseline to {@code top}, with 4 px rounded corners
     * on the data end only (the end away from the baseline).
     */
    static void bar(Graphics2D g, double x, double width, double baselineY, double topY, Color color) {
        double y = Math.min(baselineY, topY);
        double h = Math.abs(baselineY - topY);
        if (h < 0.5) {
            return;
        }
        double r = Math.min(4, h);
        Path2D shape = new Path2D.Double();
        shape.append(new RoundRectangle2D.Double(x, y, width, h, 2 * r, 2 * r), false);
        g.setColor(color);
        g.fill(shape);
        // square off the baseline end
        if (topY <= baselineY) {
            g.fill(new java.awt.geom.Rectangle2D.Double(x, y + h - r, width, r));
        } else {
            g.fill(new java.awt.geom.Rectangle2D.Double(x, y, width, r));
        }
    }

    static void stroke(Graphics2D g, float width) {
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
    }

    /** Legend entry: a small colour swatch and its label. Returns the x after the entry. */
    static int legendEntry(Graphics2D g, int x, int y, Color color, String label) {
        g.setColor(color);
        g.fillRoundRect(x, y - 10, 12, 12, 4, 4);
        g.setColor(TEXT_SECONDARY);
        g.setFont(SMALL);
        g.drawString(label, x + 18, y);
        return x + 18 + g.getFontMetrics().stringWidth(label) + 22;
    }

    /** "Nice" axis ticks (steps of 1, 2 or 5 x 10^k) covering [min, max], about {@code count} of them. */
    static List<Double> ticks(double min, double max, int count) {
        if (max <= min) {
            max = min + 1;
        }
        double raw = (max - min) / count;
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double norm = raw / mag;
        double step = (norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10) * mag;
        List<Double> ticks = new ArrayList<>();
        for (double v = Math.floor(min / step) * step; v <= max + step * 0.999; v += step) {
            ticks.add(Math.abs(v) < step * 1e-9 ? 0 : v);
            if (v >= max) {
                break;
            }
        }
        return ticks;
    }

    /** Short number for axis labels: 12000 -> "12k", 1500000 -> "1.5M". */
    static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1e6) {
            return trim(v / 1e6) + "M";
        }
        if (a >= 1e4) {
            return trim(v / 1e3) + "k";
        }
        return trim(v);
    }

    private static String trim(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.2f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.equals("-0") ? "0" : s;
    }
}
