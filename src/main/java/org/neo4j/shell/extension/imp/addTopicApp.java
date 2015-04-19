package org.neo4j.shell.extension.imp;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.kernel.impl.util.StringLogger;
import org.neo4j.shell.*;
import org.neo4j.shell.impl.AbstractApp;
import org.neo4j.shell.AppCommandParser;
import org.neo4j.shell.kernel.GraphDatabaseShellServer;
import org.neo4j.shell.extension.util.*;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;


public class addTopicApp extends AbstractApp {

	// Config options (util Config.java from Neo4J shell tools by MH)
    {
         addOptionDefinition( "i", new OptionDefinition( OptionValueType.MUST, "Input CSV/TSV file" ) );
         addOptionDefinition( "o", new OptionDefinition( OptionValueType.MUST,"Output CSV/TSV file" ) );
         addOptionDefinition("b", new OptionDefinition(OptionValueType.MUST, "Batch Size default " + Config.DEFAULT_BATCH_SIZE));
         addOptionDefinition( "d", new OptionDefinition( OptionValueType.MUST, "Delimeter, default is comma ',' " ) );
    }

    private ExecutionEngine engine;		// Initial Cypher engine
    protected ExecutionEngine getEngine() {
        if (engine==null) engine=new ExecutionEngine(getServer().getDb(), StringLogger.SYSTEM);		// connect to running server
        return engine;
    }

    @Override
    public String getName() {
        return "add-topic";
    }

    @Override
    public GraphDatabaseShellServer getServer() {
        return (GraphDatabaseShellServer) super.getServer();
    }

    @Override
    public Continuation execute(AppCommandParser parser, Session session, Output out) throws Exception {
    	// Parse command
    	Config config = Config.fromOptions(parser);
        char delim = delim(parser.option("d", ","));
        int batchSize = Integer.parseInt(parser.option("b", String.valueOf(Config.DEFAULT_BATCH_SIZE)));


        
        String inputFileName = parser.option("i", null);
//        String inputFileName = "/Users/zys/projects/javaworkspace/test_jython/review_with_topic.csv";
        String outputFileName = parser.option("o", null);
//        File outputFile = new File("test.csv");
        File outputFile = new File(outputFileName);
        File inputFile = new File(inputFileName);
 
        String query = Config.extractQuery(parser);
        out.println(String.format("Query: %s infile %s delim '%s' outfile %s batch-size %d",
                query,name(inputFileName),delim,name(outputFile),batchSize));
        
        CSVWriter writer = createWriter(outputFile, config);
        CSVReader reader = createReader(inputFile, config);
        
        
        // Execute client command
        if (writer==null) {		// Read returned results
        	//String query = "match(n: review {review_id: {review_id}}) set n+={topic:{topic}}";
        	int count = executeOnInput(reader, query, config, new ProgressReporter(out));    
        	out.println("Execution on "+count+" rows of data.");           
        	if (reader!=null) reader.close();
        } 
        else {					// Output queried data into csv file for ML analysis
        	int count = execute(query, writer);
            out.println("Execution on "+count+" rows of data.");
            if (writer!=null) writer.close();
            
            // Run Graphlab ML toolkit in Python script
            StringBuffer retlog = runGL.excuteCmd(out);
            out.println(retlog.toString());            
        }
        return Continuation.INPUT_COMPLETE;
    }

    private String name(Object file) {
         if (file==null) return "(none)";
         return file.toString();
    }

     private char delim(String value) {
         if (value.length()==1) return value.charAt(0);
         if (value.contains("\\t")) return '\t';
         if (value.contains(" ")) return ' ';
         throw new RuntimeException("Illegal delimiter '"+value+"'");
     }

     private CSVWriter createWriter(File outputFile, Config config) throws IOException {
         if (outputFile==null) return null;
         FileWriter file = new FileWriter(outputFile);
         return new CSVWriter(file,config.getDelimChar());
     }

     private CSVReader createReader(File inputFile, Config config) throws FileNotFoundException {
         if (inputFile==null) return null;
         FileReader file = new FileReader(inputFile);
         return new CSVReader(file,config.getDelimChar());
     }

