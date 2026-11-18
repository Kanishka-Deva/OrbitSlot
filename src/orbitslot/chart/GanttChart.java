package orbitslot.chart;

import orbitslot.model.Antenna;
import orbitslot.model.AntennaOutage;
import orbitslot.model.Assignment;
import orbitslot.model.Disruption;
import orbitslot.model.DroppedPass;
import orbitslot.model.Pass;
import orbitslot.model.PassCancellation;
import orbitslot.model.Schedule;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Gantt-style timeline of one or more schedules over the planning window:
 * one row per antenna (passes coloured by satellite type), a row of missed
 * high-priority passes, and, if disruptions are given, antenna outages
 * (hatched), cancelled passes and the passes a repair added (outlined).
 * It only reads the Schedule model; it contains no scheduling logic.
 */
public final class GanttChart {

    /**
     * One stacked panel.
     * @param addedPassIds passes to outline (e.g. added by a repair); may be empty
     */
    public record Panel(String title, Schedule schedule, List<Disruption> disruptions, Set<String> addedPassIds) {
        public Panel(String title, Schedule schedule) {
            this(title, schedule, List.of(), Set.of());
        }
    }

    private static final String[] TYPES = {"Weather", "Comms", "CubeSat"};
    private static final Color OUTAGE_FILL = new Color(0xecebe7);
    private static final Color OUTAGE_HATCH = new Color(0x9a9992);

    private static final int WIDTH = 2000;
    private static final int LEFT = 150, RIGHT = 30, TOP = 92;
    private static final int PANEL_HEADER = 30, AXIS_ROW = 18, ROW = 38, BAR = 26, SMALL_ROW = 26, PANEL_GAP = 26;

    private final ZoneId zone;
    private final int highPriorityMin;

    public GanttChart(ZoneId zone, int highPriorityMin) {
        this.zone = zone;
        this.highPriorityMin = highPriorityMin;
    }

    public BufferedImage render(String title, String subtitle, List<Panel> panels, Instant start, Instant end) {
        int height = TOP + 20;
        for (Panel p : panels) {
            height += panelHeight(p) + PANEL_GAP;
        }
        BufferedImage img = Canvas.image(WIDTH, height);
        Graphics2D g = Canvas.graphics(img);
        Canvas.title(g, title, subtitle, 24);
        legend(g, panels.stream().anyMatch(p -> !p.disruptions().isEmpty()),
                panels.stream().anyMatch(p -> !p.addedPassIds().isEmpty()));

        int y = TOP;
        for (Panel p : panels) {
            drawPanel(g, p, y, start, end);
            y += panelHeight(p) + PANEL_GAP;
        }
        g.dispose();
        return img;
    }

    private int panelHeight(Panel p) {
        int rows = p.schedule().station().antennas().size() * ROW + SMALL_ROW;
        if (!p.disruptions().isEmpty()) {
            rows += SMALL_ROW;
        }
        return PANEL_HEADER + AXIS_ROW + rows;
    }

