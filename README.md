# query-log-parser
a tool to parse and transform Neo4j query logs

## Usage
```
usage: java -cp query-log-parser-1.0-SNAPSHOT.jar org.neo4j.logging.parser.QueryLogParser 
-i,--input <arg>    query.log file path
-o,--output <arg>   output [json|jmeter|hc|standard]
```

query.log can be in standard or json format (auto-detected)

**outputs** 
* json : query.log in json format
* standard : query.log in standard format
* jmeter : a JMeter test plan file
* hc : [TODO] statistics for the Health Check

Whichever output is selected the output file is names "_inputFile_.out".

## JMeter test play

The feature aims to generate a valid test plan xml file, with :
- a Bolt Connection Element 
- Thread Groups for each client identified in the source field of the query.log, configured to run in parallel.
- within each thread group, a Bolt Sampler for each query, scheduled according to the query timestamps 