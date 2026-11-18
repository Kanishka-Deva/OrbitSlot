package orbitslot.io;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Converts an OMM record into the classic three-line TLE format
 * (name, line 1, line 2) that predict4java understands.
 *
 * Column layout follows the standard NORAD two-line element format.
 * Each line is exactly 69 characters, the last one being a checksum.
 */
public final class OmmToTleConverter {

    /** TLEs have a 5-digit catalog number field. */
    public static final int MAX_TLE_CATALOG_NUMBER = 99_999;

    /** True if this record fits in the TLE format. */
    public boolean canConvert(OmmRecord r) {
        return r.noradCatId() > 0 && r.noradCatId() <= MAX_TLE_CATALOG_NUMBER;
    }

    /** Returns {name, line1, line2}. */
    public String[] toTle(OmmRecord r) {
        if (!canConvert(r)) {
            throw new IllegalArgumentException("Catalog number " + r.noradCatId()
                    + " of " + r.objectName() + " does not fit in the 5-digit TLE field");
        }
        return new String[] {r.objectName(), line1(r), line2(r)};
    }

    String line1(OmmRecord r) {
        String body = String.format(Locale.ROOT, "1 %05d%c %-8s %s %s %s %s %d %4d",
                r.noradCatId(),
                classification(r),
                internationalDesignator(r.objectId()),
                epoch(r.epoch()),
                firstDerivative(r.meanMotionDot()),
                exponential(r.meanMotionDdot()),
                exponential(r.bstar()),
                r.ephemerisType(),
                r.elementSetNo() % 10_000);
        return withChecksum(body);
    }

    String line2(OmmRecord r) {
        String body = String.format(Locale.ROOT, "2 %05d %8.4f %8.4f %07d %8.4f %8.4f %11.8f%5d",
                r.noradCatId(),
                r.inclination(),
                r.raOfAscNode(),
                Math.round(r.eccentricity() * 1e7),
                r.argOfPericenter(),
                r.meanAnomaly(),
                r.meanMotion(),
                r.revAtEpoch() % 100_000);
        return withChecksum(body);
    }

    private static char classification(OmmRecord r) {
        String c = r.classificationType();
        return (c == null || c.isEmpty()) ? 'U' : c.charAt(0);
    }

    /** "2003-048A" becomes "03048A". */
    static String internationalDesignator(String objectId) {
        if (objectId == null || objectId.length() < 9) {
            return "";
        }
        return objectId.substring(2, 4) + objectId.substring(5);
    }

    /** "2026-11-17T12:56:09.149856" becomes "26321.53899479" (YYDDD.DDDDDDDD). */
    static String epoch(String isoEpoch) {
        LocalDateTime t = LocalDateTime.parse(isoEpoch);
        double day = t.getDayOfYear() + t.toLocalTime().toNanoOfDay() / 86_400e9;
        return String.format(Locale.ROOT, "%02d%012.8f", t.getYear() % 100, day);
    }

    /** First derivative of mean motion, e.g. 4.4e-7 becomes " .00000044". */
    static String firstDerivative(double v) {
        String digits = String.format(Locale.ROOT, "%.8f", Math.abs(v)); // "0.00000044"
        return (v < 0 ? "-" : " ") + digits.substring(1);
    }

    /**
     * TLE "assumed decimal point" exponential notation, 8 characters:
     * 4.6625e-5 = 0.46625e-4 becomes " 46625-4".
     */
    static String exponential(double v) {
        if (v == 0.0) {
            return " 00000+0";   // CelesTrak's spelling of zero
        }
        double abs = Math.abs(v);
        int exp = (int) Math.floor(Math.log10(abs)) + 1;
        long mantissa = Math.round(abs / Math.pow(10, exp) * 1e5);
        if (mantissa == 100_000) {          // rounding pushed it to 1.0
            mantissa = 10_000;
            exp++;
        }
        if (Math.abs(exp) > 9) {
            throw new IllegalArgumentException("Value out of TLE exponent range: " + v);
        }
        return String.format(Locale.ROOT, "%c%05d%c%d",
                v < 0 ? '-' : ' ', mantissa, exp < 0 ? '-' : '+', Math.abs(exp));
    }

    /** Appends the modulo-10 checksum: digits count as their value, '-' counts as 1. */
    static String withChecksum(String line68) {
        if (line68.length() != 68) {
            throw new IllegalStateException("TLE line must be 68 chars before checksum, got "
                    + line68.length() + ": " + line68);
        }
        int sum = 0;
        for (char c : line68.toCharArray()) {
            if (Character.isDigit(c)) {
                sum += c - '0';
            } else if (c == '-') {
                sum += 1;
            }
        }
        return line68 + (sum % 10);
    }
}
