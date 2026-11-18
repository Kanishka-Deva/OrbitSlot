package orbitslot.model;

/** Communications satellite (our data: the Iridium constellation). */
public class CommsSat extends Satellite {

    public CommsSat(int noradId, String name, int priority, double downlinkKbps, double bufferMB) {
        super(noradId, name, priority, downlinkKbps, bufferMB);
    }

    @Override
    public String typeName() {
        return "Comms";
    }
}
