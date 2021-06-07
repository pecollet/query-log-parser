package org.neo4j.logging;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public class QueryLogParserTest {



    //TODO : test inputs
    //TODO : test json parsing
    //TODO : test standard parsing

    //TODO : test json output
    //TODO : test standard output
    //TODO : test jmeter test plan

//    @Test
//    void shouldOutputToMultipleLogs() throws IOException {
//        String REGEX_LOG_ENTRY_START ="\n(?=2021)";
//        String filename = "/Users/david.pecollet/installs/tmp/install/instance2/neo4j-enterprise-4.3.0-drop04.0/logs/query.log";
//        String input=Files.readString(Path.of(filename)); //"234aaa456437aaaa-0id";
//        Pattern.compile(REGEX_LOG_ENTRY_START).splitAsStream(input).forEach(e -> System.out.println(">> "+e));
//    }
//
//    private static ContentValidator assertLog( FormattedLogFormat format, String content )
//    {
//        switch ( format )
//        {
//            case PLAIN:
//                return new LoggerContentValidator( content );
//            case JSON:
//                return new JsonContentValidator( content );
//            default:
//        }
//        throw new AssertionError();
//    }
//
//    private interface ContentValidator
//    {
//        void contains( LogLineContent... logLines );
//        void contains( LogLineContent logLine, Throwable exception );
//    }
}
