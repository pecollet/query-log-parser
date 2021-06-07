package org.neo4j.logging.writer;

import java.util.Map;

public interface LogLineWriter {
    public String writeLine(Map<String,Object> map) ;
}
