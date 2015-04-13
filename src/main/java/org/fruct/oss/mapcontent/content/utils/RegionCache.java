package org.fruct.oss.mapcontent.content.utils;

import android.util.JsonReader;

import org.apache.http.client.utils.URIUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class RegionCache {
	private static final Logger log = LoggerFactory.getLogger(RegionCache.class);

	private final File cacheDir;

	private Map<String, RegionDesc> cachedFiles = new HashMap<>();
	private Map<String, Region> regionsCache = new HashMap<>();

	public RegionCache(File cacheDir) {
		this.cacheDir = cacheDir;
		cacheDir.mkdirs();

		loadCachedFiles();
	}

	public void clearDiskCache() {
		// Delete *.poly and *.zip files
		for (File file : cacheDir.listFiles()) {
			file.delete();
		}
	}

	private void loadCachedFiles() {
		if (!cacheDir.isDirectory()) {
			return;
		}

		FileFilter filter = new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.getName().endsWith(".json");
			}
		};

		for (File file : cacheDir.listFiles(filter)) {
			loadRegionsFile(file);
		}
	}

	private void loadRegionsFile(File file) {
		FileReader reader = null;
		try {
			reader = new FileReader(file);
			JSONObject jsonObject = new JSONObject(StrUtil.readerToString(reader));

			JSONArray regionsJson = jsonObject.getJSONArray("regions");
			for (int i = 0; i < regionsJson.length(); i++) {
				JSONObject regionJson = regionsJson.getJSONObject(i);

				int adminLevel = regionJson.getInt("admin-level");
				String polyFileName = regionJson.getString("poly-file");
				String regionId = regionJson.getString("region-id");
				JSONObject names = regionJson.getJSONObject("names");

				String localeName;
				try {
					localeName = names.getString(Locale.getDefault().getLanguage());
				} catch (JSONException e) {
					localeName = names.keys().next();
				}

				File polyFile = new File(cacheDir, polyFileName);

				if (polyFile.exists()) {
					RegionDesc regionDesc = new RegionDesc(regionId, localeName, polyFile, adminLevel);
					cachedFiles.put(regionId, regionDesc);
				}
			}
		} catch (IOException e) {
			log.error("Can't read regions json file {}", file.toString(), e);
		} catch (JSONException e) {
			log.error("Json file invalid: {}", file.toString(), e);
		} finally {
			Utils.silentClose(reader);
		}
	}

	public Region getRegion(String regionId) {
		Region region = regionsCache.get(regionId);
		if (region != null) {
			return region;
		}

		RegionDesc regionDesc = cachedFiles.get(regionId);
		if (regionDesc == null) {
			return null;
		}

		FileInputStream input = null;
		try {
			input = new FileInputStream(regionDesc.file);
			region = new Region(input);
			regionsCache.put(regionId, region);
			return region;
		} catch (java.io.IOException e) {
			cachedFiles.remove(regionId);
			return null;
		} finally {
			Utils.silentClose(input);
		}
	}

	public void putRegion(String regionId, Region region) {
		regionsCache.put(regionId, region);
	}

	public void updateDiskCache(String[] cacheUrls) throws IOException {
		if (!cacheDir.isDirectory()) {
			return;
		}

		clearDiskCache();

		File tmpFile = new File(cacheDir, "zipFile.zip");

		for (String cacheUrl : cacheUrls) {
			InputStream inputStream = null;
			FileOutputStream outputStream = null;
			try {
				inputStream = UrlUtil.getInputStream(cacheUrl);
				outputStream = new FileOutputStream(tmpFile);
				StrUtil.copyStream(inputStream, outputStream);

				DirUtil.unzip(tmpFile, cacheDir);

				// Rename regions.json to unique name
				File regionsZipFile = new File(cacheDir, "regions.json");
				if (regionsZipFile.exists()) {
					if (!regionsZipFile.renameTo(new File(cacheDir, cacheUrl.hashCode() + ".json"))) {
						throw new IOException("Can't rename regions.json file");
					}
				}
			} finally {
				Utils.silentClose(inputStream);
				Utils.silentClose(outputStream);
			}
		}

		loadCachedFiles();
	}

	private static class RegionDesc {
		String regionId;
		String name;
		File file;
		int adminLevel;

		public RegionDesc(String regionId, String name, File file, int adminLevel) {
			this.regionId = regionId;
			this.name = name;
			this.file = file;
			this.adminLevel = adminLevel;
		}
	}
}
