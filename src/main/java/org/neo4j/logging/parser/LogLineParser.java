package org.neo4j.logging.parser;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;

public interface LogLineParser {

    public Stream<Map<?,?>> parse() throws IOException;
}