     //// execute on output query
     private int execute(String query, CSVWriter writer) {
         ExecutionResult result = getEngine().execute(query);
         return writeResult(result, writer, true);
     }
     
     private int writeResult(ExecutionResult result, CSVWriter writer, boolean first) {
         if (writer==null) return IteratorUtil.count(result);
         String[] cols = new String[result.columns().size()];
         result.columns().toArray(cols);
         String[] data = new String[cols.length];
         if (first) {
             writer.writeNext(cols);	// skip header
         }

         int count=0;
         for (Map<String, Object> row : result) {
             writeRow(writer, cols, data, row);
             count++;
         }
         return count;
     }

     private void writeRow(CSVWriter writer, String[] cols, String[] data, Map<String, Object> row) {
         for (int i = 0; i < cols.length; i++) {
             String col = cols[i];             
             data[i]= toString(row, col);
             
             // Data clean before Ouput 
             if(col.equalsIgnoreCase("text")) {
            	 data[i]=data[i].replaceAll("[^ A-Za-z ]", "");
            	 data[i]=data[i].replaceAll("\\b\\w{1,3}\\b\\s?", "");
             }             
         }
         writer.writeNext(data);
     }

     //// execute on input query ( based on batch read module From Neo4J shell tools by MH)
     private int executeOnInput( CSVReader reader, String query, Config config, ProgressReporter reporter) throws IOException {
         Map<String, Object> params = createParams(reader);
         Map<String, Type> types = extractTypes(params);
         Map<String, String> replacements = computeReplacements(params, query); 
         String[] input;
         int outCount = 0;
         StringBuffer deb = new StringBuffer();
         
         // Neo4J batch transaction
         try (BatchTransaction tx = new BatchTransaction(getServer().getDb(),config.getBatchSize(),reporter) ){	
             while ((input = reader.readNext()) != null) { 
                 Map<String, Object> queryParams = update(params, types, input);                 
                 String newQuery = applyReplacements(query, replacements, queryParams); // in case input file header is different to Cypher default format
                 deb.append(input);
                 ExecutionResult result = getEngine().execute(newQuery,queryParams);
                 ProgressReporter.update(result.getQueryStatistics(), reporter);
                 
                 tx.increment();
                 outCount++;
             }
         }
         return outCount;
     }

     private Map<String, Type> extractTypes(Map<String, Object> params) {
         Map<String,Object> newParams = new LinkedHashMap<>();
         Map<String,Type> types = new LinkedHashMap<>();
         for (String header : params.keySet()) {
             if (header.contains(":")) {
                 String[] parts = header.split(":");
                 newParams.put(parts[0],null);
                 types.put(parts[0],Type.fromString(parts[1]));
             } else {
                 newParams.put(header,null);
                 types.put(header,Type.STRING);
             }
         }
         params.clear();
         params.putAll(newParams);
         return types;
     }

     private String applyReplacements(String query, Map<String, String> replacements, Map<String, Object> queryParams) {
         for (Map.Entry<String, String> entry : replacements.entrySet()) {
             Object value = queryParams.get(entry.getKey());
             query = query.replace(entry.getValue(), String.valueOf(value));
         }
         return query;
     }

     private Map<String, String> computeReplacements(Map<String, Object> params, String query) {
         Map<String, String> result = new HashMap<>();
         for (String name : params.keySet()) {
             String pattern = "#{" + name + "}";
             if (query.contains(pattern)) result.put(name,pattern);
         }
         return result;
     }

     private String toString(Map<String, Object> row, String col) {
         Object value = row.get(col);
         return value == null ? null : value.toString();

     }

     private Map<String, Object> createParams(CSVReader reader) throws IOException {
         String[] header = reader.readNext();
         Map<String,Object> params=new LinkedHashMap<>();
         for (String name : header) {
             params.put(name,null);
         }
         return params;
     }

     private Map<String, Object> update(Map<String, Object> params, Map<String, Type> types, String[] input) {
         int col=0;
         for (Map.Entry<String, Object> param : params.entrySet()) {
             Type type = types.get(param.getKey());
             Object value = type.convert(input[col++]);
             param.setValue(value);
         }
         return params;
     }

    
}
