package org.neo4j.logging.parser;


import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class StandardLogParserTest {


    @Test
    void shouldParseWithDefaultConfig() throws IOException {
        StandardLogParser parser=  new StandardLogParser(Path.of("src/test/resources/std_4.3_query.log"));
        assertEquals("2021-05-15 19:44:31.788+0000", parser.getAt(1).get("time").toString());
        assertEquals("2021-05-15 19:45:00.428+0000", parser.getAt(10).get("time").toString());
        assertEquals("ERROR", parser.getAt(5).get("level"));
        assertEquals("INFO", parser.getAt(1).get("level"));
        assertEquals("start", parser.getAt(1).get("event"));
        assertEquals("success", parser.getAt(2).get("event"));
        assertEquals("fail", parser.getAt(5).get("event"));
        assertEquals("pipelined", parser.getAt(4).get("runtime"));
        assertEquals("neo4j", parser.getAt(4).get("database"));
        assertEquals("<none>", parser.getAt(5).get("database"));


        parser.parse().forEach(e -> {
            assertEquals(Integer.class, e.get("elapsedTimeMs").getClass());
            assertEquals(Integer.class, e.get("allocatedBytes").getClass());

            assertEquals("", e.get("username"));
            assertEquals(String.class, e.get("query").getClass());
            assertEquals(LinkedHashMap.class, e.get("queryParameters").getClass());
            assertEquals(LinkedHashMap.class, e.get("annotationData").getClass());
        });
    }

    @Test
    void shouldCountOk() throws IOException {
        StandardLogParser parser=  new StandardLogParser(Path.of("src/test/resources/std_4.3_query.log"));
        assertEquals(11L, parser.parse().count());
        assertEquals(11L, parser.count());
    }

    @Test
    void shouldParse3_5() throws IOException {
        //no start, no db
        StandardLogParser parser=  new StandardLogParser(Path.of("src/test/resources/std_3.5_query.log"));
        assertEquals("2020-03-14 12:05:02.500+0000", parser.getAt(1).get("time").toString());
        assertEquals("2020-03-14 12:13:20.503+0000", parser.getAt(4).get("time").toString());

        assertEquals("INFO", parser.getAt(1).get("level"));
        assertEquals("success", parser.getAt(1).get("event"));
        assertEquals("USR-RW", parser.getAt(1).get("username"));
        assertEquals(9901, parser.getAt(1).get("elapsedTimeMs"));

        assertEquals("ERROR", parser.getAt(4).get("level"));
        assertEquals("fail", parser.getAt(4).get("event"));
        String failure= parser.getAt(4).get("failureReason").toString();
        boolean failureContainsError= failure.contains("Failed to invoke");
        assertEquals(true, failureContainsError);

        parser.parse().forEach(e -> {
            assertEquals(Integer.class, e.get("elapsedTimeMs").getClass());
            assertNull(e.get("database"));
            assertEquals(String.class, e.get("query").getClass());
            boolean isMatch = e.get("query").toString().contains("MATCH");
            assertEquals(true, isMatch);
            assertEquals(LinkedHashMap.class, e.get("queryParameters").getClass());
            String paramValue=((LinkedHashMap)e.get("queryParameters")).get("id").toString();
            assertEquals("123", paramValue);
            assertEquals(LinkedHashMap.class, e.get("annotationData").getClass());
        });
    }

}
