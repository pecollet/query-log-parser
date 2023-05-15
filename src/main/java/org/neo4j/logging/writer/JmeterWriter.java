package org.neo4j.logging.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.chain.web.MapEntry;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.tools.ToolManager;
import org.neo4j.logging.parser.LogLineParser;
import org.neo4j.logging.utils.Util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.filtering;
import static java.util.stream.Collectors.groupingBy;

public class JmeterWriter {
    private JMeterConfig config;

    private LogLineParser parser;
    private String logStartTime;
    private long logStartTimeLong;
    private String logEndTime;
    private long timeSpanMillis;
    private List<ThreadGroupData> threadGroups=new ArrayList<>();
    private static final ObjectMapper mapper = new ObjectMapper();
    private long discardedQueries=0;
    private long discardedThreads=0;
    private boolean useQueryStarts=true;

    public JmeterWriter(LogLineParser parser) {
        this.parser=parser;
        //get log start and end times
        try {
            long count = parser.count();
            System.out.println("[file log lines  : "+count+"]");
            this.logStartTime=parser.getAt(1).get("time").toString();
            System.out.println("[file start time : "+this.logStartTime+"]");
            this.logEndTime=parser.getAt(count).get("time").toString();
            System.out.println("[file end time   : "+this.logEndTime+"]");
            long starts = parser.parse().limit(20).filter(line -> "start".equals(line.get("event"))).count();
            if (starts == 0) {
                System.out.println("[no query starts found]");
                this.useQueryStarts=false;
            }
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
        this.logStartTimeLong=Util.toEpoch(this.logStartTime);
        this.timeSpanMillis=Util.toEpoch(this.logEndTime) - this.logStartTimeLong;
        System.out.println("[file time span  : "+this.timeSpanMillis+" ms]");
    }

    public JmeterWriter withConfig(Map<String, Object> config) {
        this.config=new JMeterConfig(config);
        System.out.println("[max Queries             : "+this.config.maxQueries+"]");
        System.out.println("[max Threads groups      : "+this.config.maxThreads+"]");
        return this;
    }

    public JmeterWriter parse() throws Exception  {
        parser.parse()
            .filter(line -> this.useQueryStarts ? "start".equals(line.get("event")) : true)          //filter: Query started, bolt, INFO????
            //.filter(line -> "INFO".equals(line.get("level")))
            .filter(line -> line.get("source").toString().startsWith("bolt-session"))
            .limit(this.config.maxQueries)                                      //apply limit
                //bolt-session\tbolt\tneo4j-cypher-shell/v4.3.0-drop04.0\t\tclient/127.0.0.1:58881\tserver/127.0.0.1:7617>
            .collect(groupingBy(line -> {
                        String[] source=line.get("source").toString().split("\\t");
                        String clientHost=source[4].split(":")[0];
                        String clientDriver=source[2];
                        return clientHost+'\t'+clientDriver;
                        })
                    )         //group by source
            .forEach((src,listOfEntries)-> createThreadGroup(src, listOfEntries));  //create Thread groups

        return this;
    }
    private void createThreadGroup(String source, List<Map<String, Object>> listOfEntries) {
        if (this.threadGroups.size() < this.config.maxThreads) {  //TODO : find way to limit upstream (in the collector)
            ThreadGroupData tg = new ThreadGroupData(source);
            String firstTime = listOfEntries.get(0).get("time").toString();
            tg.setStartTime(firstTime);
            for (Map<String, Object> queryMap : listOfEntries) {
                BoltSamplerData bs = new BoltSamplerData(queryMap.get("time").toString(), queryMap.getOrDefault("database", "").toString(),
                        queryMap.get("query").toString(), (Map)queryMap.get("queryParameters"), this.config);
                tg.addBoltSampler(bs);
            }
            this.threadGroups.add(tg);
        } else {
            this.discardedQueries+=listOfEntries.size();
            this.discardedThreads++;
        }
    }


    public void write(Path outputFilePath) {
        System.out.println("[Output thread groups    : "+this.threadGroups.size()+"]");
        System.out.println("[Thread limit - discarded thread groups/queries : "+this.discardedThreads+"/"+this.discardedQueries+"]");

        long outputQueryCount = this.threadGroups.stream().map(tg -> tg.getQueries().size()).collect(Collectors.summingInt(Integer::intValue));
        System.out.println("[Output Bolt samplers    : "+outputQueryCount+"]");

        //period covered start timestamp, end timestamps, duration with speed factor
        Optional<Long> startTime = this.threadGroups.stream().map(tg -> tg.getQueries().get(0).startTime).min(Long::compare);
        Optional<Long> endTime =this.threadGroups.stream().map(tg -> tg.getQueries().get(tg.getQueries().size()-1).startTime).max(Long::compare);
        System.out.println("[Test plan start : "+Util.epochToTimestamp(startTime.orElse(0L))+"]");
        System.out.println("[Test plan end   : "+Util.epochToTimestamp(endTime.orElse(0L))+"]");
        long duration=endTime.orElse(0L)-startTime.orElse(0L);
        System.out.println("[Test plan duration (s)  : "+ duration / (1000 * config.speedFactor) +" (with x"+config.speedFactor+")]");

        ToolManager velocityToolManager = new ToolManager();
        velocityToolManager.configure("src/velocity-tools.xml");

        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init();
        Template t = velocityEngine.getTemplate("src/main/resources/jmx_layout.xml");

        VelocityContext context = new VelocityContext(velocityToolManager.createContext());
        context.put("threadGroups", this.threadGroups);

        try {
            FileWriter writer = new FileWriter(String.valueOf(outputFilePath));
            t.merge(context, writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    public class BoltSamplerData {
        private long startTime;
        private String database;
        private String query;
        private String queryParameters;
        private String name;
        private String timerName="Wait";
        private String timerComment;
        private long threadDelay;
        private int txTimeout;
        private String recordQueryResults;
        private String accessMode;
        private double speedFactor;

        public BoltSamplerData(String time, String database, String query, Map<?,?> parameters, JMeterConfig config) {
            this.database=database.equals("<none>") ? "" : database;
            this.query=query;
            this.name= (query.length() > config.samplerNameMaxLength) ? StringEscapeUtils.escapeXml(query.substring(0,config.samplerNameMaxLength-1))+"..." : query;
            try {
                this.queryParameters=mapper.writeValueAsString(parameters); //Util.formatJson(parameters);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                this.queryParameters="Error parsing json";
            }
            this.txTimeout=config.samplerTxTimeout;
            this.startTime=Util.toEpoch(time);
            this.accessMode=config.samplerAccessMode;
            this.recordQueryResults=config.samplerRecordQueryResults;
            this.speedFactor= config.speedFactor;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTimerName() {
            return timerName;
        }

        public void setTimerName(String timerName) {
            this.timerName = timerName;
        }
        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }

        public String getQueryParameters() {
            return queryParameters;
        }

        public void setQueryParameters(String queryParameters) {
            this.queryParameters = queryParameters;
        }

        public String getTimerComment() {
            return timerComment;
        }

        public void setTimerComment(String timerComment) {
            this.timerComment = timerComment;
        }

        public long getThreadDelay() {
            return threadDelay;
        }

        public void setThreadDelay(long threadDelay) {
                this.threadDelay = (long)Math.ceil(threadDelay / this.speedFactor);
        }

        public int getTxTimeout() {
            return txTimeout;
        }

        public void setTxTimeout(int txTimeout) {
            this.txTimeout = txTimeout;
        }

        public String getRecordQueryResults() {
            return recordQueryResults;
        }

        public void setRecordQueryResults(String recordQueryResults) {
            this.recordQueryResults = recordQueryResults;
        }

        public String getAccessMode() {
            return accessMode;
        }

        public void setAccessMode(String accessMode) {
            this.accessMode = accessMode;
        }
    }

    public class ThreadGroupData {
        private String name;
        private String comment;
        private Long delay;     //in seconds
        private Long duration; //in seconds

        private ArrayList<BoltSamplerData> queries=new ArrayList<>();

        public ThreadGroupData(String source) {
            //bolt-session\tbolt\tneo4j-cypher-shell/v4.3.0-drop04.0\t\tclient/127.0.0.1:58881\tserver/127.0.0.1:7617>
            String clientType=source.split("\\t")[1];
            String clientHostPort=source.split("\\t")[0];
            this.name=clientHostPort;
            this.comment=clientType;
        }
        public void setStartTime(String startTime) {
            long delayMs = Util.toEpoch(startTime) - Util.toEpoch(logStartTime);
            this.delay= delayMs / 1000;
            //last until last query in the log
            this.duration=(timeSpanMillis - delayMs) /1000;
        }
        public void addBoltSampler(BoltSamplerData bs) {
            //measure time between new element and previous one
            long wait;
            String comment="";
            int count = this.queries.size();
            if (count == 0) {
                //thread delay is between log time and beginning of log file
                wait=bs.startTime - logStartTimeLong;
                comment="from start to "+bs.startTime;
            } else {
                wait=bs.startTime - this.queries.get(count-1).startTime;
                comment="from "+this.queries.get(count-1).startTime+" to "+bs.startTime;
            }
            bs.setThreadDelay(wait);
            bs.setTimerComment(comment);
            this.queries.add(bs);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public Long getDelay() {
            return delay;
        }

        public void setDelay(Long delay) {
            this.delay = delay;
        }

        public Long getDuration() {
            return duration;
        }

        public void setDuration(Long duration) {
            this.duration = duration;
        }

        public ArrayList<BoltSamplerData> getQueries() {
            return queries;
        }

        public void setQueries(ArrayList<BoltSamplerData> queries) {
            this.queries = queries;
        }
    }

    private class JMeterConfig {
        private int maxThreads=100;
        private int maxQueries=10000;
        private double speedFactor=1;
        private Map filters;

        private int samplerNameMaxLength=50;
        private int samplerTxTimeout=60;
        private String samplerAccessMode="WRITE";
        private String samplerRecordQueryResults="true";

        public JMeterConfig() {}
        public JMeterConfig(Map<String, Object> configMap) {
            configMap.entrySet().stream().forEach(me-> {
                String key=me.getKey().toString();
                Object value=me.getValue();
                if ( "speedFactor".equals(key)) {
                    this.speedFactor=(double)value;
                }
                if ( "maxThreads".equals(key)) {
                    this.maxThreads=(int)value;
                }
                if ( "maxQueries".equals(key)) {
                    this.maxQueries=(int)value;
                }
                if ( "samplerNameMaxLength".equals(key)) {
                    this.samplerNameMaxLength=(int)value;
                }
                if ( "samplerTxTimeout".equals(key)) {
                    this.samplerTxTimeout=(int)value;
                }
                if ( "samplerAccessMode".equals(key)) {
                    this.samplerAccessMode=(String)value;
                }
                if ( "samplerRecordQueryResults".equals(key)) {
                    this.samplerRecordQueryResults=(String)value;
                }
            });
        }
    }
}
