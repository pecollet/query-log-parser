package org.neo4j.logging.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringEscapeUtils;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class Util {

    public static Long toEpoch(String timeDateStr) {
        DateTimeFormatter dtf  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ");
        ZonedDateTime zdt  = ZonedDateTime.parse(timeDateStr, dtf);
        return zdt.toInstant().toEpochMilli();
    }


    //whenever a map value is string-ified json, put it in a map of its own
    public static void parseJsonStringValues(Map<String,Object> map, ObjectMapper mapper) {
        parseJsonStringValue(map, "queryParameters", mapper);
        parseJsonStringValue(map, "annotationData", mapper);
    }

    public static void parseJsonStringValue(Map<String,Object> map, String key, ObjectMapper mapper) {
        if (map.containsKey(key)) {
            //gson can parse unquoted json!
            String canonicalJson = com.google.gson.JsonParser.parseString(map.get(key).toString()).toString();
            try {
                Map<?,?> jsonMap =mapper.readValue(canonicalJson,Map.class);
                //replace dirty json string by the map
                map.put(key, jsonMap);
            } catch (JsonProcessingException e) {
                System.out.println("Error while parsing field "+key);
                e.printStackTrace();
            }
        }
    }
}
