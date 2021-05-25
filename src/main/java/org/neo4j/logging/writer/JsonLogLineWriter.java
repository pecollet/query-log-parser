package org.neo4j.logging.writer;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;

public class JsonLogLineWriter implements LogLineWriter {

    private static final ObjectMapper mapper = new ObjectMapper();

    public JsonLogLineWriter() {
        mapper.addMixIn(Map.class, ReOrderMixIn.class);
        //mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)

    }

    //TODO : currently writing proper JSON, all the way, including in the queryParameters field
    // c.f. QueryLogFormatter.formatMapValue
    //TODO : order of fields is random ; use mapper.addMixIn(HashMap.class, ReOrderMixIn.class);
    public String writeLine(Map<?,?> map) {
        try{
            return mapper.writeValueAsString(map)+'\n';
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return "";
    }

    @JsonPropertyOrder({"time", "level", "event", "id"})
    private abstract class ReOrderMixIn {}
}
