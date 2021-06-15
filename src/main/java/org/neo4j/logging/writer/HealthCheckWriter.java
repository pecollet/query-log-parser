package org.neo4j.logging.writer;

import org.neo4j.logging.parser.LogLineParser;
import org.neo4j.logging.utils.BoundedPriorityQueue;
import org.neo4j.logging.utils.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.groupingBy;

public class HealthCheckWriter {
    private LogLineParser parser;
    private Map<String, QueryStats> stats=new HashMap<>();

    private GlobalStats globalStats;

    private static Comparator<QueryStats> compareByTotalTime = Comparator.comparing(qs -> qs.getTotalMillis(), Comparator.reverseOrder());

    public HealthCheckWriter(LogLineParser parser) {
        this.parser = parser;
        this.globalStats=new GlobalStats(5, 2000, 10000);
    }

    public HealthCheckWriter parse() throws IOException {
        parser.parse()
                .filter(line -> "success".equals(line.get("event").toString()))
                //.collect(groupingBy(line -> line.get("database").toString() + "_" + line.get("query").toString())) //group by db+query
                .forEach(logEntry -> addToStats(logEntry));
        return this;
    }

    public void write(Path outputFilePath, int topK) throws IOException {
        //write global stats
        StandardLogLineWriter writer = new StandardLogLineWriter();
        try {
            Files.write(outputFilePath,
                    globalStats.write().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.TRUNCATE_EXISTING
            );

        } catch (IOException e) {
            e.printStackTrace();
        }

        //"query log analyzer"-like stats
        QueryStats[] qs = this.stats.values().stream()
                .sorted(compareByTotalTime)
                .limit(topK)
                .toArray(QueryStats[]::new);
        IntStream.range(0, qs.length)
                .forEach(idx -> {
                    String output="";
                    output+="mostCostly_cypher["+idx+"]="+qs[idx].cypher.replaceAll("\n"," ")+'\n';
                    output+="mostCostly_totalTime["+idx+"]="+qs[idx].totalMillis +'\n';
                    output+="mostCostly_minTime["+idx+"]="+qs[idx].minTime +'\n';
                    output+="mostCostly_maxTime["+idx+"]="+qs[idx].maxTime +'\n';
                    output+="mostCostly_count["+idx+"]="+qs[idx].count +'\n';
                    output+="mostCostly_totalMem["+idx+"]="+qs[idx].totalMem+'\n';
                    try {
                        Files.write(outputFilePath,
                                output.getBytes(StandardCharsets.UTF_8),
                                StandardOpenOption.APPEND
                        );
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }

    private void addToStats(Map<?, ?> logEntry) {
        QueryStats qs;
        String database = logEntry.get("database").toString();
        String cypher = logEntry.get("query").toString();
        String key = database + "_" + cypher;
        if (stats.containsKey(key)) {
            qs=stats.get(key);
        } else {
            qs=new QueryStats(database, cypher);
        }
        qs.addQuery(logEntry);
        stats.put(key, qs);

        //globalStats
        globalStats.addQuery(logEntry);

    }

    private class GlobalStats {
        private int threshold1=2000;
        private int threshold2=10000;
        private int topK=5;

        private Comparator<Map> queryComparator = Comparator.comparing(m -> (Integer)m.get("elapsedTimeMs"), Comparator.reverseOrder());

        private long count=0;
        private long countBetweenthreshold1And2=0;
        private long countOverThreshold2=0;
        private double totalTimeMillis=0;
        private long failedCount=0;
        private double totalFailedTimeMillis=0;

        private LinkedHashMap<String, Long> protocols=new LinkedHashMap<>();
        private LinkedHashMap<String, Long> drivers=new LinkedHashMap<>();
        private LinkedHashMap<String, Long> clients=new LinkedHashMap<>();

        private BoundedPriorityQueue<Map> topKSuccessfulQueries;
        private BoundedPriorityQueue<Map> topKFailedQueries;

        public GlobalStats(int topK, int threshold1, int threshold2) {
            this.topK=topK;
            if (threshold2 > threshold1) {
                this.threshold1 = threshold1;
                this.threshold2 = threshold2;
            } else {System.out.println("threshold2 should be > to threshold1. Using defaults.");}
            this.topKSuccessfulQueries= new BoundedPriorityQueue(this.topK, this.queryComparator);
            this.topKFailedQueries=new BoundedPriorityQueue(this.topK, this.queryComparator);
        }
        public void addQuery(Map logEntry) {
            String event = logEntry.get("event").toString();
            if ("success".equals(event)) {
                this.topKSuccessfulQueries.add(logEntry);
            } else if ("fail".equals(event)) {
                this.topKFailedQueries.add(logEntry);
            }
            addQueryTime((Integer)logEntry.get("elapsedTimeMs"), event);

            addCountsPerType(logEntry.get("source").toString());
        }
        private void addCountsPerType(String source) {
            Map<String, String> sourceMap=splitSource(source);

            if (sourceMap.containsKey("protocol")) {
                String protocol= sourceMap.get("protocol");
                Long count = this.protocols.get(protocol);
                if (count == null) {
                    this.protocols.put(protocol, 1L);
                } else {
                    this.protocols.put(protocol, count + 1);
                }
            }
            if (sourceMap.containsKey("client")) {
                String client= sourceMap.get("client");
                Long count = this.clients.get(client);
                if (count == null) {
                    this.clients.put(client, 1L);
                } else {
                    this.clients.put(client, count + 1);
                }
            }
            if (sourceMap.containsKey("driver")) {
                String driver= sourceMap.get("driver");
                Long count = this.drivers.get(driver);
                if (count == null) {
                    this.drivers.put(driver, 1L);
                } else {
                    this.drivers.put(driver, count + 1);
                }
            }
        }
        private void addQueryTime(long queryTime, String event) {
            if ("success".equals(event)) {
                this.count++;
                this.totalTimeMillis += queryTime;

                if (queryTime >= this.threshold2) {
                    this.countOverThreshold2++;
                } else {
                    if (queryTime >= this.threshold1) this.countBetweenthreshold1And2++;
                }
            }
            if ("fail".equals(event)) {
                this.failedCount++;
                this.totalFailedTimeMillis+=queryTime;
            }
        }

        public String write() {
            String output= "queryCount="+ this.count+'\n';
            output+="above10sCount="+this.countOverThreshold2+'\n';
            output+="above2sCount="+this.countBetweenthreshold1And2+'\n';
            output+="queryAvg="+ (1.0 *this.totalTimeMillis / this.count )+'\n';
            output+="queryErrorCount="+this.failedCount+'\n';
            int hasErrors=0;
            if (this.failedCount > 0 ) {
                hasErrors=1;
                output += "queryErrorAvg=" + (1.0 * this.totalFailedTimeMillis / this.failedCount) + '\n';
            }
            output+="queryLogErrors="+hasErrors+'\n';

            String types_labels = String.join(",", this.protocols.keySet());
            String types_counts = String.join(",", this.protocols.values().toString());
            String clients_labels = String.join(",", this.drivers.keySet());
            String clients_counts = String.join(",", this.drivers.values().toString());
            output+="clients_labels="+clients_labels+'\n';
            output+="clients_counts="+clients_counts+'\n';
            output+="types_labels="+types_labels+'\n';
            output+="types_counts="+types_counts+'\n';

            for (int i=0; i<this.topK ; i++) {
                Map q = this.topKSuccessfulQueries.poll();
                output+="top5SuccessfulQueries["+i+"]="+q.get("raw").toString().replaceAll("\n", " ")+'\n';
            }

            output+='\n';
            for (int i=0; i<this.topKFailedQueries.size() ; i++) {
                Map q = this.topKFailedQueries.poll();
                output+="top5ErrorQueries["+i+"]="+q.get("raw").toString().replaceAll("\n", " ")+'\n';
            }
            output+='\n';
            return output;
        }
    }

    private class QueryStats {
        private String cypher;
        private String database;
        private long minLogTime=0;
        private long maxLogTime=0;
        private long count=0;

        private long totalMillis=0;
        private long minTime=0;
        private long maxTime=0;

        private long totalCPU=0;
        private long minCPU=0;
        private long maxCPU=0;

        private long totalPlanning=0;
        private long minPlanning=0;
        private long maxPlanning=0;

        private long totalWaiting=0;
        private long minWaiting=0;
        private long maxWaiting=0;

        private long totalMem=0;
        private long minMem=0;
        private long maxMem=0;

        private long totalPcHits=0;
        private long totalPcFaults=0;
        private double totalPcHitRatio=0;
        private double minPcHitRatio=0;
        private double maxPcHitRatio=0;

        private HashSet<String> protocols=new HashSet<>();
        private HashSet<String> drivers=new HashSet<>();
        private HashSet<String> clients=new HashSet<>();

        public QueryStats(String db, String cypher) {
            this.database = db;
            this.cypher = cypher;
        }
        public void addQuery(Map logEntry) {
            this.count++;
            addSource((String)logEntry.get("source"));
            addLogTime(Util.toEpoch((String)logEntry.get("time")));

            addTimings((Integer)logEntry.get("elapsedTimeMs"), (Integer)logEntry.get("planning"),
                    (Integer)logEntry.get("cpu"), (Integer)logEntry.get("waiting"));
            addMemStats((Integer)logEntry.get("allocatedBytes"), (Integer)logEntry.get("pageHits"),
                    (Integer)logEntry.get("pageFaults"));
        }
        private void addSource(String source) {
            Map<String, String> sourceMap=splitSource(source);

            if (sourceMap.containsKey("protocol")) this.protocols.add(sourceMap.get("protocol"));
            if (sourceMap.containsKey("client")) this.clients.add(sourceMap.get("client"));
            if (sourceMap.containsKey("driver")) this.drivers.add(sourceMap.get("driver"));
        }

        private void addLogTime(long logTime) {
            if (this.minLogTime == 0 && this.maxLogTime == 0) {
                this.minLogTime = logTime;
                this.maxLogTime = logTime;
            } else {
                if (logTime < this.minLogTime) this.minLogTime = logTime;
                if (logTime > this.maxLogTime) this.maxLogTime = logTime;
            }
        }


        private void addTimings(Integer queryTime, Integer planningTime, Integer cpuTime, Integer waitingTime) {

            if (planningTime != null) {
                this.totalPlanning += planningTime;
                if (this.maxPlanning == 0 && this.minPlanning == 0) {
                    this.maxPlanning = planningTime;
                    this.minPlanning = planningTime;
                } else {
                    if (planningTime < this.minPlanning) this.minPlanning = planningTime;
                    if (planningTime > this.maxPlanning) this.maxPlanning = planningTime;
                }
            }
            if (cpuTime != null) {
                this.totalCPU += cpuTime;
                if (this.maxCPU == 0 && this.minCPU == 0) {
                    this.maxCPU = cpuTime;
                    this.minCPU = cpuTime;
                } else {
                    if (cpuTime < this.minCPU) this.minCPU = cpuTime;
                    if (cpuTime > this.maxCPU) this.maxCPU = cpuTime;
                }
            }
            if (waitingTime != null) {
                this.totalWaiting += waitingTime;
                if (this.maxWaiting == 0 && this.minWaiting == 0) {
                    this.maxWaiting = waitingTime;
                    this.minWaiting = waitingTime;
                } else {
                    if (waitingTime < this.minWaiting) this.minWaiting = waitingTime;
                    if (waitingTime > this.maxWaiting) this.maxWaiting = waitingTime;
                }
            }

            this.totalMillis += queryTime;
            //set min & max
            if (this.maxTime == 0 && this.minTime == 0) {
                this.maxTime = queryTime;
                this.minTime = queryTime;
            } else {
                if (queryTime < this.minTime) this.minTime = queryTime;
                if (queryTime > this.maxTime) this.maxTime = queryTime;
            }

        }
        private void addMemStats(Integer allocatedBytes, Integer pageHits, Integer pageFaults){
            this.totalMem += allocatedBytes;

            if (this.maxMem == 0 && this.minMem == 0) {
                this.maxMem = allocatedBytes;
                this.minMem = allocatedBytes;
            } else {
                if (allocatedBytes < this.minMem) this.minMem = allocatedBytes;
                if (allocatedBytes > this.maxMem) this.maxMem = allocatedBytes;
            }

            if (pageHits != null && pageFaults != null && (pageHits > 0 || pageFaults > 0)) {
                this.totalPcHits += pageHits;
                this.totalPcFaults += pageFaults;
                double hitRatio = 100.0 * pageHits / (pageHits + pageFaults) ;
                this.totalPcHitRatio = 100.0 * this.totalPcHits / (this.totalPcHits + this.totalPcFaults) ;
                if (this.maxPcHitRatio == -1 && this.minPcHitRatio == -1) {
                    this.maxPcHitRatio = hitRatio;
                    this.minPcHitRatio = hitRatio;
                } else {
                    if (hitRatio < this.minPcHitRatio) this.minPcHitRatio = hitRatio;
                    if (hitRatio > this.maxPcHitRatio) this.maxPcHitRatio = hitRatio;
                }
            }

        }
        public long getTotalMillis() {return this.totalMillis;}

    }

    private static Map<String, String> splitSource(String source) {
        Map<String, String> tmp = new HashMap<>();
        if (source.startsWith("embedded-session")) {
            //embedded-session
            tmp.put("protocol","embedded");
        } else if (source.startsWith("server-session")) {
            //server-session	http	10.176.6.112	/db/vzbulh202012/tx/commit
            tmp.put("protocol",source.split("\t")[1]);
            tmp.put("client",source.split("\t")[2]);
        } else {
            //bolt-session	bolt	neo4j-python/4.1.1 Python/3.6.8-final-0 (linux)		client/10.176.6.112:57788	server/10.176.6.33:7687>
            tmp.put("protocol",source.split("\t")[1]);
            tmp.put("driver",source.split("\t")[2]);
            tmp.put("client",source.split("\t")[4].replaceFirst("^client/", ""));
        }
        return tmp;
    }
}
