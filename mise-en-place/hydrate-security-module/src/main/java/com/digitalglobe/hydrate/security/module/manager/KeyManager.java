package com.digitalglobe.hydrate.security.module.manager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.security.cert.Certificate;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.time.Period;

import com.digitalglobe.hydrate.logging.factory.HydrateLoggerFactory;
import com.digitalglobe.hydrate.logging.logger.HydrateLogger;
import com.digitalglobe.hydrate.security.module.cert.CertificateGenerator;
import com.digitalglobe.hydrate.security.module.model.DataKey;
import com.digitalglobe.hydrate.security.module.model.EncryptedData;
import com.safenetinc.luna.provider.LunaProvider;

/**
 * This example illustrates how to use the Luna KeyStore. For information on the
 * LunaMP keystore see KeyStoreLunaMPDemo.java
 */
@Service
public class KeyManager {

    private final static HydrateLogger LOGGER = HydrateLoggerFactory.getDefaultHydrateLogger(KeyManager.class);

    private static final int RSA_KEYSIZE = 2048; // Master key (asymmetric) key
						 // size
    private static final int AES_KEYSIZE = 256; // Data key (symmetric) key size
    // Note that this requires installation of jce_policy-8.zip
    // by placing the unzipped files into jre\lib\security

    /*
     * There are other RSA Ciphers available for use: RSA/NONE/PKCS1v1_5
     * RSA/NONE/OAEPWithSHA1andMGF1Padding
     *
     * For a full list of supported Ciphers in the Luna provider please see the
     * Luna Development Guide.
     *
     * For a list of supported Ciphers in alternate providers please see the
     * documentation of the provider in question.
     */
    private static final String RSA_CIPHER_TYPE = "RSA/NONE/OAEPWithSHA1andMGF1Padding";

    // // TODO: Move this outside code!!!
    // private static final String PARTITION_PASSWORD = "password";

    @Autowired(required = true)
    private LunaManager lunaManager;

    @Value("${key.store.password}")
    private String keyStorePassword;

    private KeyStore loginToKeyStore() {
	KeyStore myStore = null;

	if (Security.getProvider("LunaProvider") == null) {
	    LOGGER.info("adding LunaProvider");
	    Security.addProvider(new LunaProvider());
	}
	try {
	    ByteArrayInputStream is1 = new ByteArrayInputStream(("slot:1").getBytes());
	    // The Luna keystore is a Java Cryptography view of the contents
	    // of the HSM.
	    myStore = KeyStore.getInstance("Luna");

	    /*
	     * Loading a Luna keystore can be done without specifying an input
	     * stream or password if a login was previously done to the first
	     * slot.
	     *
	     * In this case we have not logged in to the slot and shall do so
	     * here.
	     *
	     * The byte array input stream contains "slot:1" specifying that we
	     * wish to open a keystore corresponding to the slot with ID 1. You
	     * can also open keystores by name. using the syntax
	     * "tokenlabel:PartitionName"
	     */
	    myStore.load(is1, keyStorePassword.toCharArray());

	} catch (KeyStoreException kse) {
	    LOGGER.error("Unable to create keystore object: " + kse.getMessage());
	    System.exit(-1);
	} catch (NoSuchAlgorithmException nsae) {
	    LOGGER.error("Unexpected NoSuchAlgorithmException while loading keystore");
	    System.exit(-1);
	} catch (CertificateException e) {
	    LOGGER.error("Unexpected CertificateException while loading keystore");
	    System.exit(-1);
	} catch (IOException e) {
	    // this should never happen
	    LOGGER.error("Unexpected IOException while loading keystore.");
	    System.exit(-1);
	}
	return myStore;
    }

    private static Certificate[] generateCertificateChain(KeyPair keyPair) throws Exception {
	Certificate[] certChain = new Certificate[1];
	certChain[0] = CertificateGenerator.generateLunaSelfSignedCertificate(keyPair, Period.ofYears(1));
	return certChain;
    }

