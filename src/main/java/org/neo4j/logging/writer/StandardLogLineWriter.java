package org.neo4j.logging.writer;

import java.util.Map;

public class StandardLogLineWriter implements LogLineWriter {
    public String writeLine(Map<?,?> map) {
        String eventString=getEventString(map.get("event").toString());
        String reasonString=map.get("failureReason") == null ? "" : " - "+map.get("failureReason");
        return  map.get("time")+" "+
                map.get("level")+" "+
                eventString+
                "id:"+map.get("id")+" - "+
                map.get("elapsedTimeMs")+" ms: "+
                map.get("allocatedBytes")+" B - "+
                map.get("source")+"\t"+
                map.get("database")+" - "+
                map.get("user")+" - "+
                map.get("query")+" - "+
                map.get("queryParameters")+" - "+
                "runtime="+map.get("runtime")+" - "+
                map.get("annotationData")+
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
