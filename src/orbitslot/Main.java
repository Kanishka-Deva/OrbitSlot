package orbitslot;

import com.github.amsacode.predict4java.GroundStationPosition;

/** Entry point. Stage 0: proves the classpath works (javac + predict4java jar). */
public final class Main {

    public static void main(String[] args) {
        GroundStationPosition station = new GroundStationPosition(52.487, 13.475, 23);
        System.out.printf("hello from OrbitSlot - predict4java station at %.3f, %.3f%n",
                station.getLatitude(), station.getLongitude());
    }
}
