package orbitslot.scheduler;

import orbitslot.constraint.Constraint;
import orbitslot.constraint.MinElevationConstraint;
import orbitslot.constraint.SlewTimeConstraint;
import orbitslot.model.Schedule.Assignment;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static orbitslot.Fixtures.pass;
import static orbitslot.Fixtures.station;
import static orbitslot.Fixtures.weather;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FCFS, Greedy and SA on small cases where the right answer is known. */
class SchedulerTest {

    private static final List<Constraint> CONSTRAINTS =
            List.of(new MinElevationConstraint(), new SlewTimeConstraint());

    private final GroundStation oneAntenna = station(1);
    private final ScheduleValidator validator = new ScheduleValidator();

    /**
     * Three passes on one antenna:
     *   A prio 2, 0-10 min | B prio 9, 5-15 min (overlaps A) | C prio 3, 20-30 min.
     * FCFS takes A (earliest), so B is lost; Greedy takes B first, so A is lost.
     * C fits after either.
     */
    private final Pass a = pass("A", weather(1, 2), 0, 10);
    private final Pass b = pass("B", weather(2, 9), 5, 15);
    private final Pass c = pass("C", weather(3, 3), 20, 30);
    private final List<Pass> abc = List.of(c, b, a);    // deliberately not sorted

    @Test
    void fcfsTakesTheEarliestPass() {
        Schedule s = new FcfsScheduler(CONSTRAINTS).schedule(abc, oneAntenna);
        assertEquals(Set.of("A", "C"), assignedIds(s));
        assertEquals(List.of("B"), s.dropped().stream().map(d -> d.pass().id()).toList());
        assertTrue(s.dropped().get(0).reason().contains("busy with W1"));
        assertTrue(validator.validate(s, abc).isEmpty());
    }

    @Test
    void greedyTakesTheHighestPriorityPass() {
        Schedule s = new GreedyPriorityScheduler(CONSTRAINTS).schedule(abc, oneAntenna);
        assertEquals(Set.of("B", "C"), assignedIds(s));
        assertEquals(List.of("A"), s.dropped().stream().map(d -> d.pass().id()).toList());
        assertTrue(validator.validate(s, abc).isEmpty());
    }

    @Test
    void greedyBreaksPriorityTiesByDataVolume() {
        Pass shortPass = pass("short", weather(1, 5), 0, 5);
        Pass longPass = pass("long", weather(2, 5), 2, 12);   // same priority, more data
        Schedule s = new GreedyPriorityScheduler(CONSTRAINTS).schedule(List.of(shortPass, longPass), oneAntenna);
        assertEquals(Set.of("long"), assignedIds(s));
    }

    @Test
    void secondAntennaTakesTheOverlappingPass() {
        Schedule s = new FcfsScheduler(CONSTRAINTS).schedule(abc, station(2));
        assertEquals(Set.of("A", "B", "C"), assignedIds(s));
        assertEquals(1, s.assignmentOf(a).orElseThrow().antenna().number());
        assertEquals(2, s.assignmentOf(b).orElseThrow().antenna().number());
        assertTrue(validator.validate(s, abc).isEmpty());
    }

    @Test
    void passBelowTheMaskIsDroppedWithReason() {
        Pass low = pass("low", weather(1, 9), 0, 10, 5, 0, 0);
        Schedule s = new GreedyPriorityScheduler(CONSTRAINTS).schedule(List.of(low), oneAntenna);
        assertTrue(s.assignments().isEmpty());
        assertTrue(s.dropped().get(0).reason().contains("below"));
    }

    /**
     * A case where Greedy is clearly not optimal for the score:
     * X (prio 9, 4 min) sits between Y and Z (prio 7, 10 min each) and clashes
     * with both. Greedy keeps X: score 9 x 240 MB = 2160. Dropping X for Y + Z
     * gives 7 x 1200 MB = 8400. SA must find that.
     */
    @Test
    void simulatedAnnealingImprovesOnGreedy() {
        Pass y = pass("Y", weather(1, 7), 0, 10);
        Pass x = pass("X", weather(2, 9), 9, 13);
        Pass z = pass("Z", weather(3, 7), 12, 22);    // Y.los + 60 s setup <= Z.aos? 10 + 1 <= 12, yes
        List<Pass> passes = List.of(x, y, z);

        Schedule greedy = new GreedyPriorityScheduler(CONSTRAINTS).schedule(passes, oneAntenna);
        Schedule sa = new SimulatedAnnealingScheduler(CONSTRAINTS, 42, 2000, 0.5, 0.001)
                .schedule(passes, oneAntenna);

        assertEquals(Set.of("X"), assignedIds(greedy));
        assertEquals(2160, Objective.score(greedy), 1e-6);
        assertEquals(Set.of("Y", "Z"), assignedIds(sa));
        assertEquals(8400, Objective.score(sa), 1e-6);
        assertTrue(validator.validate(sa, passes).isEmpty());
    }

    @Test
    void simulatedAnnealingIsRepeatableAndNeverWorseThanGreedy() {
        List<Pass> passes = List.of(
                pass("p1", weather(1, 3), 0, 12), pass("p2", weather(2, 8), 5, 9),
                pass("p3", weather(3, 6), 8, 20), pass("p4", weather(4, 2), 15, 30),
                pass("p5", weather(5, 9), 25, 28), pass("p6", weather(6, 5), 29, 40));
        GroundStation two = station(2);
        SimulatedAnnealingScheduler sa = new SimulatedAnnealingScheduler(CONSTRAINTS, 7, 3000, 0.5, 0.001);

        Schedule first = sa.schedule(passes, two);
        Schedule second = sa.schedule(passes, two);
        Schedule greedy = new GreedyPriorityScheduler(CONSTRAINTS).schedule(passes, two);

        assertEquals(keys(first), keys(second));
        assertTrue(Objective.score(first) >= Objective.score(greedy) - 1e-9);
        assertTrue(validator.validate(first, passes).isEmpty());
    }

    @Test
    void emptyInputGivesEmptySchedule() {
        for (Scheduler s : List.of(new FcfsScheduler(CONSTRAINTS), new GreedyPriorityScheduler(CONSTRAINTS),
                new SimulatedAnnealingScheduler(CONSTRAINTS, 1, 100, 0.5, 0.001))) {
            Schedule result = s.schedule(List.of(), oneAntenna);
            assertTrue(result.assignments().isEmpty() && result.dropped().isEmpty(), s.name());
        }
    }

    private static Set<String> assignedIds(Schedule s) {
        return s.assignments().stream().map(x -> x.pass().id()).collect(Collectors.toSet());
    }

    private static Set<String> keys(Schedule s) {
        return s.assignments().stream().map(SchedulerTest::key).collect(Collectors.toSet());
    }

    private static String key(Assignment a) {
        return a.pass().id() + "@" + a.antenna().number();
    }
}
