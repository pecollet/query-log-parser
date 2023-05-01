# query-log-parser [WIP]
A tool to parse and transform Neo4j query logs.
Allows generating JMeter test plans to replay queries.

## Usage
```
usage: query-log-parser.sh 
-i,--input <query.log>                            path of the query.log file to process. For Aura, the connection URL (ex: neo4j+s://ffffffff.databases.neo4j.io)
-o,--output [json|jmeter|standard]                output type

-start <YYYY-MM-DD HH:MM:SS>                      Start timestamp, UTC time. (Optional. Defaults to 5min ago)
-end <YYYY-MM-DD HH:MM:SS>                        End timestamp, UTC time. (Optional. Defaults to now)
-q,--query-limit <x>                              Limit to the number of queries to consider (Optional. default:1000)

-p,--aura-project <gcp_project>                   GCP project hosting the Aura logs (Optional. only used if -i points to a Aura URL)

-s,--jmeter-speed <x>                             Multiplier applied to all time intervals to speed up/slow down the JMeter replay (Optional. default:1)
-t,--jmeter-max-threads <x>                       Limit to the number of JMeter thread groups to create (Optional. default:100)
-snl,--jmeter-sampler-name-length <x>             Max length of Bolt Sampler (Optional. default:25)
-sto,--jmeter-sampler-tx-timeout <x>              Transaction timeout in seconds (Optional. default:60)
-sam,--jmeter-sampler-access-mode [READ|WRITE]    Access mode for tests against a cluster (Optional. default=WRITE)
-srr,--jmeter-sampler-record-results [true|false] Whether the Bolt response should be recorded by the Sampler [true|false] (Optional. default:true)
```

query.log can be in standard or json format (dbms.logs.query.format=json in neo4j 4.3). Format is auto-detected.

ex:
```
./query-log-parser.sh -i /path/to/query.log -o jmeter

./query-log-parser.sh -i /path/to/query.log -o json

./query-log-parser.sh -i "neo4j+s://12345.databases.neo4j.io" -o standard -p "aws-aura-customer"
```

**outputs** 

In the same directory as the input file, with an extra suffix :
* json : query.log in json format ("_inputfile_.json")
* standard : query.log in standard format ("_inputfile_.std")
* jmeter : a JMeter test plan file to replay the query log ("_inputfile_.jmx")

For Aura exports, the file will be generated in a local subdirectory named "output".

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
