# query-log-parser [WIP]
A tool to parse and transform Neo4j query logs.
Allows generating JMeter test plans to replay queries.

## Usage
```
usage: query-log-parser.sh 
-i,--input <query.log>    query.log file path
-o,--output <output_file>   output [json|jmeter|hc|standard]
```

query.log can be in standard or json format (dbms.logs.query.format=json in neo4j 4.3). Format is auto-detected.

ex:
```
./query-log-parser.sh -i "neo4j+s://dbid.databases.neo4j.io" -o standard -p "aws-aura-customer"
```

**outputs** 
* json : query.log in json format ("_inputfile_.json.log")
* standard : query.log in standard format ("_inputfile_.std.log")
* jmeter : a JMeter test plan file to replay the query log ("_inputfile_.jmx")
* hc : statistics for the Health Check ("_inputfile_.hc.properties")

## JMeter test plan

The feature aims to generate a valid test plan xml file, with :
- a Bolt Connection Element 
- Thread Groups for each client identified in the source field of the query.log, configured to run in parallel.
- within each thread group, a Bolt Sampler for each query (sent via bolt), scheduled according to the query timestamps with Timers. 

### options
- speedFactor <decimal> : multiplier applied to all time intervals to speed up/slow down the replay (default=1)
- maxThreads <integer> : limit to the number of thread groups to create, and to the number of query sources to consider (default=100). Queries beyond that limit will be discarded.
- maxQueries <integer> : limit to the number of queries to consider (default=1000).
- samplerNameMaxLength <integer> : max length of Bolt Sampler "test name" (default=25). The Bolt Sampler is named with the truncated cypher query. That helps distinguish queries in JMeter listeners.
- samplerTxTimeout <integer> : transaction timeout in seconds, used by the JMeter's Neo4j driver (default=60). Applies to all samplers.
- samplerAccessMode READ|WRITE : Access mode for tests against a cluster (default=WRITE). Applies to all samplers.
- samplerRecordQueryResults true|false : whether the Bolt response should be recorded by the Sampler (default=true). Slows tests down, but helps debug. Applies to all samplers.
- [TODO] filters <some format> : filters on various fields (database, user, timestamp, duration, protocol, client, driver, metadata, regex against cypher) to select/exclude specific queries
