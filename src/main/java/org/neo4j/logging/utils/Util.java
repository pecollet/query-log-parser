package org.neo4j.logging.utils;

import org.apache.commons.lang.StringEscapeUtils;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Util {

    public static Long toEpoch(String timeDateStr) {
        DateTimeFormatter dtf  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ");
        ZonedDateTime zdt  = ZonedDateTime.parse(timeDateStr, dtf);
        return zdt.toInstant().toEpochMilli();
    }

    public static String escapeParametersForXml(String parameters) {
        //{&quot;paramName&quot;:&quot;dOw3Lpor&quot;}
        return StringEscapeUtils.escapeXml(parameters);
    }
}
