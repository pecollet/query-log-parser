package org.neo4j.logging.utils;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;


public class UtilTest {

    @ParameterizedTest
    @ValueSource(strings = {"2021-05-15 19:44:30.265+0000"})
    void toEpochTest(String date)  {
        assertEquals(1621107870265L, Util.toEpoch(date));
    }


    @ParameterizedTest
    @ValueSource(strings = {
            "{ xxxx:12}",
            "{xxxx: \"wertwe\"}",
            "{\"xxxx\" : value}",
            "{xxxx: {address: 'localhost:7617'}, database: <null>}"
        })
    void parseJsonStringValueTest(String dirtyJson)  {
        String key="someKey";
        Map map=new HashMap<String,Object>();
        map.put(key, dirtyJson);
        ObjectMapper mapper= new ObjectMapper();
        Util.parseJsonStringValue(map, key, mapper);

        assertEquals(LinkedHashMap.class, map.get(key).getClass());
        Map<String,Object> subMap = (Map)map.get(key);
        assertTrue(subMap.containsKey("xxxx"));
    }
}
