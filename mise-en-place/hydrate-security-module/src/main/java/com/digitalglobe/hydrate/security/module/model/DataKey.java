package com.digitalglobe.hydrate.security.module.model;

import java.security.Key;

public class DataKey {

    private byte[] encryptedBytes;
    private Key symmetricKey;

    public byte[] getEncryptedBytes() {
	return encryptedBytes;
    }

    public void setEncryptedBytes(byte[] encryptedBytes) {
	this.encryptedBytes = encryptedBytes;
    }

    public Key getSymmetricKey() {
	return symmetricKey;
    }

    public void setSymmetricKey(Key symmetricKey) {
	this.symmetricKey = symmetricKey;
    }
}
