# query-log-parser [WIP]
A tool to parse and transform Neo4j query logs.
Allows generating JMeter test plans to replay queries.

## Usage
```
usage: java -cp query-log-parser-1.0-SNAPSHOT.jar org.neo4j.logging.parser.QueryLogParser 
-i,--input <query.log>    query.log file path
-o,--output <output_file>   output [json|jmeter|hc|standard]
```

query.log can be in standard or json format (dbms.logs.query.format=json in neo4j 4.3). Format is auto-detected.

**outputs** 
* json : query.log in json format ("_inputfile_.json.log")
* standard : query.log in standard format ("_inputfile_.std.log")
* jmeter : a JMeter test plan file to replay the query log ("_inputfile_.jmx")
* hc : [TODO] statistics for the Health Check

## JMeter test plan

The feature aims to generate a valid test plan xml file, with :
- a Bolt Connection Element 
- Thread Groups for each client identified in the source field of the query.log, configured to run in parallel.
- within each thread group, a Bolt Sampler for each query, scheduled according to the query timestamps 
