package orbitslot.constraint;

import orbitslot.model.GroundStation;
import orbitslot.model.Pass;

import java.time.Duration;

/**
 * Time an antenna needs between the end of one pass and the start of the next:
 * rotating from the first pass's LOS azimuth to the next pass's AOS azimuth
 * (shorter way round, azimuth only) plus a fixed setup time.
 * Shared by {@link SlewTimeConstraint} and the schedule validator.
 */
public final class Turnaround {

    private Turnaround() { }

    public static Duration required(Pass from, Pass to, GroundStation station) {
        double diff = Math.abs(from.losAzimuthDeg() - to.aosAzimuthDeg()) % 360;
        double degrees = Math.min(diff, 360 - diff);
        long slewMillis = Math.round(degrees / station.slewSpeedDegPerSec() * 1000);
        return Duration.ofMillis(slewMillis).plusSeconds(station.setupSeconds());
    }

    /** True if {@code to} can be tracked after {@code from} on the same antenna. */
    public static boolean fits(Pass from, Pass to, GroundStation station) {
        return !to.aos().isBefore(from.los().plus(required(from, to, station)));
    }
}
