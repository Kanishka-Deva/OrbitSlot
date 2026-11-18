package orbitslot.model;

/** Earth-observation / weather satellite: high data rate, usually high priority. */
public class WeatherSat extends Satellite {

    public WeatherSat(int noradId, String name, int priority, double downlinkKbps, double bufferMB) {
        super(noradId, name, priority, downlinkKbps, bufferMB);
    }

    @Override
    public String typeName() {
        return "Weather";
    }
}
