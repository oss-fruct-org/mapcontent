package org.fruct.oss.mapcontent.content.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

public class Utils {
	@NonNull
	public static String serializeStringList(@NonNull List<String> list) {
		if (list.isEmpty()) {
			return "[]";
		}

		JSONArray json = new JSONArray();
		for (String string : list) {
			json.put(string);
		}

		return json.toString();
	}

	@NonNull
	public static List<String> deserializeStringList(@Nullable String jsonEncoded) {
		ArrayList<String> ret = new ArrayList<String>();

		if (jsonEncoded == null) {
			return ret;
		}

		try {
			JSONArray json = new JSONArray(jsonEncoded);

			for (int i = 0; i < json.length(); i++) {
				ret.add(json.getString(i));
			}

		} catch (JSONException e) {
			throw new IllegalArgumentException("Argument must be JSON array");
		}

		return ret;
	}

	public static void silentClose(@Nullable Closeable closeable) {
		try {
			if (closeable != null) {
				closeable.close();
			}
		} catch (IOException ignored) {
		}
	}

	/**
	 * Workaround for "java.lang.IncompatibleClassChangeError: interface not implemented" in Samsung Galaxy S4
	 */
	public static void silentClose(@Nullable ZipFile zipFile) {
		try {
			if (zipFile != null) {
				zipFile.close();
			}
		} catch (IOException ignored) {
		}
	}

	public static boolean checkNetworkAvailability(Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo info = connManager.getActiveNetworkInfo();
		return info != null && info.isConnected();
	}

}
