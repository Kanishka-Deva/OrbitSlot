package orbitslot.chart;

import orbitslot.model.Antenna;
import orbitslot.model.Disruption.AntennaOutage;
import orbitslot.model.Schedule.Assignment;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static orbitslot.Fixtures.at;
import static orbitslot.Fixtures.pass;
import static orbitslot.Fixtures.station;
import static orbitslot.Fixtures.weather;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Checks that the charts draw the data where it belongs, by reading pixel
 * colours at positions computed from the layout (plot spans x = 150 .. 1970,
 * first antenna row centred at y = 159, missed-pass marks at y = 185 .. 197).
 */
class ChartTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private final GroundStation station = station(1);
    private final Antenna ant1 = station.antennas().get(0);

    /** Window of 2 h: the first hour holds a planned pass, the second a missed high-priority pass. */
    private BufferedImage gantt(List<orbitslot.model.Disruption> disruptions) {
        Pass planned = pass("p", weather(1, 5), 0, 60);
        Pass missed = pass("m", weather(2, 9), 60, 120);
        Schedule s = new Schedule(station);
        s.add(new Assignment(planned, ant1));
        s.drop(missed, "test");
        return new GanttChart(ZoneOffset.UTC, 8).render("t", "s",
                List.of(new GanttChart.Panel("plan", s, disruptions, Set.of())), at(0), at(120));
    }

    @Test
    void ganttDrawsPassesAndMissedPassesAtTheirTimes() {
        BufferedImage img = gantt(List.of());
        double plotWidth = 1970 - 150;
        int firstQuarter = (int) (150 + 0.25 * plotWidth);
        int thirdQuarter = (int) (150 + 0.75 * plotWidth);

        assertEquals(Canvas.series(6).getRGB(), img.getRGB(firstQuarter, 159), "weather pass on ANT-1");
        assertEquals(Canvas.SURFACE.getRGB(), img.getRGB(thirdQuarter, 159), "nothing planned in hour 2");
        assertEquals(Canvas.CRITICAL.getRGB(), img.getRGB(thirdQuarter, 191), "missed prio-9 pass");
        assertEquals(Canvas.SURFACE.getRGB(), img.getRGB(firstQuarter, 191), "nothing missed in hour 1");
    }

    @Test
    void ganttShowsOutageBehindTheRow() {
        BufferedImage img = gantt(List.of(new AntennaOutage(ant1, at(70), at(110))));
        int x = (int) (150 + (90.0 / 120) * (1970 - 150));
        // The outage area is hatched: not the plain surface, and not a pass colour.
        boolean hatched = false;
        for (int dx = 0; dx < 8; dx++) {
            int rgb = img.getRGB(x + dx, 159);
            assertNotEquals(Canvas.series(6).getRGB(), rgb);
            hatched |= rgb != Canvas.SURFACE.getRGB();
        }
        assertEquals(true, hatched);
    }

    @Test
    void barChartBarHeightIsProportionalToTheValue() {
        BufferedImage img = new BarChart().render("t", "s", "u",
                List.of(new BarChart.Bar("a", 100, 100, 100), new BarChart.Bar("b", 50, 50, 50)),
                "%.0f", List.of(0, 1));
        // Bars are centred in two equal slots between x = 78 and 732; baseline y = 480 - 58 = 422.
        int xa = (int) (78 + 654 * 0.25), xb = (int) (78 + 654 * 0.75);
        int topA = topOf(img, xa, Canvas.series(0)), topB = topOf(img, xb, Canvas.series(1));
        double heightA = 422 - topA, heightB = 422 - topB;
        assertEquals(2.0, heightA / heightB, 0.05);
    }

    @Test
    void niceTicksAndCompactLabels() {
        assertEquals(List.of(0.0, 100_000.0, 200_000.0, 300_000.0),   // 252k / 5 rounds up to a 100k step
                Canvas.ticks(0, 252_000, 5));
        assertEquals(List.of(0.0, 5.0, 10.0, 15.0, 20.0), Canvas.ticks(0, 19, 5));
        assertEquals("12k", Canvas.compact(12_000));
        assertEquals("1.5M", Canvas.compact(1_500_000));
        assertEquals("2.5", Canvas.compact(2.5));
    }

    private static int topOf(BufferedImage img, int x, Color color) {
        for (int y = 0; y < img.getHeight(); y++) {
            if (img.getRGB(x, y) == color.getRGB()) {
                return y;
            }
        }
        throw new AssertionError("colour not found in column " + x);
    }
}
