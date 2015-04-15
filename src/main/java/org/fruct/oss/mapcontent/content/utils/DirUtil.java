package org.fruct.oss.mapcontent.content.utils;

import android.content.Context;
import android.os.Environment;
import android.support.annotation.NonNull;

import org.fruct.oss.mapcontent.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DirUtil {
	private static final Logger log = LoggerFactory.getLogger(DirUtil.class);

	/*
	public void writeFileString(@NonNull File file, @NonNull String string) throws IOException {
		FileWriter writer = null;
		try {
			writer = new FileWriter(file);
			writer.write(string);
		} finally {
			Utils.silentClose(writer);
		}
	}

	public String readFileString(@NonNull File file) throws IOException {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			return StrUtil.readerToString(reader);
		} finally {
			Utils.silentClose(reader);
		}
	}
*/

	public static void unzip(File zipFile, File outputDir) throws IOException {
		ZipInputStream zipInputStream = null;
		FileOutputStream fileOutputStream = null;
		byte[] buffer = new byte[4096];
		try {
			zipInputStream = new ZipInputStream(new FileInputStream(zipFile));
			ZipEntry zipEntry;
			while ((zipEntry = zipInputStream.getNextEntry()) != null) {
				File file = new File(outputDir, zipEntry.getName());
				if (zipEntry.isDirectory()) {
					file.mkdir();
				} else {
					fileOutputStream = new FileOutputStream(new File(outputDir, zipEntry.getName()));

					int readed;
					while ((readed = zipInputStream.read(buffer)) > 0) {
						fileOutputStream.write(buffer, 0, readed);
					}

					fileOutputStream.close();
					fileOutputStream = null;
				}
			}

			log.info("Zip archive successfully extracted");
		} catch (IOException e) {
			log.error("Can't extract archive {}", zipFile);
			throw e;
		} finally {
			Utils.silentClose(zipInputStream);
			Utils.silentClose(fileOutputStream);
		}
	}

	public static String[] getSecondaryDirs() {
		List<String> ret = new ArrayList<String>();
		String secondaryStorageString = System.getenv("SECONDARY_STORAGE");
		if (secondaryStorageString != null && !secondaryStorageString.trim().isEmpty()) {
			String[] dirs = secondaryStorageString.split(":");

			for (String dir : dirs) {
				File file = new File(dir);
				if (file.isDirectory() && file.canWrite()) {
					ret.add(dir);
				}
			}

			if (ret.isEmpty())
				return null;
			else
				return ret.toArray(new String[ret.size()]);

		} else {
			return null;
		}
	}

	public static String[] getExternalDirs(Context context) {
		List<String> paths = new ArrayList<String>();
		String[] secondaryDirs = getSecondaryDirs();
		if (secondaryDirs != null) {
			for (String secondaryDir : secondaryDirs) {
				paths.add(secondaryDir + "/roadsigns");
			}
		}

		File externalStorageDir = Environment.getExternalStorageDirectory();
		if (externalStorageDir != null && externalStorageDir.isDirectory()) {
			paths.add(Environment.getExternalStorageDirectory().getPath() + "/roadsigns");
		}

		return paths.toArray(new String[paths.size()]);
	}

	public static StorageDirDesc[] getPrivateStorageDirs(Context context) {
		List<StorageDirDesc> ret = new ArrayList<StorageDirDesc>();

		// Secondary external storage
		String[] secondaryDirs = getSecondaryDirs();
		if (secondaryDirs != null) {
			for (String secondaryStoragePath : secondaryDirs) {
				ret.add(new StorageDirDesc(R.string.storage_path_sd_card, secondaryStoragePath + "/Android/data/" + context.getPackageName() + "/files"));
			}
		}

		// External storage
		File externalDir = context.getExternalFilesDir(null);
		if (externalDir != null)
			ret.add(new StorageDirDesc(R.string.storage_path_external, externalDir.getPath()));

		// Internal storage
		ret.add(new StorageDirDesc(R.string.storage_path_internal, context.getDir("other", 0).getPath()));

		return ret.toArray(new StorageDirDesc[ret.size()]);
	}

	public static void deleteDir(File dir) {
		if (!dir.exists() && !dir.isDirectory())
			return;

		File[] listFiles = dir.listFiles();
		for (File file : listFiles) {
			if (!file.isDirectory()) {
				file.delete();
			}
		}

		dir.delete();
	}

	public static class StorageDirDesc {
		public final int nameRes;
		public final String path;

		public StorageDirDesc(int nameRes, String path) {
			this.nameRes = nameRes;
			this.path = path;
		}
	}
}
