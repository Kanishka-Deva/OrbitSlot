package orbitslot.metrics;

/** Summary numbers for one schedule. Data volumes in MB (10^6 bytes). */
public record Metrics(
        String algorithm,
        int totalPasses,
        int scheduledPasses,
        int droppedPasses,
        double downlinkedMB,
        double availableMB,
        double antennaUtilisationPct,
        int highPriorityPasses,
        int highPriorityMissed) {

    /** Share of all offered data that was downlinked, in percent. */
    public double downlinkedPct() {
        return availableMB == 0 ? 0 : 100.0 * downlinkedMB / availableMB;
    }
}
