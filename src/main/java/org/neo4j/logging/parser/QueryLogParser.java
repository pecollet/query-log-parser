package org.neo4j.logging.parser;

import org.apache.commons.cli.*;
import org.neo4j.logging.writer.JmeterWriter;
import org.neo4j.logging.writer.JsonLogLineWriter;
import org.neo4j.logging.writer.LogLineWriter;
import org.neo4j.logging.writer.StandardLogLineWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;


public class QueryLogParser {

    public enum QueryLogType
    {
        JSON,
        STANDARD
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


    public static void main(String[] args) {

        //process arguments
        Option input = new Option("i", "input", true, "input file path");
        input.setRequired(true);
        options.addOption(input);

        Option output = new Option("o", "output", true, "output [json|jmeter|hc|standard]");
        output.setRequired(true);
        options.addOption(output);

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("query-log-parser", options);
            System.exit(1);
        }
        String inputFile = cmd.getOptionValue("input");
        OutputFormat outputOption = OutputFormat.valueOf(cmd.getOptionValue("output").toUpperCase(Locale.ROOT));

        //open input file
        System.out.println("Loading file"+inputFile+"...");
        Path inputFilePath = Path.of(inputFile);
        String outputFile=inputFile+".out";

        LogLineParser logLineParser=selectParser(inputFilePath);

        //create output file
        try {
            if (!Files.exists(Path.of(outputFile))) {
                outputFilePath = Files.createFile(Path.of(outputFile));
            } else {
                outputFilePath = Path.of(outputFile);
            }
        } catch(IOException e) {
            e.printStackTrace();
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
                try {
                    new JmeterWriter(logLineParser)
                            .withMaxQueries(1000)
                            .parse()
                            .write(outputFilePath);
                } catch(IOException e) {
                    e.printStackTrace();
                    System.exit(2);
                }
                break;
            case HC:
                //TODO : Health check output
                break;
        }

    }

    private static LogLineParser selectParser(Path path) {
        Optional<String> firstLine=Optional.empty();
        QueryLogType detectedType=QueryLogType.STANDARD;
        LogLineParser parser;
        try {
            firstLine=Files.readString(path).lines().findFirst();
        } catch(IOException e) {
            e.printStackTrace();
            System.exit(2);
        }
        if (firstLine.isPresent()) {
            if (firstLine.get().startsWith("{")) {
                parser = new JsonParser(path);
                detectedType= QueryLogType.JSON;
            } else {
                parser = new StandardParser(path);
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

    private static void translate(Path outputFilePath, LogLineParser parser, LogLineWriter writer) {
        try {
            //logLineParser.parse().forEach(e-> System.out.println(e));
            parser.parse()
                    .forEach(m -> {
                        try {
                            Files.write(outputFilePath,
                                    writer.writeLine(m).getBytes(StandardCharsets.UTF_8),
                                    StandardOpenOption.APPEND
                            );
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        } catch(IOException e) {
            e.printStackTrace();
            System.exit(4);
        }
    }
}
