package org.fruct.oss.mapcontent.content.utils;

import org.apache.http.client.utils.URIUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class RegionCache {
	private final File cacheDir;

	private Map<String, File> cachedFiles = new HashMap<>();
	private Map<String, Region> regionsCache = new HashMap<>();

	public RegionCache(File cacheDir) {
		this.cacheDir = cacheDir;
		cacheDir.mkdirs();

		loadCachedFiles();
	}

	public void clearDiskCache() {
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
				return pathname.getName().endsWith(".poly");
			}
		};

		for (File file : cacheDir.listFiles(filter)) {
			String fileName = file.getName();
			String regionId = fileName.substring(0, fileName.length() - 5 /* '.poly' length */);
			cachedFiles.put(regionId, file);
		}
	}

	public Region getRegion(String regionId) {
		Region region = regionsCache.get(regionId);
		if (region != null) {
			return region;
		}

		File regionFile = cachedFiles.get(regionId);
		if (regionFile == null) {
			return null;
		}

		FileInputStream input = null;
		try {
			input = new FileInputStream(regionFile);
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

	public void putRegions(File zipArchive) throws IOException {
		DirUtil.unzip(zipArchive, cacheDir);
		loadCachedFiles();
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
			} finally {
				Utils.silentClose(inputStream);
				Utils.silentClose(outputStream);
			}
		}

		loadCachedFiles();
	}
}