    private void drawPanel(Graphics2D g, Panel p, int top, Instant start, Instant end) {
        Schedule s = p.schedule();
        double plotLeft = LEFT, plotRight = WIDTH - RIGHT;
        double seconds = Duration.between(start, end).toSeconds();
        java.util.function.ToDoubleFunction<Instant> x = t -> {
            double f = Duration.between(start, t).toSeconds() / seconds;
            return plotLeft + Math.max(0, Math.min(1, f)) * (plotRight - plotLeft);
        };

        // header: panel title and counts
        List<DroppedPass> missed = s.dropped().stream()
                .filter(d -> d.pass().satellite().priority() >= highPriorityMin).toList();
        g.setColor(Canvas.TEXT);
        g.setFont(Canvas.BOLD);
        g.drawString(p.title(), 24, top + 18);
        int tw = g.getFontMetrics().stringWidth(p.title());
        g.setFont(Canvas.SMALL);
        g.setColor(Canvas.TEXT_SECONDARY);
        String counts = String.format("%d passes scheduled, %d dropped, %d high-priority missed",
                s.assignments().size(), s.dropped().size(), missed.size());
        if (!p.addedPassIds().isEmpty()) {
            counts += String.format(", %d added or moved by repair (outlined)", p.addedPassIds().size());
        }
        g.drawString(counts, 24 + tw + 14, top + 18);

        int axisY = top + PANEL_HEADER + AXIS_ROW - 4;
        int rowsTop = top + PANEL_HEADER + AXIS_ROW;
        int rowsBottom = rowsTop + s.station().antennas().size() * ROW + SMALL_ROW
                + (p.disruptions().isEmpty() ? 0 : SMALL_ROW);

        // hour grid + labels
        long hours = Duration.between(start, end).toHours();
        int labelEvery = hours > 12 ? 2 : 1;
        DateTimeFormatter hh = DateTimeFormatter.ofPattern("HH:mm").withZone(zone);
        g.setFont(Canvas.SMALL);
        for (long h = 0; h <= hours; h++) {
            double gx = x.applyAsDouble(start.plusSeconds(h * 3600));
            g.setColor(Canvas.GRID);
            Canvas.stroke(g, 1);
            g.draw(new Line2D.Double(gx, rowsTop, gx, rowsBottom));
            if (h % labelEvery == 0) {
                g.setColor(Canvas.TEXT_SECONDARY);
                Canvas.centred(g, hh.format(start.plusSeconds(h * 3600)), gx, axisY);
            }
        }

        // antenna rows
        int rowY = rowsTop;
        for (Antenna antenna : s.station().antennas()) {
            rowLabel(g, antenna.label(), rowY, ROW);
            double barTop = rowY + (ROW - BAR) / 2.0;
            for (Disruption d : p.disruptions()) {
                if (d instanceof AntennaOutage o && o.antenna().equals(antenna)) {
                    outage(g, x.applyAsDouble(o.from()), x.applyAsDouble(o.to()), barTop - 4, BAR + 8);
                }
            }
            for (Assignment a : s.assignmentsFor(antenna)) {
                Pass pass = a.pass();
                double x0 = x.applyAsDouble(pass.aos()), x1 = x.applyAsDouble(pass.los());
                Rectangle2D r = new Rectangle2D.Double(x0, barTop, Math.max(1.5, x1 - x0), BAR);
                g.setColor(typeColour(pass));
                g.fill(r);
                g.setColor(Canvas.SURFACE);           // thin surface ring separates neighbours
                Canvas.stroke(g, 1);
                g.draw(r);
                if (p.addedPassIds().contains(pass.id())) {
                    g.setColor(Canvas.TEXT);
                    Canvas.stroke(g, 2);
                    g.draw(new Rectangle2D.Double(r.getX() - 2, r.getY() - 2, r.getWidth() + 4, r.getHeight() + 4));
                }
            }
            g.setColor(Canvas.AXIS);
            Canvas.stroke(g, 1);
            g.draw(new Line2D.Double(plotLeft, rowY + ROW, plotRight, rowY + ROW));
            rowY += ROW;
        }

        // missed high-priority passes
        rowLabel(g, "Missed prio ≥ " + highPriorityMin, rowY, SMALL_ROW);
        for (DroppedPass d : missed) {
            double x0 = x.applyAsDouble(d.pass().aos()), x1 = x.applyAsDouble(d.pass().los());
            g.setColor(Canvas.CRITICAL);
            g.fill(new Rectangle2D.Double(x0, rowY + 7, Math.max(1.5, x1 - x0), SMALL_ROW - 14));
        }
        rowY += SMALL_ROW;

        // cancelled passes
        if (!p.disruptions().isEmpty()) {
            rowLabel(g, "Cancelled", rowY, SMALL_ROW);
            for (Disruption d : p.disruptions()) {
                if (d instanceof PassCancellation c) {
                    double x0 = x.applyAsDouble(c.pass().aos()), x1 = x.applyAsDouble(c.pass().los());
                    cancelled(g, x0, rowY + 5, Math.max(6, x1 - x0), SMALL_ROW - 10);
                }
            }
        }
    }

    private static void rowLabel(Graphics2D g, String label, int rowY, int rowHeight) {
        g.setColor(Canvas.TEXT);
        g.setFont(Canvas.BODY);
        Canvas.right(g, label, LEFT - 12, rowY + rowHeight / 2.0 + 5);
    }

    private static void outage(Graphics2D g, double x0, double x1, double y, double h) {
        Rectangle2D r = new Rectangle2D.Double(x0, y, x1 - x0, h);
        g.setColor(OUTAGE_FILL);
        g.fill(r);
        Shape clip = g.getClip();
        g.clip(r);
        g.setColor(OUTAGE_HATCH);
        Canvas.stroke(g, 1);
        for (double hx = x0 - h; hx < x1; hx += 7) {
            g.draw(new Line2D.Double(hx, y + h, hx + h, y));    // 45 deg hatch
        }
        g.setClip(clip);
        g.draw(r);
    }

    private static void cancelled(Graphics2D g, double x, double y, double w, double h) {
        g.setColor(Canvas.CRITICAL);
        g.setStroke(new BasicStroke(2));
        g.draw(new Rectangle2D.Double(x, y, w, h));
        g.draw(new Line2D.Double(x, y, x + w, y + h));
        g.draw(new Line2D.Double(x, y + h, x + w, y));
    }

    private static Color typeColour(Pass pass) {
        String type = pass.satellite().typeName();
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i].equals(type)) {
                return Canvas.series(i);
            }
        }
        return Canvas.series(TYPES.length);
    }

    private void legend(Graphics2D g, boolean disruptions, boolean added) {
        int lx = 24, ly = 72;
        for (int i = 0; i < TYPES.length; i++) {
            lx = Canvas.legendEntry(g, lx, ly, Canvas.series(i), TYPES[i] + " pass");
        }
        lx = Canvas.legendEntry(g, lx, ly, Canvas.CRITICAL, "missed high-priority pass (prio ≥ " + highPriorityMin + ")");
        if (disruptions) {
            outage(g, lx, lx + 24, ly - 11, 14);
            g.setColor(Canvas.TEXT_SECONDARY);
            g.setFont(Canvas.SMALL);
            g.drawString("antenna outage", lx + 30, ly);
            lx += 30 + g.getFontMetrics().stringWidth("antenna outage") + 22;
            cancelled(g, lx, ly - 10, 20, 12);
            g.setColor(Canvas.TEXT_SECONDARY);
            g.drawString("cancelled pass", lx + 26, ly);
            lx += 26 + g.getFontMetrics().stringWidth("cancelled pass") + 22;
        }
        if (added) {
            g.setColor(Canvas.TEXT);
            Canvas.stroke(g, 2);
            g.drawRect(lx, ly - 11, 14, 14);
            g.setColor(Canvas.TEXT_SECONDARY);
            g.setFont(Canvas.SMALL);
            g.drawString("added by repair", lx + 20, ly);
        }
    }
}
