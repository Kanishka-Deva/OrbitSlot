package orbitslot.constraint;

import orbitslot.model.Antenna;
import orbitslot.model.Disruption.AntennaOutage;
import orbitslot.model.Schedule.Assignment;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Satellite;
import orbitslot.model.Schedule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static orbitslot.Fixtures.at;
import static orbitslot.Fixtures.pass;
import static orbitslot.Fixtures.station;
import static orbitslot.Fixtures.weather;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstraintTest {

    private final GroundStation station = station(2);
    private final Antenna ant1 = station.antennas().get(0);
    private final Antenna ant2 = station.antennas().get(1);
    private final Satellite sat = weather(1, 5);

    // --- MinElevationConstraint ----------------------------------------------

    @Test
    void minElevationRejectsLowPassAndAllowsMaskExactly() {
        MinElevationConstraint c = new MinElevationConstraint();
        Schedule empty = new Schedule(station);
        Pass low = pass("low", sat, 0, 10, 9.9, 0, 0);
        Pass exact = pass("exact", sat, 0, 10, 10.0, 0, 0);

        assertTrue(c.violation(new Assignment(low, ant1), empty).isPresent());
        assertTrue(c.violation(new Assignment(low, ant1), empty).get().contains("below"));
        assertTrue(c.violation(new Assignment(exact, ant1), empty).isEmpty());
    }

    // --- Turnaround ----------------------------------------------------------

    @Test
    void turnaroundIsSlewTheShortWayRoundPlusSetup() {
        // LOS at 350 deg, next AOS at 20 deg: 30 deg the short way, at 3 deg/s = 10 s, + 60 s setup.
        Pass a = pass("a", sat, 0, 10, 45, 0, 350);
        Pass b = pass("b", sat, 20, 30, 45, 20, 0);
        assertEquals(Duration.ofSeconds(70), Turnaround.required(a, b, station));
    }

    @Test
    void turnaroundFitsOnlyWhenTheGapIsLongEnough() {
        Pass a = pass("a", sat, 0, 10, 45, 0, 0);        // LOS az 0
        Pass b = pass("b", sat, 11, 20, 45, 180, 180);   // needs 60 s slew + 60 s setup = 2 min
        Pass c = pass("c", sat, 12, 20, 45, 180, 180);
        assertFalse(Turnaround.fits(a, b, station));
        assertTrue(Turnaround.fits(a, c, station));       // exactly 2 min gap is enough
    }

    // --- SlewTimeConstraint --------------------------------------------------

    @Test
    void slewRejectsOverlapOnSameAntennaButNotOnAnother() {
        SlewTimeConstraint c = new SlewTimeConstraint();
        Schedule s = new Schedule(station);
        s.add(new Assignment(pass("a", sat, 0, 10), ant1));
        Pass overlapping = pass("b", weather(2, 5), 5, 15);

        assertTrue(c.violation(new Assignment(overlapping, ant1), s).isPresent());
        assertTrue(c.violation(new Assignment(overlapping, ant2), s).isEmpty());
    }

    @Test
    void slewChecksPassesBeforeAndAfterTheCandidate() {
        SlewTimeConstraint c = new SlewTimeConstraint();
        Schedule s = new Schedule(station);
        s.add(new Assignment(pass("later", sat, 20, 30), ant1));

        // Ends 30 s before "later" starts: less than the 60 s setup.
        assertTrue(c.violation(new Assignment(pass("early", weather(2, 5), 0, 19.5), ant1), s).isPresent());
        // Ends 1 min before: exactly the setup time, slew is 0.
        assertTrue(c.violation(new Assignment(pass("ok", weather(3, 5), 0, 19), ant1), s).isEmpty());
    }

    // --- AntennaOutageConstraint ---------------------------------------------

    @Test
    void outageBlocksOnlyItsAntennaAndOnlyDuringTheOutage() {
        AntennaOutageConstraint c = new AntennaOutageConstraint(
                List.of(new AntennaOutage(ant1, at(10), at(20))));
        Schedule s = new Schedule(station);

        assertTrue(c.violation(new Assignment(pass("during", sat, 15, 25), ant1), s).isPresent());
        assertTrue(c.violation(new Assignment(pass("during", sat, 15, 25), ant2), s).isEmpty());
        assertTrue(c.violation(new Assignment(pass("after", sat, 20, 30), ant1), s).isEmpty());   // ends are exclusive
        assertTrue(c.violation(new Assignment(pass("before", sat, 0, 10), ant1), s).isEmpty());
    }
}
