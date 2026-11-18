package orbitslot.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads data/priorities.csv (columns: norad_id,priority,note).
 * Blank lines and lines starting with # are ignored. A missing file means
 * "no overrides".
 */
public final class PriorityOverridesLoader {

    public Map<Integer, Integer> load(Path csv) throws IOException {
        Map<Integer, Integer> overrides = new HashMap<>();
        if (!Files.exists(csv)) {
            return overrides;
        }
        List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        for (String line : lines.subList(1, lines.size())) {   // skip header
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] f = trimmed.split(",", -1);
            try {
                overrides.put(Integer.parseInt(f[0].trim()), Integer.parseInt(f[1].trim()));
            } catch (RuntimeException e) {
                throw new IllegalStateException("Bad line in " + csv + ": " + line, e);
            }
        }
        return overrides;
    }
}
