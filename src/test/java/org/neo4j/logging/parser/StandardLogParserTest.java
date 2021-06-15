package org.neo4j.logging.parser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public class StandardLogParserTest {


    @Test
    void shouldParseWithDefaultConfig() throws IOException {
        StandardLogParser parser=  new StandardLogParser(Path.of("src/test/resources/std_4.3_query.log"));
        Assertions.assertEquals("2021-05-15 19:44:31.788+0000", parser.getAt(1).get("time").toString());
        Assertions.assertEquals("2021-05-15 19:45:00.428+0000", parser.getAt(10).get("time").toString());
        Assertions.assertEquals("ERROR", parser.getAt(5).get("level"));
        Assertions.assertEquals("INFO", parser.getAt(1).get("level"));
        Assertions.assertEquals("start", parser.getAt(1).get("event"));
        Assertions.assertEquals("success", parser.getAt(2).get("event"));
        Assertions.assertEquals("fail", parser.getAt(5).get("event"));

        parser.parse().forEach(e -> {
            Assertions.assertEquals(Integer.class, e.get("elapsedTimeMs").getClass());
            Assertions.assertEquals(Integer.class, e.get("allocatedBytes").getClass());
            Assertions.assertEquals(String.class, e.get("query").getClass());
            Assertions.assertEquals(LinkedHashMap.class, e.get("queryParameters").getClass());
            Assertions.assertEquals(LinkedHashMap.class, e.get("annotationData").getClass());
        });
    }

    @Test
    void shouldCountOk() throws IOException {
        StandardLogParser parser=  new StandardLogParser(Path.of("src/test/resources/std_4.3_query.log"));
        Assertions.assertEquals(11L, parser.parse().count());
        Assertions.assertEquals(11L, parser.count());
    }

}
