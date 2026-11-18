package orbitslot.scheduler;

import orbitslot.model.Antenna;
import orbitslot.model.Disruption.AntennaOutage;
import orbitslot.model.Schedule.Assignment;
import orbitslot.model.Disruption;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Disruption.PassCancellation;
import orbitslot.model.Schedule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static orbitslot.Fixtures.at;
import static orbitslot.Fixtures.pass;
import static orbitslot.Fixtures.station;
import static orbitslot.Fixtures.weather;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The validator must catch every kind of broken schedule, built by hand (bypassing the constraints). */
class ScheduleValidatorTest {

    private final GroundStation station = station(2);
    private final Antenna ant1 = station.antennas().get(0);
    private final ScheduleValidator validator = new ScheduleValidator();

    private final Pass a = pass("a", weather(1, 5), 0, 10);
    private final Pass b = pass("b", weather(2, 5), 30, 40);

    @Test
    void validScheduleHasNoProblems() {
        Schedule s = new Schedule(station);
        s.add(new Assignment(a, ant1));
        s.drop(b, "test");
        assertEquals(List.of(), validator.validate(s, List.of(a, b)));
    }

    @Test
    void detectsOverlapOnOneAntenna() {
        Pass overlapping = pass("o", weather(3, 5), 5, 15);
        Schedule s = new Schedule(station);
        s.add(new Assignment(a, ant1));
        s.add(new Assignment(overlapping, ant1));
        List<String> problems = validator.validate(s, List.of(a, overlapping));
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("turnaround"));
    }

    @Test
    void detectsMissingSetupTimeEvenWithoutOverlap() {
        Pass tooSoon = pass("t", weather(3, 5), 10.5, 20);   // 30 s after a, setup needs 60 s
        Schedule s = new Schedule(station);
        s.add(new Assignment(a, ant1));
        s.add(new Assignment(tooSoon, ant1));
        assertEquals(1, validator.validate(s, List.of(a, tooSoon)).size());
    }

    @Test
    void detectsPassBelowMask() {
        Pass low = pass("low", weather(3, 5), 0, 10, 3, 0, 0);
        Schedule s = new Schedule(station);
        s.add(new Assignment(low, ant1));
        assertTrue(validator.validate(s, List.of(low)).get(0).contains("elevation mask"));
    }

    @Test
    void detectsPassThatIsNeitherAssignedNorDropped() {
        Schedule s = new Schedule(station);
        s.add(new Assignment(a, ant1));
        List<String> problems = validator.validate(s, List.of(a, b));
        assertEquals(List.of("pass b is neither assigned nor dropped"), problems);
    }

    @Test
    void detectsCancelledPassStillScheduledAndPassDuringOutage() {
        Schedule s = new Schedule(station);
        s.add(new Assignment(a, ant1));
        s.add(new Assignment(b, ant1));
        List<Disruption> disruptions = List.of(
                new PassCancellation(a, at(-30)),
                new AntennaOutage(ant1, at(35), at(50)));
        List<String> problems = validator.validate(s, List.of(a, b), disruptions);
        assertEquals(2, problems.size());
        assertTrue(problems.get(0).contains("cancelled pass a"));
        assertTrue(problems.get(1).contains("pass b overlaps an outage"));
    }
}
