package org.neo4j.logging.parser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

public class JsonLogParserTest {

    @Test
    void shouldParseJsonLogWithDefaultConfig() throws IOException {
        JsonLogParser parser=  new JsonLogParser(Path.of("src/test/resources/json_4.3_query.log"));
        Assertions.assertEquals(parser.parse().count(), 11L);

//        parser.parse().forEach(e -> {
//                assertEquals(e.get("level"), )
//        });
    }
}
