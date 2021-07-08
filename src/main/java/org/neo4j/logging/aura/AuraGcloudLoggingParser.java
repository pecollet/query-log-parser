package org.neo4j.logging.aura;


import com.google.api.gax.paging.Page;
import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Logging.EntryListOption;
import com.google.cloud.logging.Payload;
import org.neo4j.logging.parser.LogLineParser;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class AuraGcloudLoggingParser implements LogLineParser {
    private String projectId;
    private String dbid;
    private String authKeyFile = "/path/to/my/key.json";
    private LoggingOptions options;

    public AuraGcloudLoggingParser(String projectId, String authKeyFile, String dbid) throws IOException {
        this.projectId = projectId;
        this.authKeyFile = authKeyFile;
        this.dbid = dbid;
        //TODO : deal with authentication to GCP
        Credentials credentials = ServiceAccountCredentials.fromStream(new FileInputStream(this.authKeyFile));
        //Credentials credentials = GoogleCredentials.create(new AccessToken(accessToken, expirationTime));

        this.options = LoggingOptions.newBuilder()//getDefaultInstance();
                .setProjectId(this.projectId)
                .setCredentials(credentials)
                .build();
    }

    public Stream<LogEntry> getLogEntries(String dbid, String extraFilter) throws Exception {
        String filter="jsonPayload.dbid=\""+dbid+"\" ";
        filter+=extraFilter;
//        jsonPayload.event!="start"
//        -"CALL aura.activity.last()"
//        -"CALL dbms.routing.getRoutingTable"
//        -"CALL dbms.components()"
//        -"CALL dbms.showCurrentUser()"
//        -"CALL dbms.clientConfig()"
//        -"SHOW DATABASES"
//        -"CALL dbms.cluster.role"
//        -"CALL db.labels() YIELD label"
//        -"CALL db.indexes()"
//        -"CALL dbms.procedures"

        try(Logging logging = this.options.getService()) {
            Page<LogEntry> entries = logging.listLogEntries(
                    EntryListOption.filter("logName=projects/" + this.options.getProjectId() + "/logs/neo4j-query"
                            + " AND " + filter));
            //Iterator<LogEntry> entryIterator = entries.iterateAll().iterator();
//            do {
//                for (LogEntry logEntry : entries.iterateAll()) {
//                    System.out.println(logEntry);
//                }
//                entries = entries.getNextPage();
//            } while (entries != null);
            return StreamSupport.stream(entries.iterateAll().spliterator(), false);
        }
    }

    private Map<?,?> mapGCloudLogEntry(LogEntry le) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "query");
        map.put("raw", le.toString());
        //TODO : get the data
        Map<String, Object> payload = le.<Payload.JsonPayload>getPayload().getDataAsMap();
        map.putAll(payload);
//        "username": "neo4j",
//        "elapsedTimeMs": 10,
//        "id": "235001",
//        "neo4jcloud.role": "database",
//        "runtime": "pipelined",
//        "dbid": "dbid",
//        "query": "MATCH ... RETURN *",
//        "pageHits": 0,
//        "neo4jversion": "4.3.0-drop03-1",
//        "source": "bolt-session\tbolt\tneo4j-dotnet/4.3\t\tclient/10.0.0.3:45162\tserver/10.0.0.63:17687>",
//        "annotationData": "{}",
//        "allocatedBytes": 504188,
//        "message": "MATCH...",
//        "dbid_lbl": "dbid",
//        "pageFaults": 0,
//        "database": "neo4j",
//        "neo4jcloud.environment": "production",
//        "event": "commit"
        return map;
    }

    public Stream<Map<?,?>> parse() throws Exception {
        return getLogEntries(this.dbid, "").map(le -> mapGCloudLogEntry(le));
    };

    public long count() throws Exception {
        return getLogEntries(this.dbid, "").count();
    };

    public Map<?, ?> getAt(long index) throws Exception {
        return getLogEntries(this.dbid, "").skip(index - 1).findFirst().map(le -> mapGCloudLogEntry(le)).get();
    };
}
