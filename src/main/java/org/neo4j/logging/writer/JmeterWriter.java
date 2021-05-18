package org.neo4j.logging.writer;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.neo4j.logging.parser.LogLineParser;
import org.neo4j.logging.utils.Util;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.*;

import static java.util.stream.Collectors.groupingBy;

public class JmeterWriter {
    private int maxThreads=100;
    private int maxQueries=10000;
    private LogLineParser parser;
    private String logStartTime;
    private String logEndTime;
    private long timeSpanMillis;
    private List<ThreadGroupData> threadGroups=new ArrayList<>();

    public JmeterWriter(LogLineParser parser) {
        this.parser=parser;
        //get log start and end times
        try {
            this.logStartTime=parser.parse().findFirst().get()
                        .get("time").toString();
            long count = parser.parse().count();
            this.logEndTime=parser.parse()
                        .skip(count - 1).findFirst().get()
                        .get("time").toString();
        } catch(IOException e) {
            e.printStackTrace();
            System.exit(2);
        }
        this.timeSpanMillis=Util.toEpoch(this.logEndTime) - Util.toEpoch(this.logStartTime);
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
                        queryMap.get("query").toString(), queryMap.get("queryParameters").toString());
                tg.addBoltSampler(bs);
            }
            this.threadGroups.add(tg);
        }
    }


    public void write(Path outputFilePath) {
        System.out.println(this.threadGroups.size()+" "+this.threadGroups.get(0).getName() );
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init();
        Template t = velocityEngine.getTemplate("src/main/resources/jmx_layout.xml");

        VelocityContext context = new VelocityContext();


        context.put("threadGroups", this.threadGroups);

        StringWriter writer = new StringWriter();
        t.merge( context, writer );
        System.out.println(writer);
       // writer.

    }

    public class BoltSamplerData {
        private long startTime;
        private String database;
        private String query;
        private String queryParameters; //TODO : convert to xml format
        private String timerName="Wait";
        private String timerComment;
        private long threadDelay;
        private int txTimeout;

        public BoltSamplerData(String time, String database, String query, String parameters) {
            this.database=database;
            this.query=query;
            this.queryParameters=parameters;
            this.txTimeout=60;
            this.startTime=Util.toEpoch(time);
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
            this.delay= (Util.toEpoch(startTime) - Util.toEpoch(logStartTime)) /1000;
            //last until last query in the log
            this.duration=(timeSpanMillis /1000) - this.delay;
        }
        public void addBoltSampler(BoltSamplerData bs) {
            //measure time between new element and previous one
            long wait;
            String comment="";
            int count = this.queries.size();
            if (count == 0) {
                wait=0;
                comment="no wait";
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
