package orbitslot.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The output of a scheduler: which passes are tracked by which antenna.
 * Checking that the schedule is valid is the job of constraints and the
 * validator (Stage 2), not of this class.
 */
public final class Schedule {

    private final GroundStation station;
    private final List<Assignment> assignments = new ArrayList<>();
    private final List<DroppedPass> dropped = new ArrayList<>();

    public Schedule(GroundStation station) {
        this.station = station;
    }

    public void add(Assignment assignment) {
        if (!station.antennas().contains(assignment.antenna())) {
            throw new IllegalArgumentException(assignment.antenna() + " is not part of " + station.name());
        }
        assignments.add(assignment);
    }

    public void drop(Pass pass, String reason) {
        dropped.add(new DroppedPass(pass, reason));
    }

    /** Dropped passes, sorted by AOS. */
    public List<DroppedPass> dropped() {
        return dropped.stream().sorted(Comparator.comparing(d -> d.pass().aos())).toList();
    }

    public GroundStation station() {
        return station;
    }

    /** All assignments, unmodifiable, in the order they were added. */
    public List<Assignment> assignments() {
        return Collections.unmodifiableList(assignments);
    }

    /** Assignments of one antenna, sorted by AOS. */
    public List<Assignment> assignmentsFor(Antenna antenna) {
        return assignments.stream()
                .filter(a -> a.antenna().equals(antenna))
                .sorted(Comparator.comparing(a -> a.pass().aos()))
                .toList();
    }

    public boolean contains(Pass pass) {
        return assignments.stream().anyMatch(a -> a.pass().equals(pass));
    }
}
