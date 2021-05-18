package org.neo4j.logging.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class JsonLogLineWriter implements LogLineWriter {

    private static final ObjectMapper mapper = new ObjectMapper();

    public String writeLine(Map<?,?> map) {
        try{
            return mapper.writeValueAsString(map)+'\n';
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return "";
    }
}
