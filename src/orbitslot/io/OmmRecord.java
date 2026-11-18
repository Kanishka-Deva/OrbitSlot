package orbitslot.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One satellite's orbit data as published by CelesTrak in OMM JSON format.
 * Field names match the CCSDS OMM keywords used in the JSON files.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OmmRecord(
        @JsonProperty("OBJECT_NAME") String objectName,
        @JsonProperty("OBJECT_ID") String objectId,
        @JsonProperty("EPOCH") String epoch,
        @JsonProperty("MEAN_MOTION") double meanMotion,
        @JsonProperty("ECCENTRICITY") double eccentricity,
        @JsonProperty("INCLINATION") double inclination,
        @JsonProperty("RA_OF_ASC_NODE") double raOfAscNode,
        @JsonProperty("ARG_OF_PERICENTER") double argOfPericenter,
        @JsonProperty("MEAN_ANOMALY") double meanAnomaly,
        @JsonProperty("EPHEMERIS_TYPE") int ephemerisType,
        @JsonProperty("CLASSIFICATION_TYPE") String classificationType,
        @JsonProperty("NORAD_CAT_ID") int noradCatId,
        @JsonProperty("ELEMENT_SET_NO") int elementSetNo,
        @JsonProperty("REV_AT_EPOCH") int revAtEpoch,
        @JsonProperty("BSTAR") double bstar,
        @JsonProperty("MEAN_MOTION_DOT") double meanMotionDot,
        @JsonProperty("MEAN_MOTION_DDOT") double meanMotionDdot) {

    /** Orbital period in minutes (mean motion is in revolutions per day). */
    public double periodMinutes() {
        return 1440.0 / meanMotion;
    }
}
