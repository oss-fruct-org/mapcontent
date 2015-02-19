package org.fruct.oss.mapcontent.content;

import org.fruct.oss.mapcontent.content.utils.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;

public class UnpackedDir {
	private File unpackedDir;

	/**
	 * Create from known contentItem
	 * @param unpackedRootDir root path for unpacked archives
	 * @param contentItem content item
	 */
	public UnpackedDir(File unpackedRootDir, ContentItem contentItem) {
		this.unpackedDir = new File(unpackedRootDir, contentItem.getHash());
		unpackedDir.mkdirs();
		//writeString(new File(unpackedDir, "content-type.txt"), contentItem.getType());
	}

	/**
	 * Create from already unpacked path
	 * @param unpackedPath patch to unpacked item
	 */
	public UnpackedDir(String unpackedPath) {
		this.unpackedDir = new File(unpackedPath);
	}

	/*public String getContentType() {
		if (contentType == null) {
			return (contentType = readString(new File(unpackedDir, "content-type.info")));
		} else {
			return contentType;
		}
	}*/

	public File getUnpackedDir() {
		return unpackedDir;
	}


	public boolean isUnpacked() {
		return new File(unpackedDir, "unpacked.info").exists();
	}

	/*public boolean isGarbage() {
		return new File(unpackedDir, "garbage.info").exists();
	}*/

	public void markUnpacked() {
		try {
			new File(unpackedDir, "unpacked.info").createNewFile();
		} catch (IOException ignored) {
		}
	}

	private String readString(File file) {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			return reader.readLine();
		} catch (IOException e) {
			return null;
		} finally {
			Utils.silentClose(reader);
		}
	}

	private void writeString(File file, String str) {
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(file);
			writer.print(str);
			writer.flush();
		} catch (FileNotFoundException ignored) {
			ignored.toString();
		} finally {
			Utils.silentClose(writer);
		}
	}

	/*public void markGarbage() {
		try {
			new File(unpackedDir, "garbage.info").createNewFile();
		} catch (IOException ignored) {
		}
	}*/
}
