package orbitslot.repair;

import orbitslot.alert.ScheduleListener;
import orbitslot.constraint.Constraint;
import orbitslot.constraint.MinElevationConstraint;
import orbitslot.constraint.SlewTimeConstraint;
import orbitslot.model.Antenna;
import orbitslot.model.AntennaOutage;
import orbitslot.model.Assignment;
import orbitslot.model.Disruption;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.PassCancellation;
import orbitslot.model.Schedule;
import orbitslot.scheduler.GreedyPriorityScheduler;
import orbitslot.scheduler.ScheduleValidator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static orbitslot.Fixtures.at;
import static orbitslot.Fixtures.pass;
import static orbitslot.Fixtures.station;
import static orbitslot.Fixtures.weather;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One antenna. Plan (Greedy): P (prio 9, 60-70 min) wins over its overlapping
 * rival Q (prio 4, 62-72); R (prio 5, 300-310) is far in the future.
 * Then P is cancelled at 30 min.
 */
class RepairTest {

    private static final List<Constraint> CONSTRAINTS =
            List.of(new MinElevationConstraint(), new SlewTimeConstraint());

    private final GroundStation station = station(1);
    private final Antenna ant1 = station.antennas().get(0);
    private final Pass p = pass("P", weather(1, 9), 60, 70);
    private final Pass q = pass("Q", weather(2, 4), 62, 72);
    private final Pass r = pass("R", weather(3, 5), 300, 310);
    private final List<Pass> passes = List.of(p, q, r);
    private final Schedule plan = new GreedyPriorityScheduler(CONSTRAINTS).schedule(passes, station);
    private final List<Disruption> cancelP = List.of(new PassCancellation(p, at(30)));

    @Test
    void planIsAsExpected() {
        assertEquals(Set.of("P", "R"), ids(plan));
    }

    @Test
    void noRepairOnlyLosesTheCancelledPass() {
        DisruptionResult result = new DisruptionSimulator(CONSTRAINTS).run(plan, passes, cancelP, new NoRepair());
        assertEquals(Set.of("R"), ids(result.schedule()));
        assertEquals(1, result.forcedLosses());
        assertEquals(0, result.planChanges());
    }

    @Test
    void rollingRepairFillsTheFreedSlotWithTheRival() {
        RepairStrategy rolling = new RollingHorizonRepair("rolling", Duration.ofHours(2));
        DisruptionResult result = new DisruptionSimulator(CONSTRAINTS).run(plan, passes, cancelP, rolling);
        assertEquals(Set.of("Q", "R"), ids(result.schedule()));
        assertEquals(1, result.forcedLosses());
        assertEquals(1, result.planChanges());       // Q added
        assertTrue(new ScheduleValidator().validate(result.schedule(), passes, cancelP).isEmpty());
    }

    @Test
    void rollingRepairDoesNotLookBeyondItsHorizon() {
        // Q moved to 3 h after the disruption: outside a 2 h horizon, so it is not picked up.
        Pass farQ = pass("Q", weather(2, 4), 210, 220);
        List<Pass> input = List.of(p, farQ, r);
        Schedule planWithoutQ = new Schedule(station);
        planWithoutQ.add(new Assignment(p, ant1));
        planWithoutQ.add(new Assignment(r, ant1));
        planWithoutQ.drop(farQ, "kept out on purpose for this test");

        RepairStrategy rolling = new RollingHorizonRepair("rolling", Duration.ofHours(2));
        DisruptionResult result = new DisruptionSimulator(CONSTRAINTS).run(planWithoutQ, input, cancelP, rolling);
        assertEquals(Set.of("R"), ids(result.schedule()));
    }

    @Test
    void outageLosesThePassesItHitsAndTellsTheListeners() {
        List<String> events = new ArrayList<>();
        DisruptionSimulator sim = new DisruptionSimulator(CONSTRAINTS);
        sim.addListener(new ScheduleListener() {
            @Override public void disruptionAnnounced(Disruption d) { events.add("announced"); }
            @Override public void passLost(Pass pass, String reason) { events.add("lost " + pass.id()); }
        });
        List<Disruption> outage = List.of(new AntennaOutage(ant1, at(55), at(80)));
        DisruptionResult result = sim.run(plan, passes, outage, new NoRepair());

        assertEquals(List.of("announced", "lost P"), events);
        assertEquals(Set.of("R"), ids(result.schedule()));
        assertTrue(new ScheduleValidator().validate(result.schedule(), passes, outage).isEmpty());
    }

    private static Set<String> ids(Schedule s) {
        return s.assignments().stream().map(a -> a.pass().id()).collect(Collectors.toSet());
    }
}
