package io.fixprotocol.orchestra2proto;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
//import javax.xml.bind.ValidationEvent;
import javax.xml.bind.util.ValidationEventCollector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import io.fixprotocol._2016.fixrepository.*;

public class ProtoGen {
	
	static Logger logger = Logger.getLogger(ProtoGen.class);
	
	public Map<String, StringBuilder> generateProtos(String repoFileName, CodegenSettings settings)
			throws FileNotFoundException, IOException, JAXBException
	{
		Repository rootElement = null;
		ValidationEventCollector evHandler = new JAXBValidator();
		JAXBContext jc = JAXBContext.newInstance("io.fixprotocol._2016.fixrepository");
		Unmarshaller unmarshaller = jc.createUnmarshaller();
		unmarshaller.setEventHandler(evHandler);
		FileInputStream fis = new FileInputStream(repoFileName);
		rootElement = (Repository) (unmarshaller.unmarshal(fis));
		fis.close();
		ModelFactory pf = null;
		switch(settings.getSchemaLanguage()) {
			case PROTO2: pf = new ProtobufModelFactory(rootElement, settings); break;
			case PROTO3: pf = new ProtobufModelFactory(rootElement, settings); break;
			case CAPNPROTO: pf = new CapnpModelFactory(rootElement, settings); break;
			case FLATBUFFERS: pf = new CapnpModelFactory(rootElement, settings); break;
		}
		IModel protoModel = pf.buildModel();
		Map<String, StringBuilder> fileSet = protoModel.toFileSet();
		return fileSet;
	}
	
	public static void main(String[] args) {
		
		BasicConfigurator.configure();
		
		CodegenSettings codegenSettings = new CodegenSettings();
		String outputPath = "./";
		String repoFileName = null;
		
		final String LANG_KEYSTR = "lang";
		final String OPATH_KEYSTR = "opath";
		final String NOSORT_KEYSTR = "nosort";
		final String ALTFILESET_KEYSTR = "altfs";
		
		Options cmdLineOptions = new Options();
		
		Option langOpt =  new Option(LANG_KEYSTR, null, true, "Schema language (valid args: proto2 proto3 capnp)");
		langOpt.setRequired(false);
		cmdLineOptions.addOption(langOpt);
		Option outputDirOpt = new Option(OPATH_KEYSTR, null, true, "Output directory");
		outputDirOpt.setRequired(false);
		cmdLineOptions.addOption(outputDirOpt);
		Option sortOpt = new Option(NOSORT_KEYSTR, null, false, "Maintain repo field ordering");
		sortOpt.setRequired(false);
		cmdLineOptions.addOption(sortOpt);
		Option altFilePackagingOpt = new Option(ALTFILESET_KEYSTR, null, false, "Use alternate output file packaging");
		altFilePackagingOpt.setRequired(false);
		cmdLineOptions.addOption(altFilePackagingOpt);
		
		Option help = new Option("help", "Print this message");
		cmdLineOptions.addOption(help);
		
		Option version = new Option("version", "Print version");
		cmdLineOptions.addOption(version);
		
		CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        try {
            CommandLine cmd = parser.parse(cmdLineOptions, args);
            if(cmd.hasOption("help")) {
            	formatter.printHelp("fixprotogen [options] <repo-file>\n Options:\n", cmdLineOptions);
                System.exit(1);
            }
            if(cmd.hasOption("version")) {
            	System.out.println("FIX Proto Schema Generator 0.0.1");
                System.exit(1);
            }
            if(cmd.hasOption(LANG_KEYSTR)) {
	            String langStr = cmd.getOptionValue(LANG_KEYSTR);
	            if(langStr.equalsIgnoreCase("proto2")) {
	            	codegenSettings.schemaLanguage = CodegenSettings.SchemaLanguage.PROTO2;
	            	outputPath = "generated_v2_protos";
	            }
	            else if(langStr.equalsIgnoreCase("proto3")) {
	            	codegenSettings.schemaLanguage = CodegenSettings.SchemaLanguage.PROTO3;
	            	outputPath = "generated_v3_protos";
	            }
	            else if(langStr.equalsIgnoreCase("capnp")) {
	            	codegenSettings.schemaLanguage = CodegenSettings.SchemaLanguage.CAPNPROTO;
	            	outputPath = "generated_capnps";
	            }
	            else if(langStr.equalsIgnoreCase("flatb")) {
	            	codegenSettings.schemaLanguage = CodegenSettings.SchemaLanguage.FLATBUFFERS;
	            	outputPath = "generated_flatbs";
	            }
	            else {
	            	System.err.println("Error: illegal lang option: " + langStr);
	            	System.exit(1);
	            }
            }
            else {
            	codegenSettings.schemaLanguage = CodegenSettings.SchemaLanguage.PROTO3;
            	outputPath = "generated_v3_protos";
            }
            if(cmd.hasOption(OPATH_KEYSTR)) {
            	outputPath = cmd.getOptionValue(OPATH_KEYSTR);
            }
            codegenSettings.useAltOutputPackaging = cmd.hasOption(ALTFILESET_KEYSTR) ? true : false;
            codegenSettings.maintainRepoFieldOrder = cmd.hasOption(NOSORT_KEYSTR) ? true : false;
            List<String> srcFiles = cmd.getArgList();
            if(srcFiles.isEmpty()) {
            	System.err.println("Error: missing repo file arg.");
            	formatter.printHelp("fixprotogen [options] <repo-file>\n Options:\n", cmdLineOptions);
                System.exit(1);
            }
            else {
            	repoFileName = srcFiles.get(0);
            }
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("fixprotogen [options] <repo-file>\n Options:\n", cmdLineOptions);
            System.exit(1);
        } catch (Exception e) {
        	System.out.println(e.getMessage());
        }
		
		ProtoGen protoGen = new ProtoGen();
		
		try {
			Map<String, StringBuilder> fileSet = protoGen.generateProtos(repoFileName, codegenSettings);
			File dir = new File(outputPath);
			if(!dir.exists()) {
				dir.mkdir();
			}
			for(Map.Entry<String, StringBuilder> entry : fileSet.entrySet()) {
				String targetFile = outputPath + "//" + entry.getKey();
				String protoText = entry.getValue().toString();
				FileWriter fstream = new FileWriter(targetFile);
				BufferedWriter out = new BufferedWriter(fstream);
				out.write(protoText);
				out.close();
			}
		}
		catch (FileNotFoundException e) {
			logger.error("FileNotFoundException: " + e.getMessage());
			e.printStackTrace();
		}
		catch (IOException e) {
			logger.error("IOException: " + e.getMessage());
			e.printStackTrace();
		}
		catch (JAXBException e) {
			logger.error("JAXBException: " + e.getMessage());
			e.printStackTrace();
		}
		/*
		catch (SecurityException e) {
			logger.error("Security Exception: " + e.getMessage());
			e.printStackTrace();
		}
		*/
	}
	
}