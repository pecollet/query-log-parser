package org.neo4j.logging;

import org.apache.commons.cli.*;
import org.neo4j.logging.aura.AuraFileLoggingParser;
import org.neo4j.logging.aura.AuraGcloudLoggingParser;
import org.neo4j.logging.parser.JsonLogParser;
import org.neo4j.logging.parser.LogLineParser;
import org.neo4j.logging.parser.StandardLogParser;
import org.neo4j.logging.writer.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class QueryLogParser {

    public enum QueryLogType
    {
        JSON,
        STANDARD,
        AURA
    }
    public enum OutputFormat
    {
        JSON,
        STANDARD,
        JMETER,
        HC
    }
    private static final Options options = new Options();
    private static final CommandLineParser parser = new DefaultParser();
    private static final HelpFormatter formatter = new HelpFormatter();
    private static CommandLine cmd=null;

    private static LogLineWriter logLineWriter;
    private static Path outputFilePath;
    private static Pattern aura_url_pattern = Pattern.compile("^neo4j\\+s://(?<dbid>[a-f0-9]+)\\.databases\\.neo4j\\.io$");


    public static void main(String[] args) {

        //process arguments
        Option input = new Option("i", "input", true, "path of the query.log file to process. For Aura, the connection URL (ex: neo4j+s://ffffffff.databases.neo4j.io).");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option("o", "output", true, "output to produce : [json|jmeter|hc|standard]");
        output.setRequired(true);
        options.addOption(output);

        options.addOption(new Option("s", "jmeter-speed", true,  "multiplier applied to all time intervals to speed up/slow down the JMeter replay (default:1)"));
        options.addOption(new Option("q", "query-limit", true,  "limit to the number of queries to consider (default:1000)"));
        options.addOption(new Option("t", "jmeter-max-threads", true,  "limit to the number of JMeter thread groups to create (default:100)"));
        options.addOption(new Option("snl", "jmeter-sampler-name-length", true,  "max length of Bolt Sampler (default:25)"));
        options.addOption(new Option("sto", "jmeter-sampler-tx-timeout", true,  "transaction timeout in seconds (default:60)"));
        options.addOption(new Option("sam", "jmeter-sampler-access-mode", true,  "Access mode for tests against a cluster [READ|WRITE] (default=WRITE)"));
        options.addOption(new Option("srr", "jmeter-sampler-record-results", true,  "whether the Bolt response should be recorded by the Sampler [true|false] (default:true)"));

        options.addOption(new Option("p", "aura-project", true,  "Name of the GCloud project / AWS account"));
        options.addOption(new Option("start", "aura-start-time", true,  "Start timestamp, in the 'YYYY-MM-DD HH:MM:SS' format, UTC time. Default to 5min ago."));
        options.addOption(new Option("end", "aura-end-time", true,  "End timestamp, in the 'YYYY-MM-DD HH:MM:SS' format, UTC time. Defaults to now."));
        //TODO : allow multiple input files (-i <dir> and we fetch all query.log* in it)
        // apply limit to all


        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("query-log-parser.sh", options, true);
            System.exit(1);
        }
        String inputFile = cmd.getOptionValue("input");
        OutputFormat outputOption = OutputFormat.valueOf(cmd.getOptionValue("output").toUpperCase(Locale.ROOT));

        Matcher matcher = aura_url_pattern.matcher(inputFile);
        LogLineParser logLineParser=null;
        String auraDbId=null;
        String outputFile="tmp";

        if (matcher.find()) {
            auraDbId=matcher.group("dbid");
            String project = cmd.getOptionValue("aura-project");
            if (project == null) {
                System.out.println("Please specify a GCP project with option -p.");
                System.exit(2);
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String start_ts=cmd.getOptionValue("aura-start-time");
            String end_ts=cmd.getOptionValue("aura-end-time");
            if (start_ts == null && end_ts != null) {  //both null is ok
                    System.out.println("Start time required if end time is specified.");
                    System.exit(2);
            }
            if (end_ts == null) { //default to now
                Instant instant = Instant.now ();
                ZonedDateTime zdt = ZonedDateTime.ofInstant ( instant , ZoneId.of ( "UTC" ) );
                end_ts = zdt.format(formatter);
                if (start_ts == null) { //default to 5min ago
                    start_ts = zdt.minus ( 5 , ChronoUnit.MINUTES ).format(formatter);
                }
            }
            if (end_ts.compareTo(start_ts) < 0) {
                System.out.println("Start time should be before end time.");
                System.exit(2);
            }
            try {
                System.out.println("Requesting Aura logs from "+start_ts+" to "+end_ts);
                //" AND timestamp > \"2022-11-16T11\" AND timestamp <= \"2022-11-16T12\""
                logLineParser = new AuraGcloudLoggingParser(project, String.format(" AND timestamp > \"%s\" AND timestamp <= \"%s\"", start_ts.replace(' ', 'T'), end_ts.replace(' ', 'T')), auraDbId);
            } catch (Exception e) {
                System.out.println("Failed to connect to GCP Logging");
                e.printStackTrace();
                System.exit(2);
            }
            outputFile=outputFileName(String.format("%s_%s_%s",auraDbId, start_ts.replaceAll("[-: ]", ""), end_ts.replaceAll("[-: ]", "")), outputOption);
        } else {
            //open input file
            System.out.println("Loading file : "+inputFile+"...");
            Path inputFilePath = Path.of(inputFile);
            logLineParser=selectParser(inputFilePath);
            outputFile=outputFileName(inputFile, outputOption);
        }

        //create output file
        try {
            if (!Files.exists(Path.of(outputFile))) {
                outputFilePath = Files.createFile(Path.of(outputFile));
            } else {
                outputFilePath = Path.of(outputFile);
                //TODO: truncate file
            }
        } catch(IOException e) {
            System.out.println("Failed to write to file "+outputFilePath+" ("+e.getClass()+")");
            //e.printStackTrace();
            System.exit(2);
        }

        //choose action depending on output selected
        switch (outputOption) {
            case JSON:
                logLineWriter=new JsonLogLineWriter();
                translate(outputFilePath, logLineParser, logLineWriter);
                break;
            case STANDARD:
                logLineWriter=new StandardLogLineWriter();
                translate(outputFilePath, logLineParser, logLineWriter);
                break;
            case JMETER:
                HashMap<String, Object> config = cmdLineOptionsToJmeterConfig();
                try {
                    new JmeterWriter(logLineParser)
                            .withConfig(config)
                            .parse()
                            .write(outputFilePath);
                } catch(Exception e) {
                    e.printStackTrace();
                    System.exit(2);
                }
                break;
            case HC:
                try {
                    new HealthCheckWriter(logLineParser)
                            .parse()
                            .write(outputFilePath, 10);
                } catch(Exception e) {
                    e.printStackTrace();
                    System.exit(2);
                }
                break;
        }

    }
    private static HashMap<String, Object> cmdLineOptionsToJmeterConfig() {
        HashMap<String, Object> config = new HashMap<>();

        String maxQueries = cmd.getOptionValue("query-limit");
        config.put("maxQueries", maxQueries == null ? 1000 : Integer.valueOf(maxQueries));

        String samplerNameMaxLength = cmd.getOptionValue("jmeter-sampler-name-length");
        config.put("samplerNameMaxLength", samplerNameMaxLength == null ? 25 : Integer.valueOf(samplerNameMaxLength));

        String maxThreads = cmd.getOptionValue("jmeter-max-threads");
        config.put("maxThreads", maxThreads == null ? 100 : Integer.valueOf(maxThreads));

        String speed = cmd.getOptionValue("jmeter-speed");
        config.put("speedFactor", speed == null ? 1.0 : Double.valueOf(speed));

        String samplerTxTimeout = cmd.getOptionValue("jmeter-sampler-tx-timeout");
        config.put("samplerTxTimeout", samplerTxTimeout == null ? 60 : Integer.valueOf(samplerTxTimeout));

        String samplerAccessMode = cmd.getOptionValue("jmeter-sampler-access-mode");
        config.put("samplerAccessMode", samplerAccessMode == null ? "WRITE" : samplerAccessMode);

        String samplerRecordQueryResults = cmd.getOptionValue("jmeter-sampler-record-results");
        config.put("samplerRecordQueryResults", samplerRecordQueryResults == null ? "true" : samplerRecordQueryResults);
        return config;
    }
    private static LogLineParser selectParser(Path path) {
        Optional<String> firstLine=Optional.empty();
        QueryLogType detectedType=QueryLogType.STANDARD;
        LogLineParser parser;
        try {
            firstLine=Files.readString(path).lines().findFirst();
        } catch(IOException e) {
            System.out.println("Failed to read from file "+path+" ("+e.getClass()+")");
            //e.printStackTrace();
            System.exit(2);
        }
        if (firstLine.isPresent()) {
            if (firstLine.get().startsWith("{")) {          //4.3+ json format
                parser = new JsonLogParser(path);
                detectedType = QueryLogType.JSON;
            } else if (firstLine.get().startsWith("[")) {   //aura query.logs
                parser = new AuraFileLoggingParser(path);
                detectedType = QueryLogType.AURA;
            } else {
                parser = new StandardLogParser(path);
                detectedType= QueryLogType.STANDARD;
            }
        } else {
            System.out.println("No line found");
            parser=null;
            System.exit(3);
        }
        System.out.println("Input type="+detectedType.name());
        return parser;
    }

    private static String outputFileName(String inputFile, OutputFormat outputOption) {
        String outputFileName="";
        switch (outputOption) {
            case JSON:
                outputFileName= inputFile.replaceFirst("\\.log$",".json")+".log";
                break;
            case STANDARD:
                outputFileName= inputFile.replaceFirst("\\.log$",".std")+".log";
                break;
            case JMETER:
                outputFileName= inputFile+".jmx";
                break;
            case HC:
                outputFileName= inputFile+".hc.properties";
                break;
        }
        System.out.println("Output file : "+outputFileName);
        return outputFileName;
    }

    private static void translate(Path outputFilePath, LogLineParser parser, LogLineWriter writer) {
        try {
            //logLineParser.parse().forEach(e-> System.out.println(e));
            parser.parse()
                    .forEach(m -> {
                        try {
                            Files.write(outputFilePath,
                                    writer.writeLine((Map<String, Object>) m).getBytes(StandardCharsets.UTF_8),
                                    StandardOpenOption.APPEND
                            );
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(4);
        }
    }
}
