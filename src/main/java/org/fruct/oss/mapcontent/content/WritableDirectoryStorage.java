package org.fruct.oss.mapcontent.content;

import org.fruct.oss.mapcontent.content.utils.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

public class WritableDirectoryStorage extends DirectoryStorage {
	private static final Logger log = LoggerFactory.getLogger(WritableDirectoryStorage.class);

	public WritableDirectoryStorage(KeyValue digestCache, String path) {
		super(digestCache, path);

		File file = new File(path);
		if (!file.mkdirs() && !file.isDirectory()) {
			log.warn("Can't mkdirs new path");
		}
	}

	public ContentItem storeContentItem(ContentItem remoteContentItem, InputStream input) throws IOException {
		OutputStream output = null;

		String suffix;
		if (remoteContentItem.getName().endsWith(".ghz")) {
			suffix = ".ghz";
		} else if (remoteContentItem.getName().endsWith(".map")) {
			suffix = ".map";
		} else {
			throw new IOException("Wrong file extension");
		}

		String fileNameStr = remoteContentItem.getHash() + suffix;

		File outputFile = new File(path, fileNameStr + ".roadsignsdownload");
		File targetFile = new File(path, fileNameStr);

		try {
			output = new FileOutputStream(outputFile);

			StrUtil.copyStream(input, output);

			if (!outputFile.renameTo(targetFile))
				throw new IOException("Can't replace original file with loaded file");

			DirectoryContentItem localItem = new DirectoryContentItem(this, digestCache, remoteContentItem.getName());
			localItem.setDescription(remoteContentItem.getDescription());
			localItem.setType(remoteContentItem.getType());
			localItem.setHash(remoteContentItem.getHash());
			localItem.setRegionId(remoteContentItem.getRegionId());
			localItem.setFileName(fileNameStr);
			items.add(localItem);

			return localItem;
		} catch (IOException e) {
			outputFile.delete();
			throw e;
		} finally {
			if (output != null)
				output.close();
		}
	}

	public void migrate(String newPath) {
		path = newPath;
		File file = new File(path);
		if (!file.mkdirs() && !file.isDirectory()) {
			log.warn("Can't mkdirs new path");
		}
	}

	public void markObsolete(ContentItem contentItem) throws IOException {
		digestCache.delete(contentItem.getName());
		for (Iterator<ContentItem> iterator = items.iterator(); iterator.hasNext(); ) {
			ContentItem localItem = iterator.next();

			if (localItem.getName().equals(contentItem.getName())) {
				iterator.remove();
				String itemPath = ((DirectoryContentItem) localItem).getPath();
				File obsoletedItemPath = new File(itemPath + ".obsolete");
				obsoletedItemPath.createNewFile();
			}
		}
	}

	public void deleteObsoleteItems(List<File> protectedFiles) {
		File rootDir = new File(path);

		for (File existingFile : rootDir.listFiles()) {
			File obsoleteFile = new File(existingFile.getPath() + ".obsolete");
			if (obsoleteFile.exists() && !protectedFiles.contains(existingFile)) {
				obsoleteFile.delete();
				existingFile.delete();
			}
		}
	}

	public String getStorageName() {
		return "writable-directory-storage";
	}
}
