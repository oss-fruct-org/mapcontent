package org.fruct.oss.mapcontent.content.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class StrUtil {
	private static final char[] hexDigits = "0123456789abcdef".toCharArray();

	public static void copyStream(InputStream input, OutputStream output) throws IOException {
		int bufferSize = 10000;

		byte[] buf = new byte[bufferSize];
		int read;
		while ((read = input.read(buf)) > 0) {
			output.write(buf, 0, read);
			if (Thread.currentThread().isInterrupted()) {
				throw new InterruptedIOException("copyStream thread interrupted");
			}
		}
	}

	public static String toHex(byte[] arr) {
		final char[] str = new char[arr.length * 2];

		for (int i = 0; i < arr.length; i++) {
			final int v = arr[i] & 0xff;
			str[2 * i] = hexDigits[v >>> 4];
			str[2 * i + 1] = hexDigits[v & 0x0f];
		}

		return new String(str);
	}

	public static String hashStream(InputStream in, String hash) throws IOException {
		MessageDigest md5 = null;
		try {
			md5 = MessageDigest.getInstance(hash);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

		int bsize = 4096;
		byte[] buffer = new byte[bsize];
		int length;

		while ((length = in.read(buffer, 0, bsize)) > 0) {
			md5.update(buffer, 0, length);
		}

		return toHex(md5.digest());
	}
}