    /*
     * We're modeling off of KMS operations, so the idea is to have a master key
     * on the HSM that will be used to encrypt data keys. The data keys will be
     * used from the HSM client (EC2 instance) to encrypt sensitive data.
     * Overall flow from a cryptographic perspective then is:
     *
     * generateMasterKey(keyname) - Create a master key. This will happen at
     * initialization and on key rotation
     *
     * generateAesKey() - Create a data key. This will generate a data key
     * locally then encrypt it with the HSM. The PlainText key can then be used
     * to encrypt sensitive data
     *
     * It's worth noting that per-object keys (data keys) cannot be stored on
     * the HSM. Appliances have 2MB of key and object storage
     * (https://aws.amazon.com/cloudhsm/faqs/). This includes enough storage for
     * 14,000 symmetric keys or 1,200 RSA 2048 key pairs
     */
    public void generateMasterKey(String keyName) {
	// Login to the HSM
	lunaManager.login();

	KeyPairGenerator keyGen = null;
	KeyPair keypair = null;
	try {
	    // Generate an RSA KeyPair
	    /*
	     * The KeyPairGenerator class is used to determine the type of
	     * KeyPair being generated. The most common options for this are RSA
	     * or DSA.
	     *
	     * For more information concerning the algorithms available in the
	     * Luna provider please see the Luna Development Guide. For more
	     * information concerning other providers, please read the
	     * documentation available for the provider in question.
	     *
	     * The KeyPairGenerator.getInstance method also supports specifying
	     * providers as a parameter to the method.
	     *
	     * keyGen = KeyPairGenerator.getInstance("RSA", "Luna"); - which
	     * specifies the Luna provider for the RSA KeyPair generation or
	     * keyGen = KeyPairGenerator.getInstance("RSA", "SUN"); - which uses
	     * the Sun provider for the RSA KeyPair generation
	     *
	     * Many other methods will allow you to specify the provider as a
	     * parameter. Please see the Sun JDK class reference at
	     * http://java.sun.org for more information.
	     */
	    if (Security.getProvider("LunaProvider") == null) {
		LOGGER.info("adding LunaProvider");
		Security.addProvider(new LunaProvider());
	    }
	    keyGen = KeyPairGenerator.getInstance("RSA", "LunaProvider");
	    keyGen.initialize(RSA_KEYSIZE);
	    keypair = keyGen.generateKeyPair();
	} catch (Exception e) {
	    LOGGER.error("Exception during Key Generation - " + e.getMessage());
	    System.exit(1);
	}

	KeyStore myStore = loginToKeyStore();
	// Here we simply need to store the key on the HSM for later usage
	try {
	    // Saving key to keystore
	    /*
	     * Even though the key created above was created with the Luna
	     * Provider (assuming it was the first JCE found), the key is not
	     * permanently stored on the Luna HSM automatically. Keys and
	     * Certificates are made persistent (or in PKCS#11 terms, become
	     * token objects instead of session objects) only when the
	     * setKeyEntry() or setCertificateEntry() methods are invoked.
	     */
	    myStore.setKeyEntry(keyName, keypair.getPrivate(), null, generateCertificateChain(keypair));
	} catch (Exception e) {
	    LOGGER.error("Exception saving Key to Keystore - " + e.getMessage());
	    System.exit(1);
	}

	// Logout of the token
	lunaManager.logout();
    }

    private static KeyPair getKeyPair(KeyStore keystore, String masterKeyName) throws Exception {
	Key masterPrivateKey = keystore.getKey(masterKeyName, null);
	Certificate cert = keystore.getCertificate(masterKeyName);
	PublicKey publicKey = cert.getPublicKey();
	return new KeyPair(publicKey, (PrivateKey) masterPrivateKey);
    }

