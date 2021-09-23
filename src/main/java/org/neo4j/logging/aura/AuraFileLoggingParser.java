package org.neo4j.logging.aura;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.gax.paging.Page;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.EntryListOption;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Payload;
import org.neo4j.logging.parser.LogLineParser;
import org.neo4j.logging.utils.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class AuraFileLoggingParser implements LogLineParser {
    private Path filename;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String[] BACKGROUND_QUERY_FILTER = {"CALL aura.activity.last()",
            "CALL dbms.routing.getRoutingTable($routingContext, $databaseName)", "CALL dbms.components()",
            "CALL dbms.showCurrentUser()", "CALL dbms.clientConfig()",  "SHOW DATABASES", "CALL dbms.cluster.role(\"neo4j\") YIELD role",
            "CALL db.labels() YIELD label", "CALL db.indexes()","CALL dbms.procedures", "\n" +
            "CALL db.labels() YIELD label\n" +
            "RETURN {name:'labels', data:COLLECT(label)[..1000]} AS result\n" +
            "UNION ALL\n" +
            "CALL db.relationshipTypes() YIELD relationshipType\n" +
            "RETURN {name:'relationshipTypes', data:COLLECT(relationshipType)[..1000]} AS result\n" +
            "UNION ALL\n" +
            "CALL db.propertyKeys() YIELD propertyKey\n" +
            "RETURN {name:'propertyKeys', data:COLLECT(propertyKey)[..1000]} AS result\n" +
            "UNION ALL\n" +
            "CALL dbms.functions() YIELD name, signature, description\n" +
            "RETURN {name:'functions', data: collect({name: name, signature: signature, description: description})} AS result\n" +
            "UNION ALL\n" +
            "CALL dbms.procedures() YIELD name, signature, description\n" +
            "RETURN {name:'procedures', data:collect({name: name, signature: signature, description: description})} AS result\n" +
            "UNION ALL\n" +
            "MATCH () RETURN { name:'nodes', data:count(*) } AS result\n" +
            "UNION ALL\n" +
            "MATCH ()-[]->() RETURN { name:'relationships', data: count(*)} AS result\n"};


    public AuraFileLoggingParser(Path filename) {
        this.filename=filename;
    }


    private Map<String, Object> mapGCloudLogEntry(Map le) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "query");
        // 2021-09-22T08:29:59.913Z	 => 2021-09-22 08:29:59.913+0000
        map.put("time", ((String)le.get("timestamp")).replace("T", " ").replace("Z", "+0000"));
        map.put("level",le.get("severity"));

        Map<String, Object> payload = (Map<String, Object>) le.<Map<String, Object>>get("jsonPayload");
        map.putAll(payload);

        map.put("raw",le.toString());
        //System.out.println(map.toString());

        return map;
    }


    private boolean isBackgroundQuery(Map<String, Object> entry) {
        String query = (String)entry.get("query");
        return Arrays.stream(BACKGROUND_QUERY_FILTER).anyMatch(query::equals);
    }
    public Stream<Map<String, Object>> parse() throws Exception {
        //List<Map<String,Object>> entries = mapper.readValue(Files.readString(this.filename), new TypeReference<List<Map>>() {});
        Map[] entries = mapper.readValue(Files.readString(this.filename), Map[].class);

        return Arrays.stream(entries).map(e -> mapGCloudLogEntry(e)).filter(e-> !isBackgroundQuery(e));
    };

    public long count() throws Exception {
        String[] entries = mapper.readValue(Files.readString(this.filename), String[].class);
        return entries.length;
    };

    public Map<String, Object> getAt(long index) throws Exception {
        Map[] entries = mapper.readValue(Files.readString(this.filename), Map[].class);
        return entries[(int) index];
    };
}
