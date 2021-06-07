package org.neo4j.logging.writer;

import java.util.Map;

public class StandardLogLineWriter implements LogLineWriter {


    public String writeLine(Map<String,Object> map) {
        String eventString=getEventString(map.getOrDefault("event", "").toString());
        String reasonString=map.get("failureReason") == null ? "" : " - "+map.get("failureReason");
        return  map.getOrDefault("time", "1970-01-01 00:00:00.000+0000")+" "+
                map.getOrDefault("level", "INFO")+" "+
                eventString+
                "id:"+map.getOrDefault("id", "0")+" - "+
                map.getOrDefault("elapsedTimeMs", "0")+" ms: "+
                map.getOrDefault("allocatedBytes","0")+" B - "+
                map.getOrDefault("source", "embedded-session")+"\t"+
                map.getOrDefault("database", "<none>")+" - "+
                map.getOrDefault("user", "")+" - "+
                map.getOrDefault("query", "")+" - "+
                map.getOrDefault("queryParameters", "{}")+" - "+  //TODO : should be like "- {name: 'match', desc: <null>} -" but is "- {context={address=localhost:7617}, database=<null>} -" // c.f. QueryLogFormatter.formatMapValue (: -> = and single-quote string values)
                "runtime="+map.get("runtime")+" - "+
                map.getOrDefault("annotationData", "{}")+
                reasonString+
                "\n" ;

        //TODO : add other fields (plannign, cpu, waiting, pageHits, pageFaults)
    }

    private String getEventString(String eventValue) {
        if ( "start".equals(eventValue)) {
             return " Query started: ";
        } else if ("success".equals(eventValue)) {
             return " ";
        } else {
            return "";
        }
    }
}
