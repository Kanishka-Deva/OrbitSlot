package orbitslot.io;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Reads a CelesTrak OMM JSON file (a JSON array of records). */
public final class OmmJsonReader {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<OmmRecord> read(Path file) throws IOException {
        return mapper.readValue(file.toFile(), new TypeReference<List<OmmRecord>>() { });
    }
}
