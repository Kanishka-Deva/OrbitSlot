package orbitslot;

import orbitslot.model.Antenna;
import orbitslot.model.Satellite.CubeSat;
import orbitslot.model.GroundStation;
import orbitslot.model.Pass;
import orbitslot.model.Satellite;
import orbitslot.model.Satellite.WeatherSat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Small hand-made building blocks for tests. Times are minutes after
 * {@link #T0}; azimuths default to 0 so slew time is zero unless a test
 * sets them, which leaves only the 60 s setup time between passes.
 */
public final class Fixtures {

    public static final Instant T0 = Instant.parse("2026-11-18T00:00:00Z");
    public static final double MIN_ELEVATION = 10;
    public static final double SLEW_DEG_PER_SEC = 3;
    public static final int SETUP_SECONDS = 60;

    private Fixtures() { }

    public static Instant at(double minutes) {
        return T0.plusSeconds(Math.round(minutes * 60));
    }

    public static GroundStation station(int antennas) {
        List<Antenna> list = new ArrayList<>();
        for (int i = 1; i <= antennas; i++) {
            list.add(new Antenna(i));
        }
        return new GroundStation("T", "Test station", 52.5, 13.4, 40, MIN_ELEVATION,
                SLEW_DEG_PER_SEC, SETUP_SECONDS, list);
    }

    /** A weather satellite with 8000 kbps, so one second of pass = 1 MB. */
    public static Satellite weather(int id, int priority) {
        return new WeatherSat(id, "W" + id, priority, 8000, 1000);
    }

    /** A cubesat with 8 kbps, so one second of pass = 0.001 MB. */
    public static Satellite cubesat(int id, int priority) {
        return new CubeSat(id, "C" + id, priority, 8, 5);
    }

    /** A pass from {@code fromMin} to {@code toMin}, 45 deg high, azimuth 0 at AOS and LOS. */
    public static Pass pass(String id, Satellite sat, double fromMin, double toMin) {
        return pass(id, sat, fromMin, toMin, 45, 0, 0);
    }

    public static Pass pass(String id, Satellite sat, double fromMin, double toMin,
                            double maxElevation, double aosAz, double losAz) {
        return new Pass(id, sat, at(fromMin), at(toMin), maxElevation, aosAz, losAz);
    }
}
