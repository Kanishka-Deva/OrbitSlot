package orbitslot.model;

/** Small satellite, typically a low-rate UHF/VHF downlink and a small buffer. */
public class CubeSat extends Satellite {

    public CubeSat(int noradId, String name, int priority, double downlinkKbps, double bufferMB) {
        super(noradId, name, priority, downlinkKbps, bufferMB);
    }

    @Override
    public String typeName() {
        return "CubeSat";
    }
}
