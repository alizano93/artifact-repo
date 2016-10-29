package com.digitalglobe.hydrate.security.module.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import com.digitalglobe.hydrate.security.module.configuration.ApplicationConfiguration;
import org.apache.commons.cli.CommandLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Service;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.digitalglobe.hydrate.logging.factory.HydrateLoggerFactory;
import com.digitalglobe.hydrate.logging.logger.HydrateLogger;
import com.digitalglobe.hydrate.security.module.model.SecureData;
import com.digitalglobe.hydrate.security.module.store.SecureStore;

@Service
public class ApplicationCLI {

    private static final String ERROR_MESSAGE_TWO_MODE_OPTIONS = "\"No one can serve two masters. "
	    + "Either you will hate the one and love the other,"
	    + " or you will be devoted to the one and despise the other.\"" + System.lineSeparator()
	    + "You cannot store and get secure data at the same time!.";
    private static final String ERROR_MESSAGE_NO_MODE_OPTION = "\"If you don’t stand for something, you’ll fall for anything.\" "
	    + "Please select one option either store or get data!";
    private static final HydrateLogger LOGGER = HydrateLoggerFactory.getDefaultHydrateLogger(ApplicationCLI.class);
    private static final String OPT_D = "d";
    private static final String OPT_DEBUG = "debug";
    private static final String OPT_MKN = "mkn";
    private static final String OPT_MASTER_KEY_NAME = "master-key-name";
    private static final String OPT_N = "n";
    private static final String OPT_SECURE_DATA_NAME = "secure-data-name";
    private static final String OPT_H = "h";
    private static final String OPT_HELP = "help";
    private static final String OPT_V = "v";
    private static final String OPT_VERSION = "version";
    private static final String OPT_F = "f";
    private static final String OPT_FILE = "file";
    private static final String OPT_S = "s";
    private static final String OPT_STORE = "store";
    private static final String OPT_G = "g";
    private static final String OPT_GET = "get";
    private static final String OPT_METADATA = "metadata";
    private static final String OPT_M = "m";

    @Value("${application.version:0.0.1}")
    private String version;

    @Autowired(required=true)
   private SecureStore secureStore;     

    public static void main(String[] args) {
	
	AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
	ctx.register(ApplicationConfiguration.class);
	ctx.refresh();
        ApplicationCLI appCLI = ctx.getBean( ApplicationCLI.class);
	appCLI.run(args);

	ctx.close();
    }

