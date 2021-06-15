package org.neo4j.logging.writer;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.logging.parser.StandardLogParser;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JsonLogLineWriter implements LogLineWriter {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Map<String, Integer> fieldOrder= Stream.of(
            new AbstractMap.SimpleEntry<>(1, "time"),
            new AbstractMap.SimpleEntry<>(2, "level"),
            new AbstractMap.SimpleEntry<>(3, "event"),
            new AbstractMap.SimpleEntry<>(4, "id"),
            new AbstractMap.SimpleEntry<>(5, "elapsedTimeMs"),
            new AbstractMap.SimpleEntry<>(6, "planning"),
            new AbstractMap.SimpleEntry<>(7, "cpu"),
            new AbstractMap.SimpleEntry<>(8, "waiting"),
            new AbstractMap.SimpleEntry<>(9, "allocatedBytes"),
            new AbstractMap.SimpleEntry<>(10, "pageHits"),
            new AbstractMap.SimpleEntry<>(11, "pageFaults"),
            new AbstractMap.SimpleEntry<>(12, "source"),
            new AbstractMap.SimpleEntry<>(13, "database"),
            new AbstractMap.SimpleEntry<>(14, "username"),
            new AbstractMap.SimpleEntry<>(15, "query"),
            new AbstractMap.SimpleEntry<>(16, "queryParameters"),
            new AbstractMap.SimpleEntry<>(17, "runtime"),
            new AbstractMap.SimpleEntry<>(18, "annotationData"),
            new AbstractMap.SimpleEntry<>(19, "failureReason") )
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    public JsonLogLineWriter() {
        //mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
    }

    //TODO : currently writing proper JSON, all the way, including in the queryParameters field
    // c.f. QueryLogFormatter.formatMapValue
    public String writeLine(Map<String, Object> map) {
        Map<String, Object> mapWithoutRaw = sortValues((HashMap<String, Object>)map);   ;
        if (mapWithoutRaw.containsKey("raw")) mapWithoutRaw.remove("raw");

        try{
            return mapper.writeValueAsString(mapWithoutRaw)+'\n';
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return "";
    }

    //method to sort values
    private static HashMap sortValues(HashMap map)
    {
        List list = new LinkedList(map.entrySet());
        //Custom Comparator
        Collections.sort(list, new Comparator() {
                public int compare(Object o1, Object o2) {
                    String k1 = ((Map.Entry) (o1)).getKey().toString();
                    String k2 = ((Map.Entry) (o2)).getKey().toString();
                    return fieldOrder.get(k1).compareTo(fieldOrder.get(k2));
                }
            });
        //copying the sorted list in HashMap to preserve the iteration order
        HashMap sortedHashMap = new LinkedHashMap();
        for (Iterator it = list.iterator(); it.hasNext();)
        {
            Map.Entry entry = (Map.Entry) it.next();
            sortedHashMap.put(entry.getKey(), entry.getValue());
        }
        return sortedHashMap;
    }
}
