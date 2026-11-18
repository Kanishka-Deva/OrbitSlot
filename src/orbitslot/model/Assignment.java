package orbitslot.model;

/** A decision: this pass is tracked by this antenna. */
public record Assignment(Pass pass, Antenna antenna) {
}