    public void run(String[] args) {
	

	// create the parser
	CommandLineParser parser = new DefaultParser();
	try {
	    Options mainOptions = createMainOptions();
	    Options helpOptions = createInformationOptions();

	    // OptionGroup optionGroup = new OptionGroup();
	    // optionGroup.
	    // parse the command line arguments
	    CommandLine helpLine = parser.parse(helpOptions, args, true);
	    if (helpLine.hasOption(OPT_H)) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("hydrate-security-module", mainOptions);
	    } else if (helpLine.hasOption(OPT_V)) {
		System.out.println("Version:" + version);
	    } else {

		CommandLine line = parser.parse(mainOptions, args);

		if (line.hasOption(OPT_D) && Boolean.valueOf(line.getOptionValue(OPT_D, "true"))) {
		    LOGGER.debug("Turning on DEBUG logging");
		}

		String masterKeyName = "";
		if (line.hasOption(OPT_MKN)) {
		    LOGGER.info(String.format("Using master key [%s]", line.getOptionValue(OPT_MKN)));
		    masterKeyName = line.getOptionValue(OPT_MKN);
		} else {
		    LOGGER.warn("Master key name not provided. Using default one");
		}

		String secureDataName = "";
		if (line.hasOption(OPT_N)) {
		    secureDataName = line.getOptionValue(OPT_N);
		    LOGGER.info(String.format("Name of the secure data store [%s]", line.getOptionValue(OPT_N)));
		} else {
		    LOGGER.error(String.format("Missing required arg [-%s] or [--%s], use --help for more info.", OPT_N,
			    OPT_SECURE_DATA_NAME));
		    System.exit(100);
		}

		if (line.hasOption(OPT_S) && line.hasOption(OPT_G)) {
		    LOGGER.error(ERROR_MESSAGE_TWO_MODE_OPTIONS);
		    System.exit(666);
		} else if (!line.hasOption(OPT_S) && !line.hasOption(OPT_G)) {
		    LOGGER.error(ERROR_MESSAGE_NO_MODE_OPTION);
		    System.exit(2);
		} else if (line.hasOption(OPT_S)) {

		    byte[] rawData = null;
		    if (line.hasOption(OPT_F)) {
			LOGGER.debug(String.format("Read data content off file [%s]", line.getOptionValue(OPT_F)));
			rawData = consumeInputStream(new FileInputStream(new File(line.getOptionValue(OPT_F))));
		    } else {
			LOGGER.debug(String.format("Read data content off System.in"));
			rawData = consumeInputStream(System.in);
		    }
		    
		    Map<String, String> metadata = parseMetadata(line.getOptionValues(OPT_M));
		
		    LOGGER.debug(String.format("Data read: %s%s", System.lineSeparator(), new String(rawData)));
		    
		    boolean result = storeData(masterKeyName, secureDataName, rawData, metadata);
		    LOGGER.debug(String.format("Result [%s]", ""+result));
		} else if (line.hasOption(OPT_G)) {
		    SecureData secureData = getSecureData(secureDataName);
		    
		    byte[] rawData = secureData.getRawData();
		    printMetadata(secureData.getMetadata());
		    
		    if(line.hasOption(OPT_F) ) {
			writeToFile(new File(line.getOptionValue(OPT_F)), rawData);
		    } else {
			writeToConsole(rawData);
		    }
		}
	    }
	    
	} catch (Exception exp) {
	    // oops, something went wrong
	    LOGGER.error(exp, "Parsing failed.");
	} 

    }

    private void printMetadata(Map<String, String> metadata) {
	if(metadata != null && !metadata.isEmpty() ) {
	    for(Entry<String, String> entry : metadata.entrySet()) {
		 LOGGER.info(String.format("Metadata key[%s] ==> value[%s]   ", entry.getKey(),
			   entry.getValue()));
	    }
	}
    }

    private SecureData getSecureData(String secureDataName) throws IOException {
	SecureData secureData = secureStore.getSecureData(secureDataName); 
	return secureData;
    }

    private Map<String, String> parseMetadata(String[] optionValues) {

	Map<String, String> metadata = new HashMap();
	
	if (optionValues != null ) {
		metadata = new HashMap(optionValues.length);

	for(String keyPair : optionValues) {
		int indexOfSeparatorChar = keyPair.indexOf("=");
			if (indexOfSeparatorChar != -1) {
				metadata.put(keyPair.substring(0, indexOfSeparatorChar), keyPair.substring(indexOfSeparatorChar + 1));
			} else {
				LOGGER.error(String.format("Keypair [%s] does not have a '=' separator char.", keyPair));
			}
	 }
}
	return metadata;
    }

    private boolean storeData(String masterKeyName, String secureDataName, byte[] rawData, Map<String, String> metadata) {
	SecureData secureData = new SecureData(secureDataName,  metadata, rawData); 

	
	return secureStore.storeData(secureData);
    }

    private static void writeToConsole(byte[] rawData) {
	 try {
	    writeToOutputStream(System.out, rawData);
	} catch (IOException e) {
	    LOGGER.error(e, "Error while trying to write to console...");
	}
	
    }

    private static void writeToFile(File file, byte[] rawData) {
	FileOutputStream outputStream = null;
	try {
	    outputStream = new FileOutputStream(file);
	 
	    writeToOutputStream(outputStream, rawData);
	} catch (IOException e) {
	    LOGGER.error(e, String.format("Error while trying to write to file [%s]...", file));
	}
	
	try {
	    outputStream.close();
	} catch (Exception e) {
	   LOGGER.error(e, String.format("Error while trying to close file [%s] handle", file));
	}
	
    }

    private static void writeToOutputStream(OutputStream out, byte[] rawData) throws IOException {
	out.write(rawData);
    }
    private Options createMainOptions() {
	Options options = new Options();

	Option debug = new Option(OPT_D, OPT_DEBUG, true, "Set whether to show debugging messages.");
	debug.setRequired(false);
	debug.setOptionalArg(true);
	debug.setArgName("true|false");
	debug.setType(Boolean.class);

	Option masterKeyName = new Option(OPT_MKN, OPT_MASTER_KEY_NAME, true, "Name of the master key to use.");
	masterKeyName.setRequired(false);
	debug.setOptionalArg(true);
	masterKeyName.setArgName("name");

	Option secureDataName = new Option(OPT_N, OPT_SECURE_DATA_NAME, true, "Name of the secure data store.");
	secureDataName.setRequired(true);
	secureDataName.setArgName("name");

	Option file = new Option(OPT_F, OPT_FILE, true,
		"Path containing the bytes to be (securely) stored. **If missing, then will read from System.in or print to System.out**");
	file.setRequired(false);
	file.setArgName("file-path");

	Option store = new Option(OPT_S, OPT_STORE, false, "Run on store-data mode");
	store.setRequired(false);

	Option get = new Option(OPT_G, OPT_GET, false, "Run on get-data mode");
	get.setRequired(false);

	Option metadata = new Option(OPT_M, OPT_METADATA, true, "Metadata key-value pairs to be attached to the raw data content");
	metadata.setRequired(false);
	metadata.setArgs(Option.UNLIMITED_VALUES);
	metadata.setArgName("key1=value1  [ ... keyN=valueN]");
	
	options.addOption(debug);
	options.addOption(masterKeyName);
	options.addOption(secureDataName);
	options.addOption(file);
	options.addOption(get);
	options.addOption(store);
	options.addOption(metadata);
	
	return options;
    }

    private static byte[] consumeInputStream(InputStream inputStream) {
	byte[] result = null;
	try {
	    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

	    int dataByte;
	    while ((dataByte = inputStream.read()) != -1) {
		outputStream.write(dataByte);
	    }

	    // Null was received, so loop was aborted.
	    result = outputStream.toByteArray();
	    outputStream.close();
	} catch (IOException e) {
	    LOGGER.error(e, "Error while trying to consume input stream.");
	}
	return result;
    }

    private Options createInformationOptions() {
	Options options = new Options();

	Option version = new Option(OPT_V, OPT_VERSION, false, "print version");
	version.setRequired(false);

	Option help = new Option(OPT_H, OPT_HELP, false, "print help");
	help.setRequired(false);

	options.addOption(help);
	options.addOption(version);

	return options;
    }


}
