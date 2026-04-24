package com.wagepayroll.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Sha256Hex {

	private Sha256Hex() {
	}

	public static String ofUtf8String(String value) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(64);
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
