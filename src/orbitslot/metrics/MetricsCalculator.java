package orbitslot.metrics;

import orbitslot.model.Assignment;
import orbitslot.model.DroppedPass;
import orbitslot.model.Pass;
import orbitslot.model.Schedule;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Computes {@link Metrics} for a schedule over a planning window. */
public final class MetricsCalculator {

    private final int highPriorityMin;

    public MetricsCalculator(int highPriorityMin) {
        this.highPriorityMin = highPriorityMin;
    }

    public Metrics calculate(String algorithm, List<Pass> input, Schedule schedule, Instant start, Instant end) {
        double downlinked = 0;
        long busySeconds = 0;
        for (Assignment a : schedule.assignments()) {
            downlinked += a.pass().dataVolumeMB();
            busySeconds += secondsInside(a.pass(), start, end);
        }
        double available = input.stream().mapToDouble(Pass::dataVolumeMB).sum();
        long capacitySeconds = Duration.between(start, end).toSeconds() * schedule.station().antennas().size();

        int highTotal = (int) input.stream().filter(this::isHighPriority).count();
        int highMissed = (int) schedule.dropped().stream().map(DroppedPass::pass).filter(this::isHighPriority).count();

        return new Metrics(algorithm, input.size(), schedule.assignments().size(), schedule.dropped().size(),
                downlinked, available, 100.0 * busySeconds / capacitySeconds, highTotal, highMissed);
    }

    private boolean isHighPriority(Pass p) {
        return p.satellite().priority() >= highPriorityMin;
    }

    /** Tracking time that falls inside the window (a pass may run past the end). */
    private static long secondsInside(Pass p, Instant start, Instant end) {
        Instant from = p.aos().isBefore(start) ? start : p.aos();
        Instant to = p.los().isAfter(end) ? end : p.los();
        return Math.max(0, Duration.between(from, to).toSeconds());
    }
}