    public boolean hasMasterKey(String masterKeyName) {
	lunaManager.login();
	KeyStore keystore = loginToKeyStore();
	boolean hasKey = false;
	try {
	    hasKey = keystore.containsAlias(masterKeyName);
	} catch (Exception e) {
	    LOGGER.error("Exception checking store for key name - " + e.getMessage());
	    System.exit(1);
	}
	lunaManager.logout();
	return hasKey;
    }

    public DataKey generateDataKey(String masterKeyName) {
	// Generate our "plain text" key that will be used for operations
	KeyGenerator keyGen = null;
	try {
	    keyGen = KeyGenerator.getInstance("AES");
	    keyGen.init(AES_KEYSIZE);
	} catch (Exception e) {
	    LOGGER.error("Exception initializing AES algorithm - " + e.getMessage());
	    System.exit(1);
	}
	SecretKey dataKey = keyGen.generateKey();

	/*
	 * The preferred method of logging in to the HSM is through the keystore
	 * interface.
	 *
	 * We will log in to the first slot
	 */
	lunaManager.login();
	KeyStore keystore = loginToKeyStore();
	boolean hasKey = false;
	try {
	    hasKey = keystore.containsAlias(masterKeyName);
	} catch (Exception e) {
	    LOGGER.error("Exception checking store for key name - " + e.getMessage());
	    System.exit(1);
	}
	if (!hasKey) {
	    // TODO: Throw exception
	    LOGGER.error("Key is not present in Keystore\n");
	}

	Cipher rsaCipher = null;
	KeyPair masterKey = null;
	try {
	    masterKey = getKeyPair(keystore, masterKeyName);
	} catch (Exception e) {
	    LOGGER.error("Exception getting keypair from key store - " + e.getMessage());
	    System.exit(1);
	}
	try {
	    rsaCipher = Cipher.getInstance(RSA_CIPHER_TYPE, "LunaProvider");
	    rsaCipher.init(Cipher.ENCRYPT_MODE, masterKey.getPublic());
	} catch (Exception e) {
	    LOGGER.error("Exception in Cipher Initialization - " + e.getMessage());
	    System.exit(1);
	}
	byte[] encryptedbytes = null;
	try {
	    // Encrypt the message
	    /*
	     * Encrypt/Decrypt operations can be performed in one of two ways 1.
	     * Singlepart 2. Multipart
	     *
	     * To perform a singlepart encrypt/decrypt operation use the
	     * following example. Multipart encrypt/decrypt operations require
	     * use of the Cipher.update() and Cipher.doFinal() methods.
	     *
	     * For more information please see the class documentation for the
	     * java.cryptox.Cipher class with respect to the version of the JDK
	     * you are using.
	     */
	    encryptedbytes = rsaCipher.doFinal(dataKey.getEncoded());
	} catch (Exception e) {
	    LOGGER.error("Exception during Encryption - " + e.getMessage());
	    System.exit(1);
	}
	lunaManager.logout();

	// The caller will need both the data key in raw form (generatedKey)
	// to perform the encryption and the encrypted bytes of the data key
	// as metadata. This is a similar model to KMS
	DataKey generatedKey = new DataKey();
	generatedKey.setEncryptedBytes(encryptedbytes);
	generatedKey.setSymmetricKey(dataKey);
	return generatedKey;
    }

    public static String bytesToHex(byte[] in) {
	final StringBuilder builder = new StringBuilder();
	for (byte b : in) {
	    builder.append(String.format("%02x", b));
	}
	return builder.toString();
    }

    public static byte[] hexToBytes(String s) {
	int len = s.length();
	byte[] data = new byte[len / 2];
	for (int i = 0; i < len; i += 2) {
	    data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
	}
	return data;
    }

