package orbitslot.model;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The output of a scheduler: which passes are tracked by which antenna, and
 * which passes were dropped and why. A pass is either assigned or dropped,
 * never both (adding a pass removes it from the dropped list).
 * Checking that the schedule is valid is the job of constraints and the
 * validator, not of this class.
 */
public final class Schedule {

    private final GroundStation station;
    private final Map<String, Assignment> assignments = new LinkedHashMap<>();   // by pass id
    private final Map<String, DroppedPass> dropped = new LinkedHashMap<>();      // by pass id

    public Schedule(GroundStation station) {
        this.station = station;
    }

    /** A copy that can be changed without affecting this schedule. */
    public Schedule copy() {
        Schedule c = new Schedule(station);
        c.assignments.putAll(assignments);
        c.dropped.putAll(dropped);
        return c;
    }

    public void add(Assignment assignment) {
        if (!station.antennas().contains(assignment.antenna())) {
            throw new IllegalArgumentException(assignment.antenna() + " is not part of " + station.name());
        }
        String id = assignment.pass().id();
        if (assignments.containsKey(id)) {
            throw new IllegalStateException("Pass " + id + " is already assigned");
        }
        assignments.put(id, assignment);
        dropped.remove(id);
    }

    /** Un-assigns a pass (no effect if it was not assigned). */
    public void remove(Pass pass) {
        assignments.remove(pass.id());
    }

    public void drop(Pass pass, String reason) {
        if (assignments.containsKey(pass.id())) {
            throw new IllegalStateException("Pass " + pass.id() + " is assigned; remove it before dropping");
        }
        dropped.put(pass.id(), new DroppedPass(pass, reason));
    }

    public Optional<Assignment> assignmentOf(Pass pass) {
        return Optional.ofNullable(assignments.get(pass.id()));
    }

    public GroundStation station() {
        return station;
    }

    /** All assignments (a snapshot), in the order they were added. */
    public List<Assignment> assignments() {
        return List.copyOf(assignments.values());
    }

    /** Assignments of one antenna, sorted by AOS. */
    public List<Assignment> assignmentsFor(Antenna antenna) {
        return assignments.values().stream()
                .filter(a -> a.antenna().equals(antenna))
                .sorted(Comparator.comparing(a -> a.pass().aos()))
                .toList();
    }

    /** Dropped passes, sorted by AOS. */
    public List<DroppedPass> dropped() {
        return dropped.values().stream().sorted(Comparator.comparing(d -> d.pass().aos())).toList();
    }
}
