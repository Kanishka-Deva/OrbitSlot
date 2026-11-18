package orbitslot.model;

import java.time.Duration;
import java.time.Instant;

/**
 * One visibility window of a satellite over the ground station:
 * from acquisition of signal (AOS) to loss of signal (LOS).
 * Azimuths are compass degrees (0 = north, 90 = east).
 */
public record Pass(
        String id,
        Satellite satellite,
        Instant aos,
        Instant los,
        double maxElevationDeg,
        double aosAzimuthDeg,
        double losAzimuthDeg) {

    public Pass {
        if (!los.isAfter(aos)) {
            throw new IllegalArgumentException("Pass " + id + ": LOS must be after AOS");
        }
    }

    public Duration duration() {
        return Duration.between(aos, los);
    }

    /**
     * Data that can be downlinked in this pass, in megabytes (10^6 bytes).
     * Assumption: downlink rate x pass duration.
     */
    public double dataVolumeMB() {
        return satellite.downlinkKbps() * duration().toSeconds() / 8_000.0;
    }

    /** True if the two passes share any moment in time. */
    public boolean overlaps(Pass other) {
        return aos.isBefore(other.los) && other.aos.isBefore(los);
    }
}
