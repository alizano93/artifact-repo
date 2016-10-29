package com.digitalglobe.hydrate.security.module.model;

import java.util.Map;

/**
 * POJO used as a request/response model object.
 *
 */
public class SecureData {

    private Map<String, String> metadata;
    private String secureDataName;
    private byte[] rawData;

    public SecureData(String pSecureDataName, Map<String, String> pMetadata, byte[] pRawData) {
	this.metadata = pMetadata;
	this.secureDataName = pSecureDataName;
	this.rawData = pRawData;
    }

    public Map<String, String> getMetadata() {
	return metadata;
    }

    public String getSecureDataName() {
	return secureDataName;
    }

    public byte[] getRawData() {
	return rawData;
    }

    /**
     * <font color=red >DO NOT assume {@link SecureData#setMetadata(Map)} will
     * handle the <code>metadata</code> with the same scrutiny as
     * {@link SecureData#setRawData(byte[])} will with that of the
     * <code>rawData</code> itself.</font>
     * 
     * NOTE: Keys must be all lowercase!
     * 
     * @param metadata
     *            A user metadata map that will be attached to the rawData all
     *            along.
     */
    public void setMetadata(Map<String, String> metadata) {
	this.metadata = metadata;
    }

    public void setSecureDataName(String name) {
	this.secureDataName = name;
    }

    /**
     * Set the content of the data in raw format (<code>byte[]</code>) which
     * will be stored in a secure manner.
     * 
     * @param rawData
     */
    public void setRawData(byte[] rawData) {
	this.rawData = rawData;
    }

}
