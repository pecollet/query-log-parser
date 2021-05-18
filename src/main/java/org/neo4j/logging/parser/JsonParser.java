package org.neo4j.logging.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

public class JsonParser implements LogLineParser{

    private Path filename;
    private static final ObjectMapper mapper = new ObjectMapper();

    JsonParser(Path filename) {
        this.filename=filename;
    }

    public Stream<Map<?,?>> parse() throws IOException {
        return Files.readString(this.filename).lines().map(line -> lineToMap(line));
    }



    private Map<?,?> lineToMap(String line) {
        try {
            return mapper.readValue(line, Map.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return Collections.EMPTY_MAP;
    }
}
