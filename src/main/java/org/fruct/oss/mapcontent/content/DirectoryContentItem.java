package org.fruct.oss.mapcontent.content;

import org.fruct.oss.mapcontent.content.utils.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;

public class DirectoryContentItem implements ContentItem {
	private static final Logger log = LoggerFactory.getLogger(DirectoryStorage.class);

	private final KeyValue digestCache;
	private final DirectoryStorage storage;

	private String type;
	private String name;
	private String description;
	private String regionId;

	private String hash;

	private String fileName;

	public DirectoryContentItem(DirectoryStorage storage, KeyValue digestCache, String name) {
		this.storage = storage;
		this.digestCache = digestCache;
		this.name = name;
	}

	public void setType(String type) {
		this.type = type;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String getStorage() {
		return storage.getStorageName();
	}

	@Override
	public String getHash() {
		if (hash != null) {
			return hash;
		}

		hash = digestCache.get(name);

		if (hash == null) {
			log.trace("Sha1 of {}", getPath());

			try {
				FileInputStream input = new FileInputStream(getPath());
				hash = StrUtil.hashStream(input, "sha1");
				digestCache.put(name, hash);
			} catch (IOException e) {
				throw new RuntimeException("Can't get hash of file " + name);
			}
		}

		return hash;
	}

	@Override
	public String getRegionId() {
		return regionId;
	}

	@Override
	public boolean isDownloadable() {
		return false;
	}

	@Override
	public boolean isReadonly() {
		return !(storage instanceof WritableDirectoryStorage);
	}

	public String getPath() {
		return storage.getPath() + "/" + fileName;
	}

	public void setHash(String hash) {
		this.hash = hash;
	}

	public void setRegionId(String regionId) {
		this.regionId = regionId;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getFileName() {
		return fileName;
	}
}
