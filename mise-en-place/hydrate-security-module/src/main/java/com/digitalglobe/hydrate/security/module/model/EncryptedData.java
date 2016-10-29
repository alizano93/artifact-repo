package com.digitalglobe.hydrate.security.module.model;

public class EncryptedData {
    private byte[] encryptedBytes;
    private byte[] iv;
    private byte[] encryptedKey;
    private String masterKeyName;

    public byte[] getEncryptedBytes() {
	return encryptedBytes;
    }

    public void setEncryptedBytes(byte[] encryptedBytes) {
	this.encryptedBytes = encryptedBytes;
    }

    public byte[] getIv() {
	return iv;
    }

    public void setIv(byte[] iv) {
	this.iv = iv;
    }

    public byte[] getEncryptedKey() {
	return encryptedKey;
    }

    public void setEncryptedKey(byte[] encryptedKey) {
	this.encryptedKey = encryptedKey;
    }

    public String getMasterKeyName() {
	return masterKeyName;
    }

    public void setMasterKeyName(String masterKeyName) {
	this.masterKeyName = masterKeyName;
    }
}
