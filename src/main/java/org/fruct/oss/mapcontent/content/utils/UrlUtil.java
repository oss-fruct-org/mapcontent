package org.fruct.oss.mapcontent.content.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class UrlUtil {
	private static final Logger log = LoggerFactory.getLogger(UrlUtil.class);

	private static final int MAX_RECURSION = 5;

	public static HttpURLConnection getConnection(String urlStr) throws IOException {
		return getConnection(urlStr, MAX_RECURSION);
	}

	private static HttpURLConnection getConnection(String urlStr, final int recursionDepth) throws IOException {
		log.info("Downloading {}", urlStr);
		URL url = new URL(urlStr);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setReadTimeout(10000);
		conn.setConnectTimeout(10000);

		conn.setRequestMethod("GET");
		conn.setDoInput(true);

		conn.connect();
		int code = conn.getResponseCode();
		log.info("Code {}", code);

		// TODO: not tested
		if (code != HttpURLConnection.HTTP_ACCEPTED) {
			if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP) {
				if (recursionDepth == 0)
					throw new IOException("Too many redirects");

				String newLocation = conn.getHeaderField("Location");
				log.info("Redirecting to {}", newLocation);

				conn.disconnect();
				return getConnection(newLocation, recursionDepth - 1);
			}
		}

		return conn;
	}
}
