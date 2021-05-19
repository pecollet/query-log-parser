package org.neo4j.logging.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StandardParser implements LogLineParser {
    private Path filename;

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
                    "(?<user>[^\\s]*) - " +    //* instead of
                    "(?<query>.+?(?= - ))" +
                    "(?: - (?<params>\\{.*?(?=} - )}))?" +
                    "(?: - runtime=(?<runtime>\\w+))? - " +
                    "(?<additional>\\{.+?(?=(?:$| - )))" +
                    "(?: - (?<reason>.*$))?$" );

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
            new AbstractMap.SimpleEntry<>(15, "queryParameters"),
            new AbstractMap.SimpleEntry<>(16, "params"),
            new AbstractMap.SimpleEntry<>(17, "runtime"),
            new AbstractMap.SimpleEntry<>(18, "annotationData"),
            new AbstractMap.SimpleEntry<>(19, "failureReason") )
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    StandardParser(Path filename) {
        this.filename=filename;
    }

    public Stream<Map<?,?>> parse() throws IOException {
        return Files.readString(this.filename).lines().map(line -> lineToMap(line));
    }


    private Map<?,?> lineToMap(String line) {
        //TODO : deal with multiline entries
        Matcher matcher = LOGGER_LINE_PARSER.matcher( line );
        Map<String, String> map =new HashMap<>();
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                //System.out.println("Group " + i + ": " + matcher.group(i));
                //group will be null if nothing is captured => convert to empty strings
                String value;
                if (i == 3) {
                    if (matcher.group(i) != null) {
                        value="start";
                    } else {
                        value= matcher.group(2) == "INFO" ? "success" : "fail";
                    }
                } else {
                    value = matcher.group(i);
                }
                map.put(regexGroupNames.get(i), value);
            }
        } else {
            System.out.println("No match found : "+line);
            //TODO : partial match up to query
        }
        return map;
    }
}