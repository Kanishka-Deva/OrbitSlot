package orbitslot.scheduler;

import orbitslot.constraint.Turnaround;
import orbitslot.model.Antenna;
import orbitslot.model.AntennaOutage;
import orbitslot.model.Assignment;
import orbitslot.model.Disruption;
import orbitslot.model.DroppedPass;
import orbitslot.model.PassCancellation;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Independent self-check run after every scheduler. It does not trust the
 * algorithm: it re-checks the finished schedule from scratch.
 */
public final class ScheduleValidator {

    /** @return a list of problems; empty means the schedule is valid */
    public List<String> validate(Schedule schedule, List<Pass> input) {
        List<String> problems = new ArrayList<>();
        GroundStation station = schedule.station();
        Set<String> assigned = new HashSet<>();

        for (Assignment a : schedule.assignments()) {
            if (!assigned.add(a.pass().id())) {
                problems.add("pass " + a.pass().id() + " assigned more than once");
            }
            if (a.pass().maxElevationDeg() < station.minElevationDeg()) {
                problems.add("pass " + a.pass().id() + " is below the elevation mask");
            }
        }

        for (Antenna antenna : station.antennas()) {
            List<Assignment> list = schedule.assignmentsFor(antenna);   // sorted by AOS
            for (int i = 1; i < list.size(); i++) {
                Pass prev = list.get(i - 1).pass();
                Pass cur = list.get(i).pass();
                if (!Turnaround.fits(prev, cur, station)) {
                    problems.add(antenna + ": " + cur.id() + " starts before " + prev.id()
                            + " ends + turnaround");
                }
            }
        }

        Set<String> dropped = new HashSet<>();
        for (DroppedPass d : schedule.dropped()) {
            dropped.add(d.pass().id());
            if (assigned.contains(d.pass().id())) {
                problems.add("pass " + d.pass().id() + " is both assigned and dropped");
            }
        }
        for (Pass p : input) {
            if (!assigned.contains(p.id()) && !dropped.contains(p.id())) {
                problems.add("pass " + p.id() + " is neither assigned nor dropped");
            }
        }
        return problems;
    }

    /** Extra checks after disruptions: no cancelled pass kept, nothing tracked during an outage. */
    public List<String> validate(Schedule schedule, List<Pass> input, List<Disruption> disruptions) {
        List<String> problems = validate(schedule, input);
        for (Disruption d : disruptions) {
            switch (d) {
                case PassCancellation c -> {
                    if (schedule.assignmentOf(c.pass()).isPresent()) {
                        problems.add("cancelled pass " + c.pass().id() + " is still scheduled");
                    }
                }
                case AntennaOutage o -> {
                    for (Assignment a : schedule.assignmentsFor(o.antenna())) {
                        if (o.blocks(a)) {
                            problems.add("pass " + a.pass().id() + " overlaps an outage of " + o.antenna());
                        }
                    }
                }
            }
        }
        return problems;
    }
}
