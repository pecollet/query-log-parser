package org.neo4j.logging.aura;


import com.google.api.gax.paging.Page;
import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.LoggingOptions;
import com.google.cloud.logging.Logging.EntryListOption;
import com.google.cloud.logging.Payload;
import org.neo4j.logging.parser.LogLineParser;
import org.neo4j.logging.utils.Util;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class AuraGcloudLoggingParser implements LogLineParser {
    private String projectId;
    private String dbid;
    private String authKeyFile = "/path/to/my/key.json";
    private String filter="";
    private LoggingOptions options;
    private Logging logging;
    private static final String BACKGROUND_QUERY_FILTER=" -\"CALL aura.activity.last()\" " +
            " -\"CALL dbms.routing.getRoutingTable\" " +
            " -\"CALL dbms.components()\" " +
            " -\"CALL dbms.showCurrentUser()\" " +
            " -\"CALL dbms.clientConfig()\" " +
            " -\"SHOW DATABASES\" " +
            " -\"CALL dbms.cluster.role\" " +
            " -\"CALL db.labels() YIELD label\" " +
            " -\"CALL db.indexes()\" " +
            " -\"CALL dbms.procedures\" ";

    public AuraGcloudLoggingParser(String projectId, String filter, String dbid) throws IOException {
        this.projectId = projectId;
        //this.authKeyFile = authKeyFile;
        this.filter=filter;
        this.dbid = dbid;
        System.out.println("Connecting to database '"+dbid+"' on GCP project '"+projectId+"'...");

        //TODO : deal with authentication to GCP
        // 1. Service Account : need IAM rights
        //  You are missing at least one of the following required permissions:
        //      Project
        //          iam.serviceAccounts.create
        // => service-account-file.json
        // 2. OAuth
        //  You are missing at least one of the following required permissions:
        //      Project
        //          clientauthconfig.brands.get
        //          oauthconfig.testusers.get
        //          oauthconfig.verification.get
        //          resourcemanager.projects.get
        //  => client_secrets.json
        //  scope:  https://www.googleapis.com/auth/logging.read

        //Credentials credentials = ServiceAccountCredentials.fromStream(new FileInputStream(this.authKeyFile));
        //Credentials credentials = OAuth2Credentials.create()
        //Credentials credentials =  GoogleCredentials.create(new AccessToken(accessToken, expirationTime));
        Credentials credentials = GoogleCredentials.getApplicationDefault();

        this.options = LoggingOptions.newBuilder()//getDefaultInstance();
                .setProjectId(this.projectId)
                .setCredentials(credentials)
                .build();
        this.logging= this.options.getService();
    }

    //dbid
    //extraFilter examples
    //  " timestamp > \"2021-07-09T15\" "
    //  " jsonPayload.event!=\"start\" "
    public Stream<LogEntry> getLogEntries(String dbid, String extraFilter) throws Exception {
        String filter="logName=projects/" + this.options.getProjectId() + "/logs/neo4j-query"
                        +" AND  jsonPayload.dbid=\""+dbid+"\" ";
        filter+=extraFilter;
        //filter+=" AND "+BACKGROUND_QUERY_FILTER;

//        try(Logging logging = this.options.getService()) {
            Page<LogEntry> entries = logging.listLogEntries(
                    EntryListOption.filter(filter),
                    EntryListOption.pageSize(1000));
//            int i=0;
//            for (LogEntry entry : entries.iterateAll()) {
//                System.out.println(i++ + ":"+entry.getInsertId());
//            }
            return StreamSupport.stream(entries.iterateAll().spliterator(), false);
//            Stream<LogEntry> allEntriesStream=Stream.of();
//            do {
//                allEntriesStream = Stream.concat(allEntriesStream, StreamSupport.stream(entries.iterateAll().spliterator(), false));
//                System.out.println("Has next page : "+entries.hasNextPage() +" ("+ entries.getNextPageToken()+")");
//                //TODO: fix pagination
//                //currently getting "INVALID_ARGUMENT: page_token doesn't match arguments from the request"
//                entries = entries.getNextPage();
//            } while (entries != null);
//            System.out.println("Completed");
//            return allEntriesStream;
//        }
    }

    private Map<String, Object> mapGCloudLogEntry(LogEntry le) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "query");
        //map.put("raw", le.toString());
        map.put("time", Util.epochToTimestamp(le.getTimestamp()));
        map.put("level",le.getSeverity().name());

        Map<String, Object> payload = le.<Payload.JsonPayload>getPayload().getDataAsMap();
        map.putAll(payload);
        String[] doubleValues={"allocatedBytes", "elapsedTimeMs", "pageHits", "pageFaults"}; // those need to be turned into integers
        map.replaceAll((key, value) ->  (Arrays.asList(doubleValues).contains(key)) ? ((Double) value).intValue() : value );
        map.put("raw",le.toString());
        //System.out.println(map.toString());
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

    public Stream<Map<String, Object>> parse() throws Exception {
            return getLogEntries(this.dbid, this.filter).map(le -> mapGCloudLogEntry(le));
    };

    public long count() throws Exception {
            return getLogEntries(this.dbid, this.filter).count();

    };

    public Map<String, Object> getAt(long index) throws Exception {
            return getLogEntries(this.dbid, this.filter).skip(index - 1).findFirst().map(le -> mapGCloudLogEntry(le)).get();
    };
}
