package orbitslot.model;

/** A pass the scheduler could not place, and why. */
public record DroppedPass(Pass pass, String reason) {
}