    /*
     * Often we don't want to deal with the raw encryption, so this function
     * will manage the crypto details on behalf of the caller. It's possible
     * that generateDataKey may want to be disallowed by business process (or
     * simply made private) to prohibit unencrypted data keys from leaking
     * outside the boundaries of this class.
     *
     */
    public EncryptedData encryptData(byte[] bytesToEncrypt, String masterKeyName) {
	DataKey dataKey = generateDataKey(masterKeyName);
	byte[] iv = null;
	Cipher cipher = null;

	try {
	    cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
	    cipher.init(Cipher.ENCRYPT_MODE, dataKey.getSymmetricKey());
	    iv = cipher.getIV();
	} catch (Exception e) {
	    LOGGER.error("Exception initializing AES cipher - " + e.getMessage());
	    System.exit(1);
	}
	byte[] encryptedBytes = null;
	try {
	    encryptedBytes = cipher.doFinal(bytesToEncrypt);
	} catch (Exception e) {
	    LOGGER.error("Exception during Encryption - " + e.getMessage());
	    System.exit(1);
	}
	EncryptedData rc = new EncryptedData();
	rc.setEncryptedBytes(encryptedBytes);
	rc.setIv(iv);
	rc.setEncryptedKey(dataKey.getEncryptedBytes());
	rc.setMasterKeyName(masterKeyName);
	return rc;
    }

    public byte[] decryptData(EncryptedData encryptedData) {
	/*
	 * The preferred method of logging in to the HSM is through the keystore
	 * interface.
	 *
	 * We will log in to the first slot
	 */
	lunaManager.login();
	KeyStore keystore = loginToKeyStore();
	boolean hasKey = false;
	try {
	    hasKey = keystore.containsAlias(encryptedData.getMasterKeyName());
	} catch (Exception e) {
	    LOGGER.error("Exception checking store for key name - " + e.getMessage());
	    System.exit(1);
	}
	if (!hasKey) {
	    // TODO: Throw exception
	    LOGGER.error("Key is not present in Keystore\n");
	}
	KeyPair masterKey = null;
	try {
	    masterKey = getKeyPair(keystore, encryptedData.getMasterKeyName());
	} catch (Exception e) {
	    LOGGER.error("Exception getting keypair from key store - " + e.getMessage());
	    System.exit(1);
	}

	Cipher asymmetricCipher = null;

	try {
	    asymmetricCipher = Cipher.getInstance(RSA_CIPHER_TYPE, "LunaProvider");
	    asymmetricCipher.init(Cipher.DECRYPT_MODE, masterKey.getPrivate());

	} catch (Exception e) {
	    LOGGER.error("Exception in Cipher Initialization - " + e.getMessage());
	    System.exit(1);
	}
	byte[] decryptedKeyBytes = null;
	try {
	    // Decrypt our encrypted symmetric key
	    decryptedKeyBytes = asymmetricCipher.doFinal(encryptedData.getEncryptedKey());
	} catch (Exception e) {
	    LOGGER.error("Exception in decryption of the data key - " + e.getMessage());
	    System.exit(1);
	}

	lunaManager.logout();
	Key decryptedKey = null;
	try {
	    decryptedKey = new SecretKeySpec(decryptedKeyBytes, "AES");
	} catch (Exception e) {
	    LOGGER.error("Exception converting bytes to key - " + e.getMessage());
	    System.exit(1);
	}
	LOGGER.debug("length of decryptedKeyBytes: " + decryptedKeyBytes.length);
	// Now we have our decrypted key, we can now decrypt the data
	Cipher cipher = null;
	try {
	    cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
	    cipher.init(Cipher.DECRYPT_MODE, decryptedKey, new IvParameterSpec(encryptedData.getIv()));
	} catch (Exception e) {
	    LOGGER.error("Exception initializing AES cipher - " + e.getMessage());
	    System.exit(1);
	}
	byte[] decryptedBytes = null;
	try {
	    decryptedBytes = cipher.doFinal(encryptedData.getEncryptedBytes());
	} catch (Exception e) {
	    LOGGER.error("Exception during Encryption - " + e.getMessage());
	    System.exit(1);
	}
	return decryptedBytes;
    }
}
