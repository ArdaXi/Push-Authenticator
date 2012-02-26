// Copyright (C) 2009 Google Inc.

package com.ardaxi.authenticator;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;

/**
 * An implementation of the HOTP generator specified by RFC 4226. Generates
 * short passcodes that may be used in challenge-response protocols
 *
 * The default passcode is a 6-digit decimal code
 *
 * @author sweis@google.com (Steve Weis)
 *
 */
public class PasscodeGenerator {
	/** Default decimal passcode length */
	private static final int PASS_CODE_LENGTH = 6;

	private static final int PIN_MODULO =
			(int) Math.pow(10, PASS_CODE_LENGTH);

	private final Signer signer;
	private final int codeLength;

	/*
	 * Using an interface to allow us to inject different signature
	 * implementations.
	 */
	interface Signer {
		byte[] sign(byte[] data) throws GeneralSecurityException;
	}

	/**
	 * @param mac A {@link Mac} used to generate passcodes
	 */
	public PasscodeGenerator(Mac mac) {
		this(mac, PASS_CODE_LENGTH);
	}

	/**
	 * @param mac A {@link Mac} used to generate passcodes
	 * @param passCodeLength The length of the decimal passcode
	 */
	public PasscodeGenerator(final Mac mac, int passCodeLength) {
		this(new Signer() {
			public byte[] sign(byte[] data){
				return mac.doFinal(data);
			}
		}, passCodeLength);
	}

	public PasscodeGenerator(Signer signer, int passCodeLength) {
		this.signer = signer;
		this.codeLength = passCodeLength;
	}

	private String padOutput(int value) {
		String result = Integer.toString(value);
		for (int i = result.length(); i < codeLength; i++) {
			result = "0" + result;
		}
		return result;
	}

	/**
	 * @param challenge A long-valued challenge
	 * @return A decimal response code
	 * @throws GeneralSecurityException If a JCE exception occur
	 */
	public String generateResponseCode(long challenge)
			throws GeneralSecurityException {
		byte[] value = ByteBuffer.allocate(8).putLong(challenge).array();
		return generateResponseCode(value);
	}

	/**
	 * @param challenge An arbitrary byte array used as a challenge
	 * @return A decimal response code
	 * @throws GeneralSecurityException If a JCE exception occur
	 */
	public String generateResponseCode(byte[] challenge)
			throws GeneralSecurityException {
		byte[] hash = signer.sign(challenge);

		// Dynamically truncate the hash
		// OffsetBits are the low order bits of the last byte of the hash
		int offset = hash[hash.length - 1] & 0xF;
		// Grab a positive integer value starting at the given offset.
		int truncatedHash = hashToInt(hash, offset) & 0x7FFFFFFF;
		int pinValue = truncatedHash % PIN_MODULO;
		return padOutput(pinValue);
	}

	/**
	 * Grabs a positive integer value from the input array starting at
	 * the given offset.
	 * @param bytes the array of bytes
	 * @param start the index into the array to start grabbing bytes
	 * @return the integer constructed from the four bytes in the array
	 */
	private int hashToInt(byte[] bytes, int start) {
		DataInput input = new DataInputStream(
				new ByteArrayInputStream(bytes, start, bytes.length - start));
		int val;
		try {
			val = input.readInt();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return val;
	}

	/**
	 * @param challenge A challenge to check a response against
	 * @param response A response to verify
	 * @return True if the response is valid
	 */
	public boolean verifyResponseCode(long challenge, String response)
			throws GeneralSecurityException {
		String expectedResponse = generateResponseCode(challenge);
		return expectedResponse.equals(response);
	}
}
