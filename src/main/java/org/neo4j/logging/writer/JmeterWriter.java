package org.neo4j.logging.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static java.util.stream.Collectors.groupingBy;

public class JmeterWriter {
    private int maxThreads=100;
    private int maxQueries=10000;
    private LogLineParser parser;
    private String logStartTime;
    private long logStartTimeLong;
    private String logEndTime;
    private long timeSpanMillis;
    private List<ThreadGroupData> threadGroups=new ArrayList<>();
    private static final ObjectMapper mapper = new ObjectMapper();

    public JmeterWriter(LogLineParser parser) {
        this.parser=parser;
        //get log start and end times
        try {
            this.logStartTime=parser.getAt(1).get("time").toString();
            System.out.println("[log start time : "+this.logStartTime+"]");
            long count = parser.count();
            System.out.println("[log lines read : "+count+"]");
            this.logEndTime=parser.getAt(count).get("time").toString();
            System.out.println("[log end time : "+this.logEndTime+"]");
        } catch(IOException e) {
            e.printStackTrace();
            System.exit(2);
        }
        this.logStartTimeLong=Util.toEpoch(this.logStartTime);
        this.timeSpanMillis=Util.toEpoch(this.logEndTime) - this.logStartTimeLong;
        System.out.println("[time span : "+this.timeSpanMillis+" ms]");
    }

    public JmeterWriter withMaxThreads(int maxThreads) {
        this.maxThreads=maxThreads;
        return this;
    }
    public JmeterWriter withMaxQueries(int maxQueries) {
        this.maxQueries=maxQueries;
        return this;
    }

    public JmeterWriter parse() throws IOException  {
        parser.parse()
            .filter(line -> "start".equals(line.get("event")))          //filter: Query started, bolt, INFO????
            //.filter(line -> "INFO".equals(line.get("level")))
            .filter(line -> line.get("source").toString().startsWith("bolt-session"))
            .limit(this.maxQueries)                                     //apply limit
            .collect(groupingBy(line -> line.get("source").toString())) //group by source
            .forEach((k,v)-> createThreadGroup(k,v));                   //create Thread groups

        return this;
    }
    private void createThreadGroup(String key, List<Map<?, ?>> value) {
        if (this.threadGroups.size() < this.maxThreads) {  //TODO : find way to limit upstream (in the collector)
            ThreadGroupData tg = new ThreadGroupData(key);
            String firstTime = value.get(0).get("time").toString();
            tg.setStartTime(firstTime);
            for (Map<?, ?> queryMap : value) {
                BoltSamplerData bs = new BoltSamplerData(queryMap.get("time").toString(), queryMap.get("database").toString(),
                        queryMap.get("query").toString(), (Map)queryMap.get("queryParameters"));
                tg.addBoltSampler(bs);
            }
            this.threadGroups.add(tg);
        }
    }


    public void write(Path outputFilePath) {
        System.out.println("[threads : "+this.threadGroups.size()+"]");
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

        public BoltSamplerData(String time, String database, String query, Map<?,?> parameters) {
            this.database=database.equals("<none>") ? "" : database;
            this.query=query;
            this.name= (query.length() > 25) ? query.substring(0,24)+"..." : query;
            try {
                this.queryParameters=mapper.writeValueAsString(parameters); //Util.formatJson(parameters);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                this.queryParameters="Error parsing json";
            }
            this.txTimeout=60;
            this.startTime=Util.toEpoch(time);
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
            this.threadDelay = threadDelay;
        }

        public int getTxTimeout() {
            return txTimeout;
        }

        public void setTxTimeout(int txTimeout) {
            this.txTimeout = txTimeout;
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
            String clientType=source.split("\\t")[2];
            String clientHostPort=source.split("\\t")[4];
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
}
