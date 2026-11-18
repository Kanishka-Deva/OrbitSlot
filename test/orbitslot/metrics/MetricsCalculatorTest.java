package orbitslot.metrics;

import orbitslot.model.Antenna;
import orbitslot.model.Assignment;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static orbitslot.Fixtures.at;
import static orbitslot.Fixtures.pass;
import static orbitslot.Fixtures.station;
import static orbitslot.Fixtures.weather;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsCalculatorTest {

    @Test
    void computesEveryMetricOnAHandMadeSchedule() {
        GroundStation station = station(2);
        Antenna ant1 = station.antennas().get(0);
        Pass high = pass("h", weather(1, 9), 0, 10);      // 600 MB, high priority
        Pass low = pass("l", weather(2, 3), 20, 25);      // 300 MB
        Pass missed = pass("m", weather(3, 8), 30, 35);   // 300 MB, high priority, dropped
        // A pass running past the window end only counts its part inside for utilisation.
        Pass late = pass("x", weather(4, 1), 55, 70);     // 900 MB

        Schedule s = new Schedule(station);
        s.add(new Assignment(high, ant1));
        s.add(new Assignment(low, ant1));
        s.add(new Assignment(late, ant1));
        s.drop(missed, "test");

        Metrics m = new MetricsCalculator(8).calculate("t", List.of(high, low, missed, late), s, at(0), at(60));

        assertEquals(4, m.totalPasses());
        assertEquals(3, m.scheduledPasses());
        assertEquals(1, m.droppedPasses());
        assertEquals(1800, m.downlinkedMB(), 1e-9);
        assertEquals(2100, m.availableMB(), 1e-9);
        assertEquals(100.0 * 1800 / 2100, m.downlinkedPct(), 1e-9);
        // busy 10 + 5 + 5 (of 15) = 20 min out of 2 antennas x 60 min
        assertEquals(100.0 * 20 / 120, m.antennaUtilisationPct(), 1e-9);
        assertEquals(2, m.highPriorityPasses());
        assertEquals(1, m.highPriorityMissed());
        assertEquals(9 * 600 + 3 * 300 + 900, m.score(), 1e-9);
    }
}
