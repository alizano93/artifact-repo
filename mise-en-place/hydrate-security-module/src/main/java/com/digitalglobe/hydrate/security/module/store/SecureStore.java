package com.digitalglobe.hydrate.security.module.store;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.http.util.Asserts;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.digitalglobe.hydrate.logging.factory.HydrateLoggerFactory;
import com.digitalglobe.hydrate.logging.logger.HydrateLogger;
import com.digitalglobe.hydrate.security.module.configuration.ApplicationConfiguration;
import com.digitalglobe.hydrate.security.module.manager.KeyManager;
import com.digitalglobe.hydrate.security.module.model.EncryptedData;
import com.digitalglobe.hydrate.security.module.model.SecureData;

@Service
public class SecureStore {

    @Autowired(required = true)
    private KeyManager keyManager;

    private final static HydrateLogger LOGGER = HydrateLoggerFactory.getDefaultHydrateLogger(SecureStore.class);



    private String bucketName;
    private String masterKeyName;

    public static final String CLIENT_METADATA_NAME = "cmn";

    public static final String CLIENT_METADATA_PREFIX = CLIENT_METADATA_NAME + "-";

    public SecureStore(String pMasterKeyName, String pBucketName) {
	LOGGER.info(String.format("Setting master key [%s] and bucket name [%s]", pMasterKeyName, pBucketName));
	masterKeyName = pMasterKeyName;
	bucketName = pBucketName;
    }

    public boolean storeData(SecureData dataToBeStored) {
	boolean result = false;

	AmazonS3 s3client = new AmazonS3Client(new InstanceProfileCredentialsProvider());
	try {

	    if (!keyManager.hasMasterKey(masterKeyName))
		keyManager.generateMasterKey(masterKeyName);
	    EncryptedData encrypted = keyManager.encryptData(dataToBeStored.getRawData(), masterKeyName);
	    ByteArrayInputStream inputStream = new ByteArrayInputStream(encrypted.getEncryptedBytes());
	    ObjectMetadata meta = new ObjectMetadata();
	    meta.setContentLength(encrypted.getEncryptedBytes().length);
	    meta.addUserMetadata("key", KeyManager.bytesToHex(encrypted.getEncryptedKey()));
	    meta.addUserMetadata("iv", KeyManager.bytesToHex(encrypted.getIv()));
	    meta.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION); 	
	    if (dataToBeStored.getMetadata() != null) {
		for (Entry<String, String> keyPair : dataToBeStored.getMetadata().entrySet()) {
		    meta.addUserMetadata(CLIENT_METADATA_PREFIX + keyPair.getKey(), keyPair.getValue());
		}
	    }
	    
	    LOGGER.debug(String.format("Uploading to bucket [%s]", bucketName));
	    s3client.putObject(new PutObjectRequest(bucketName, dataToBeStored.getSecureDataName(), inputStream, meta));
	    result = true;
	} catch (AmazonServiceException ase) {
	    LOGGER.error("Caught an AmazonServiceException, which " + "means your request made it "
		    + "to Amazon S3, but was rejected with an error response" + " for some reason.");
	    LOGGER.error("Error Message:    " + ase.getMessage());
	    LOGGER.error("HTTP Status Code: " + ase.getStatusCode());
	    LOGGER.error("AWS Error Code:   " + ase.getErrorCode());
	    LOGGER.error("Error Type:       " + ase.getErrorType());
	    LOGGER.error("Request ID:       " + ase.getRequestId());

	} catch (AmazonClientException ace) {
	    LOGGER.error("Caught an AmazonClientException, which " + "means the client encountered "
		    + "an internal error while trying to " + "communicate with S3, "
		    + "such as not being able to access the network.");
	    LOGGER.error("Error Message: " + ace.getMessage());
	}
	return result;
    }

    public SecureData getSecureData(String secureDataName) throws IOException {
	SecureData response = null;
	AmazonS3 s3client = new AmazonS3Client(new InstanceProfileCredentialsProvider());
	byte[] decrypted = null;
	try {
	    S3Object object = s3client.getObject(new GetObjectRequest(bucketName, secureDataName));
	    ObjectMetadata metadata = object.getObjectMetadata();
	    EncryptedData encrypted = new EncryptedData();
	    encrypted.setIv(KeyManager.hexToBytes(metadata.getUserMetaDataOf("iv")));
	    encrypted.setEncryptedKey(KeyManager.hexToBytes(metadata.getUserMetaDataOf("key")));
	    encrypted.setEncryptedBytes(toByteArray(object.getObjectContent()));
	    encrypted.setMasterKeyName(masterKeyName);
	    decrypted = keyManager.decryptData(encrypted);

	    response = new SecureData(secureDataName, getClientMetadata(metadata.getUserMetadata()), decrypted);
	} catch (AmazonServiceException ase) {
	    LOGGER.error("Caught an AmazonServiceException, which " + "means your request made it "
		    + "to Amazon S3, but was rejected with an error response" + " for some reason.");
	    LOGGER.error("Error Message:    " + ase.getMessage());
	    LOGGER.error("HTTP Status Code: " + ase.getStatusCode());
	    LOGGER.error("AWS Error Code:   " + ase.getErrorCode());
	    LOGGER.error("Error Type:       " + ase.getErrorType());
	    LOGGER.error("Request ID:       " + ase.getRequestId());
	} catch (AmazonClientException ace) {
	    LOGGER.error("Caught an AmazonClientException, which " + "means the client encountered "
		    + "an internal error while trying to " + "communicate with S3, "
		    + "such as not being able to access the network.");
	    LOGGER.error("Error Message: " + ace.getMessage());
	}

	return response;
    }

    private static Map<String, String> getClientMetadata(Map<String, String> map) {
	Map<String, String> userMetadata = new HashMap<String, String>();
	if (map != null) {

	    for (Entry<String, String> keyPair : map.entrySet()) {
		if (keyPair.getKey().startsWith(CLIENT_METADATA_PREFIX)) {
		    userMetadata.put(
			    keyPair.getKey().substring((CLIENT_METADATA_PREFIX).length(), keyPair.getKey().length()),
			    keyPair.getValue());
		}
	    }
	}
	return userMetadata;
    }

    private static byte[] toByteArray(InputStream is) throws IOException {
	ByteArrayOutputStream buffer = new ByteArrayOutputStream();

	int nRead;
	byte[] data = new byte[16384];

	while ((nRead = is.read(data, 0, data.length)) != -1) {
	    buffer.write(data, 0, nRead);
	}

	buffer.flush();

	return buffer.toByteArray();
    }
}
