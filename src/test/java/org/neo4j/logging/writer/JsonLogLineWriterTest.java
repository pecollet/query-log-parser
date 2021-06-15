package org.neo4j.logging.writer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class JsonLogLineWriterTest {

    @Test
    void shouldWriteLine()
    {
        Map<String,Object> map = new HashMap<>();
        map.put("time", "2021-05-15 19:45:02.343+0000");
        map.put("level", "INFO");
        map.put("database", "myDb");
        map.put("username", "toto");
        map.put("id", "123");
        map.put("event", "success");
        map.put("query", "MATCH (:Whatever)");
        map.put("elapsedTimeMs", 12);
        LogLineWriter writer=new JsonLogLineWriter();
        String result=writer.writeLine(map);
        Assertions.assertEquals("{\"time\":\"2021-05-15 19:45:02.343+0000\",\"level\":\"INFO\",\"event\":\"success\",\"id\":\"123\",\"elapsedTimeMs\":12,\"database\":\"myDb\",\"username\":\"toto\",\"query\":\"MATCH (:Whatever)\"}\n", result);

    }
}
