package org.neo4j.logging.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.ArrayUtils;
import org.neo4j.logging.utils.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StandardParser implements LogLineParser {
    private Path filename;
    private static final ObjectMapper mapper = new ObjectMapper(); //json mapper to parse "json" fields (ex: query parameters)

    //reference : com/neo4j/kernel/impl/query/ConfiguredQueryLoggerTest.java 987
    private static final Pattern LOGGER_LINE_PARSER = Pattern.compile(
            "^(?<time>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{4}) " +
                    "(?<level>\\w{4,5})\\s{1,2}" +
                    "(?<started>Query started: )?" +
                    "(?:id:(?<id>\\d+) - )?" +
                    "(?<elapsed>\\d+) ms: " +
                    "(?:\\(planning: (?<planning>\\d+)(?:, cpu: (?<cpu>\\d+))?, waiting: (?<waiting>\\d+)\\) - )?" +
                    "(?:(?<allocatedBytes>\\d+) B - )?" +
                    "(?:(?<pageHits>\\d+) page hits, (?<pageFaults>\\d+) page faults - )?" +
                    "(?<source>embedded-session\\t|bolt-session[^>]*>|server-session(?:\\t[^\\t]*){3})\\t" +
                    "(?<database>[^\\s]+) - " +
                    "(?<user>[^\\s]*) - " +    //* instead of +
                    "(?<query>.+?(?= - ))" +
                    "(?: - (?<params>\\{.*?(?=} - )}))?" +
                    "(?: - runtime=(?<runtime>\\w+))? - " +
                    "(?<additional>\\{.+?(?=(?:$| - )))" +
                    "(?: - (?<reason>.*$))?$" , Pattern.DOTALL);
    private static final String LOG_ENTRY_SPLITTER ="\n(?=\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{4} )";
    //private static final Pattern LOGGER_LINE_BEGINNING = Pattern.compile( REGEX_LOG_ENTRY_START );

    private static final Map<Integer, String> regexGroupNames= Stream.of(
            new AbstractMap.SimpleEntry<>(1, "time"),
            new AbstractMap.SimpleEntry<>(2, "level"),
            new AbstractMap.SimpleEntry<>(3, "event"),
            new AbstractMap.SimpleEntry<>(4, "id"),
            new AbstractMap.SimpleEntry<>(5, "elapsedTimeMs"),
            new AbstractMap.SimpleEntry<>(6, "planning"),           //TODO : check names in json format
            new AbstractMap.SimpleEntry<>(7, "cpu"),                //TODO : check names in json format
            new AbstractMap.SimpleEntry<>(8, "waiting"),            //TODO : check names in json format
            new AbstractMap.SimpleEntry<>(9, "allocatedBytes"),
            new AbstractMap.SimpleEntry<>(10, "pageHits"),          //TODO : check names in json format
            new AbstractMap.SimpleEntry<>(11, "pageFaults"),        //TODO : check names in json format
            new AbstractMap.SimpleEntry<>(12, "source"),
            new AbstractMap.SimpleEntry<>(13, "database"),
            new AbstractMap.SimpleEntry<>(14, "username"),
            new AbstractMap.SimpleEntry<>(15, "query"),
            new AbstractMap.SimpleEntry<>(16, "queryParameters"),
            new AbstractMap.SimpleEntry<>(17, "runtime"),
            new AbstractMap.SimpleEntry<>(18, "annotationData"),
            new AbstractMap.SimpleEntry<>(19, "failureReason") )
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    int[] integerFields= {5,6,7,8,9,10,11};
    StandardParser(Path filename) {
        this.filename=filename;
    }

    public Stream<Map<?,?>> parse() throws IOException {
       // return Files.readString(this.filename).lines().map(line -> lineToMap(line));
      //  return Arrays.stream(Files.readString(this.filename).split(REGEX_LOG_ENTRY_START)).map(line -> lineToMap(line));
      return  Pattern.compile(LOG_ENTRY_SPLITTER).splitAsStream(Files.readString(this.filename)).map(line -> lineToMap(line));
    }


    private Map<?,?> lineToMap(String line) {
       // System.out.println(">> "+line);
        //TODO : deal with multiline entries
        Map<String, Object> map = new HashMap<>();
        Matcher matcher = LOGGER_LINE_PARSER.matcher(line);

        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                //System.out.println("Group " + i + ": " + matcher.group(i));
                //group will be null if nothing is captured => convert to empty strings
                String value;
                if (i == 3) {
                    if (matcher.group(i) != null) {
                        value = "start";
                    } else {
                        value = matcher.group(2) == "INFO" ? "success" : "fail";
                    }
                    map.put(regexGroupNames.get(i), value);
                } else if (ArrayUtils.contains(integerFields, i)) {  //integer fields
                    //TODO : check allocatedBytes="-1" works
                    try {
                        map.put(regexGroupNames.get(i), Integer.parseInt(matcher.group(i)));
                    } catch (NumberFormatException n) {
                        //do nothing
                    }
                } else { //normal case
                    map.put(regexGroupNames.get(i), matcher.group(i));
                }
            }
            map.put("type", "query");
        } else {
            System.out.println("No match found : " + line);
        }
        Util.parseJsonStringValues(map, mapper);
        return map;
    }
}
