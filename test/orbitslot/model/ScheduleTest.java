package orbitslot.model;

import orbitslot.model.Disruption.AntennaOutage;
import orbitslot.model.Schedule.Assignment;
import org.junit.jupiter.api.Test;

import static orbitslot.Fixtures.pass;
import static orbitslot.Fixtures.station;
import static orbitslot.Fixtures.weather;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleTest {

    private final GroundStation station = station(1);
    private final Antenna ant1 = station.antennas().get(0);
    private final Pass a = pass("a", weather(1, 5), 0, 10);

    @Test
    void addingADroppedPassRemovesItFromDropped() {
        Schedule s = new Schedule(station);
        s.drop(a, "no room");
        s.add(new Assignment(a, ant1));
        assertTrue(s.dropped().isEmpty());
        assertEquals(1, s.assignments().size());
    }

    @Test
    void refusesToDropAnAssignedPassOrAssignItTwice() {
        Schedule s = new Schedule(station);
        s.add(new Assignment(a, ant1));
        assertThrows(IllegalStateException.class, () -> s.drop(a, "x"));
        assertThrows(IllegalStateException.class, () -> s.add(new Assignment(a, ant1)));
    }

    @Test
    void refusesAnAntennaOfAnotherStation() {
        Schedule s = new Schedule(station);
        assertThrows(IllegalArgumentException.class, () -> s.add(new Assignment(a, new Antenna(9))));
    }

    @Test
    void copyIsIndependent() {
        Schedule s = new Schedule(station);
        s.add(new Assignment(a, ant1));
        Schedule c = s.copy();
        c.remove(a);
        assertEquals(1, s.assignments().size());
        assertTrue(c.assignments().isEmpty());
    }

    @Test
    void dataVolumeIsRateTimesDuration() {
        // 8000 kbps for 600 s = 4.8e9 bit = 600 MB
        assertEquals(600.0, a.dataVolumeMB(), 1e-9);
    }

    @Test
    void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> pass("bad", weather(1, 5), 10, 10));
        assertThrows(IllegalArgumentException.class, () -> weather(1, 11));
        assertThrows(IllegalArgumentException.class,
                () -> new AntennaOutage(ant1, a.los(), a.aos()));
    }
}
