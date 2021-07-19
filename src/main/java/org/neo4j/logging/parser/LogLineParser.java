package org.neo4j.logging.parser;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

public interface LogLineParser {


    public Stream<Map<String, Object>> parse() throws IOException;


    public long count() throws Exception;


    public Map<String, Object> getAt(long index) throws IOException;

}
