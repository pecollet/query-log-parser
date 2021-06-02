package org.neo4j.logging.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonParser;
import org.neo4j.logging.utils.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

public class JsonLogParser implements LogLineParser{

    private Path filename;
    private static final ObjectMapper mapper = new ObjectMapper();

    public JsonLogParser(Path filename) {
        this.filename=filename;
    }

    public Stream<Map<?,?>> parse() throws IOException {
        return Files.readString(this.filename).lines().map(line -> lineToMap(line));
    }

    public long count() throws IOException {
        return Files.readString(this.filename).lines().count();
    }

    public Map<?, ?> getAt(long index) throws IOException {
        return  Files.readString(this.filename).lines().skip(index - 1).findFirst()
                .map(line -> lineToMap(line))
                .get();
    }


    private Map<?,?> lineToMap(String line) {
        try {
            Map<String,Object> tmp = mapper.readValue(line, Map.class);
            Util.parseJsonStringValues(tmp, mapper);
            return tmp;
        } catch (JsonProcessingException e) {
            System.out.println("Error while parsing line : "+line);
            e.printStackTrace();
        }
        return Collections.EMPTY_MAP;
    }
}
