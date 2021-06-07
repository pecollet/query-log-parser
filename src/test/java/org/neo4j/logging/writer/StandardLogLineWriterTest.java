package org.neo4j.logging.writer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class StandardLogLineWriterTest {

    @Test
    void shouldWriteLine()
    {
        Map<String,Object> map = new HashMap<>();
        map.put("time", "2021-05-15 19:45:02.343+0000");
        map.put("level", "INFO");
        LogLineWriter writer=new StandardLogLineWriter();
        String result=writer.writeLine(map);
        Assertions.assertEquals("2021-05-15 19:45:02.343+0000 INFO id:0 - 0 ms: 0 B - embedded-session\t<none> -  -  - {} - runtime=null - {}\n", result);

    }
}
