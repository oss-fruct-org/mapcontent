package org.fruct.oss.mapcontent.content;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.preference.PreferenceManager;

import org.fruct.oss.mapcontent.content.contenttype.ContentType;
import org.fruct.oss.mapcontent.content.utils.DigestInputStream;
import org.fruct.oss.mapcontent.content.utils.DirUtil;
import org.fruct.oss.mapcontent.content.utils.ProgressInputStream;
import org.fruct.oss.mapcontent.content.utils.Region;
import org.fruct.oss.mapcontent.content.utils.StrUtil;
import org.fruct.oss.mapcontent.content.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class ContentManagerImpl implements ContentManager {
	private static final Logger log = LoggerFactory.getLogger(ContentManagerImpl.class);

	public static final String GRAPHHOPPER_MAP = "graphhopper-map";
	public static final String MAPSFORGE_MAP = "mapsforge-map";
	public static final String SAFEGUARD_STRING = "content-manager";

	private String contentRootPath;
	private KeyValue digestCache;

	private final SharedPreferences pref;

	private final WritableDirectoryStorage mainLocalStorage;
	private final List<ContentStorage> localStorages = new ArrayList<ContentStorage>();
	private final HashMap<String, ContentType> contentTypes = new HashMap<String, ContentType>();

	private final Map<String, Region> regions = new HashMap<String, Region>();

	private NetworkStorage networkStorage;

	private List<ContentItem> localContentItems;
	private List<ContentItem> remoteContentItems;

	private Listener listener;

	public ContentManagerImpl(Context context, String contentRootPath, KeyValue digestCache,
							  HashMap<String, ContentType> contentTypes) {
		this.contentRootPath = contentRootPath;
		this.digestCache = digestCache;

		pref = PreferenceManager.getDefaultSharedPreferences(context);

		mainLocalStorage = new WritableDirectoryStorage(digestCache, contentRootPath + "/content-manager/storage");

		String[] additionalStoragePaths = DirUtil.getExternalDirs(context);
		for (String path : additionalStoragePaths) {
			DirectoryStorage storage = new DirectoryStorage(digestCache, path);
			localStorages.add(storage);
		}

		this.contentTypes.putAll(contentTypes);

		refreshLocalItemsList();
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	@Override
	public void refreshRemoteContentList(String[] rootUrls) throws IOException {
		networkStorage = new NetworkStorage(rootUrls);

		networkStorage.updateContentList();
		List<ContentItem> remoteItems = new ArrayList<ContentItem>();
		remoteItems.addAll(networkStorage.getContentList());
		remoteContentItems = remoteItems;
	}

	@Override
	public synchronized List<ContentItem> getLocalContentItems() {
		return localContentItems;
	}

	@Override
	public synchronized List<ContentItem> getRemoteContentItems() {
		return remoteContentItems;
	}

	@Override
	public synchronized List<ContentItem> findContentItemsByRegion(Location location) {
		List<ContentItem> matchingItems = new ArrayList<ContentItem>();

		for (ContentItem contentItem : localContentItems) {
			// TODO: this ContentType retrieving can be optimized
			ContentType contentType = contentTypes.get(contentItem.getType());
			String contentItemPackageFile = ((DirectoryContentItem) contentItem).getPath();
			Region region = regions.get(contentItem.getRegionId());

			// Try load region from content item package
			if (region == null) {
				region = contentType.extractRegion(contentItem, contentItemPackageFile);
			}

			if (region == null) {
				// Content item can't provide region
				if (contentType.checkRegion(contentItem, contentItemPackageFile, location)) {
					matchingItems.add(contentItem);
				}
			} else {
				regions.put(contentItem.getRegionId(), region);
				if (region.testHit(location.getLatitude(), location.getLongitude())) {
					matchingItems.add(contentItem);
				}
			}
		}

		return matchingItems;
	}

	@Override
	public synchronized void unpackContentItem(ContentItem contentItem) {
		ContentType contentType = contentTypes.get(contentItem.getType());
		String contentItemPackageFile = ((DirectoryContentItem) contentItem).getPath();

		File unpackedRootDir = new File(contentRootPath, "/content-manager/unpacked");
		unpackedRootDir.mkdirs();
		UnpackedDir unpackedDir = new UnpackedDir(unpackedRootDir, contentItem);

		if (!unpackedDir.isUnpacked()) {
			try {
				contentType.unpackContentItem(contentItem, contentItemPackageFile,
						unpackedDir.getUnpackedDir().toString());
				unpackedDir.markUnpacked();
			} catch (IOException e) {
				// TODO: handle error
			}
		}
	}

	@Override
	public synchronized String activateContentItem(ContentItem contentItem) {
		String contentItemPackageFile = ((DirectoryContentItem) contentItem).getPath();
		File unpackedRootDir = new File(contentRootPath, "/content-manager/unpacked");
		UnpackedDir unpackedDir = new UnpackedDir(unpackedRootDir, contentItem);

		if (unpackedDir.isUnpacked()) {
			pref.edit()
					.putString(getActivePackagePrefKey(contentItem.getType()), contentItemPackageFile)
					.putString(getActiveUnpackedPrefKey(contentItem.getType()),
							unpackedDir.getUnpackedDir().toString())
					.apply();
			return unpackedDir.getUnpackedDir().toString();
		} else {
			return null;
		}
	}

	@Override
	public synchronized ContentItem downloadContentItem(final NetworkContentItem remoteItem) throws IOException {
		if (networkStorage == null) {
			throw new IllegalStateException("Network storage haven't been initialized");
		}

		InputStream conn = null;
		try {
			conn = networkStorage.loadContentItem(remoteItem.getUrl());

			InputStream inputStream = new ProgressInputStream(conn, remoteItem.getDownloadSize(),
					100000, new ProgressInputStream.ProgressListener() {
				@Override
				public void update(int current, int max) {
					if (listener != null) {
						listener.downloadStateUpdated(remoteItem, current, max);
					}
				}
			});

			// Setup gzip compression
			if ("gzip".equals(remoteItem.getCompression())) {
				log.info("Using gzip compression");
				inputStream = new GZIPInputStream(inputStream);
			}

			// Setup content validation
			try {
				inputStream = new DigestInputStream(inputStream, "sha1", remoteItem.getHash());
			} catch (NoSuchAlgorithmException e) {
				log.warn("Unsupported hash algorithm");
			}

			mainLocalStorage.markObsolete(remoteItem);
			ContentItem contentItem = mainLocalStorage.storeContentItem(remoteItem, inputStream);
			refreshLocalItemsList();
			return contentItem;
		} finally {
			Utils.silentClose(conn);
		}
	}

	@Override
	public synchronized void garbageCollect() {
		List<File> activePackageFiles = new ArrayList<File>();
		List<File> activeUnpackedFiles = new ArrayList<File>();

		for (ContentType contentType : contentTypes.values()) {
			String activeUnpackedPath = getActiveUnpacked(contentType.getName());
			String activePackagePath = getActivePackage(contentType.getName());

			if (activeUnpackedPath != null) {
				assert activePackagePath != null;

				File activePackageFileObsolete = new File(activePackagePath + ".obsolete");
				File activePackageFile = new File(activePackagePath);
				File activeUnpackedFile = new File(activeUnpackedPath);

				if (activePackageFileObsolete.exists()) {
					deleteDir(activeUnpackedFile, SAFEGUARD_STRING);
					pref.edit()
							.remove(getActiveUnpackedPrefKey(contentType.getName()))
							.remove(getActivePackagePrefKey(contentType.getName()))
							.apply();
				} else {
					activePackageFiles.add(activePackageFile);
					activeUnpackedFiles.add(activeUnpackedFile);
				}
			}
		}

		List<String> migrationHistory = Utils.deserializeStringList(pref.getString("pref-migration-history", null));

		rootLoop:
		for (String root : migrationHistory) {
			File rootFile = new File(root, "content-manager");

			if (!rootFile.isDirectory()) {
				continue;
			}

			for (File activePackageFile : activePackageFiles) {
				if (isParent(rootFile, activePackageFile)) {
					continue rootLoop;
				}
			}

			for (File activeUnpackedFile : activeUnpackedFiles) {
				if (isParent(rootFile, activeUnpackedFile)) {
					continue rootLoop;
				}
			}

			deleteDir(rootFile, SAFEGUARD_STRING);
		}

		// Delete unpacked files that are not active
		File unpackedRoot = new File(contentRootPath, "/content-manager/unpacked");
		if (unpackedRoot.exists() && unpackedRoot.isDirectory()) {
			for (File unpackedDir : unpackedRoot.listFiles()) {
				if (!activeUnpackedFiles.contains(unpackedDir)) {
					deleteDir(unpackedDir, SAFEGUARD_STRING);
				}
			}
		}

		mainLocalStorage.deleteObsoleteItems(activePackageFiles);
	}

	@Override
	public void migrate(String newRootPath) {
		File fromDir = new File(contentRootPath);
		File toDir = new File(newRootPath);

		try {
			copyDirectory(fromDir, toDir);
			mainLocalStorage.migrate(newRootPath + "/content-manager/storage");

			// Update migration history
			addMigrationHistoryItem(contentRootPath, newRootPath);

			contentRootPath = newRootPath;
		} catch (IOException e) {
			log.error("Can't migrate data directory");
			deleteDir(toDir, SAFEGUARD_STRING);
		}
	}

	@Override
	public boolean deleteContentItem(ContentItem contentItem) {
		try {
			mainLocalStorage.markObsolete(contentItem);
			refreshLocalItemsList();
		} catch (IOException e) {
			return false;
		}
		return true;
	}

	private String getActivePackage(String contentType) {
		return pref.getString(getActivePackagePrefKey(contentType), null);
	}

	private String getActiveUnpacked(String contentType) {
		return pref.getString(getActiveUnpackedPrefKey(contentType), null);
	}

	private String getActivePackagePrefKey(String contentType) {
		return "pref-" + contentType + "-active-package";
	}

	private String getActiveUnpackedPrefKey(String contentType) {
		return "pref-" + contentType + "-active-unpacked";
	}

	private void addMigrationHistoryItem(String oldPath, String newPath) {
		List<String> migrationHistory = Utils.deserializeStringList(
				pref.getString("pref-migration-history", null));
		migrationHistory.add(oldPath);
		migrationHistory.remove(newPath);
		pref.edit()
				.putString("pref-migration-history", Utils.serializeStringList(migrationHistory))
				.apply();
	}

	private void refreshLocalItemsList() {
		try {
			List<ContentItem> localItems = new ArrayList<ContentItem>();

			mainLocalStorage.updateContentList();
			localItems.addAll(mainLocalStorage.getContentList());

			for (ContentStorage storage : localStorages) {
				try {
					storage.updateContentList();
					localItems.addAll(storage.getContentList());
				} catch (IOException e) {
					log.warn("Can't load additional local storage");
				}
			}

			localContentItems = localItems;
		} catch (IOException e) {
			// TODO: error
		}
	}

	private int copyDirectory(File fromDir, File toDir) throws IOException {
		int count = 0;
		for (File file : fromDir.listFiles()) {
			if (file.isFile()) {
				File newFile = new File(toDir, file.getName());

				FileInputStream inputStream = new FileInputStream(file);
				FileOutputStream outputStream = new FileOutputStream(newFile);


				StrUtil.copyStream(inputStream, outputStream);

				Utils.silentClose(inputStream);
				Utils.silentClose(outputStream);
			} else if (file.isDirectory()) {
				File newDir = new File(toDir, file.getName());
				if (!newDir.mkdir() && !newDir.isDirectory()) {
					throw new IOException("Can't create directory " + newDir.getName());
				}

				count += copyDirectory(file, newDir);
			}
		}

		return count;
	}

	private boolean deleteDir(File dir, String safeguardString) {
		if (dir == null || !dir.isDirectory())
			return false;

		if (safeguardString != null && !dir.getAbsolutePath().contains(safeguardString)) {
			throw new SecurityException("Application tries to delete illegal directory");
		}

		boolean success = true;
		for (File file : dir.listFiles()) {
			if (file.isFile()) {
				if (!file.delete())
					success = false;
			} else if (file.isDirectory()) {
				if (!deleteDir(file, safeguardString)) {
					success = false;
				}
			}
		}

		if (success) {
			return dir.delete();
		} else {
			return false;
		}
	}

	private boolean isParent(File parent, File child) {
		if (child == null) {
			return false;
		}

		if (parent.equals(child)) {
			return true;
		}

		return isParent(parent, child.getParentFile());
	}
}
