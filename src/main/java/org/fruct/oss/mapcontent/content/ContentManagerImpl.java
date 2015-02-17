package org.fruct.oss.mapcontent.content;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.preference.PreferenceManager;

import org.apache.commons.io.IOUtils;
import org.fruct.oss.mapcontent.BuildConfig;
import org.fruct.oss.mapcontent.content.contenttype2.*;
import org.fruct.oss.mapcontent.content.contenttype2.ContentType;
import org.fruct.oss.mapcontent.content.contenttypes.GraphhopperMapType;
import org.fruct.oss.mapcontent.content.contenttypes.MapsforgeMapType;
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
import java.io.InterruptedIOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class ContentManagerImpl implements ContentManager {
	private static final Logger log = LoggerFactory.getLogger(ContentManagerImpl.class);

	public static final String[] REMOTE_CONTENT_URLS = {
			"http://oss.fruct.org/projects/roadsigns/root.xml"};


	public static final String GRAPHHOPPER_MAP = "graphhopper-map";
	public static final String MAPSFORGE_MAP = "mapsforge-map";
	public static final String SAFEGUARD_STRING = "content-manager";

	private String contentRootPath;
	private KeyValue digestCache;

	private final SharedPreferences pref;

	private final NetworkStorage networkStorage;

	private final WritableDirectoryStorage mainLocalStorage;
	private final List<ContentStorage> localStorages = new ArrayList<ContentStorage>();
	private final HashMap<String, ContentType> contentTypes = new HashMap<String, ContentType>();

	private final Map<String, Region> regions = new HashMap<String, Region>();

	private List<ContentItem> localContentItems;
	private List<ContentItem> remoteContentItems;

	private Listener listener;

	public ContentManagerImpl(Context context, String contentRootPath, KeyValue digestCache,
							  HashMap<String, ContentType> contentTypes) {
		if (true) {
			REMOTE_CONTENT_URLS[0] = "http://kappa.cs.petrsu.ru/~ivashov/mordor.xml";
		}

		this.contentRootPath = contentRootPath;
		this.digestCache = digestCache;

		pref = PreferenceManager.getDefaultSharedPreferences(context);

		networkStorage = new NetworkStorage(REMOTE_CONTENT_URLS);
		mainLocalStorage = new WritableDirectoryStorage(digestCache, contentRootPath + "/content-manager/storage");

		String[] additionalStoragePaths = DirUtil.getExternalDirs(context);
		for (String path : additionalStoragePaths) {
			DirectoryStorage storage = new DirectoryStorage(digestCache, path);
			localStorages.add(storage);
		}

		this.contentTypes.putAll(contentTypes);

		refreshLocalItemsList();
		refreshRemoteItemsList();
	}

	public void setListener(Listener listener) {
		this.listener = listener;
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
					.putString("pref-" + contentItem.getType() + "-active-package", contentItemPackageFile)
					.putString("pref-" + contentItem.getType() + "-active-unpacked",
							unpackedDir.getUnpackedDir().toString())
					.apply();
			return unpackedDir.getUnpackedDir().toString();
		} else {
			return null;
		}
	}

	@Override
	public synchronized ContentItem downloadContentItem(final NetworkContentItem remoteItem) throws IOException {
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

			// Old directory now garbage, delete it from content list
			/*ContentItem deletedItem = deleteFromLocalList(remoteItem);
			if (deletedItem != null) {
				garbageItem(deletedItem);
			}*/

			ContentItem newContentItem = mainLocalStorage.storeContentItem(remoteItem, inputStream);
			localContentItems.add(newContentItem);
			return newContentItem;
		} finally {
			Utils.silentClose(conn);
		}
	}

	@Override
	public synchronized void garbageCollect() {
		List<File> activePackageFiles = new ArrayList<File>();
		List<File> activeUnpackedFiles = new ArrayList<File>();

		for (ContentType contentType : contentTypes.values()) {
			String activePackagePath = pref.getString("pref-" + contentType.getName() + "-active-package",
					null);
			String activeUnpackedPath = pref.getString("pref-" + contentType.getName() + "-active-unpacked",
					null);

			File activePackageFile = new File(activePackagePath);
			File activeUnpackedFile = new File(activeUnpackedPath);

			activePackageFiles.add(activePackageFile);
			activeUnpackedFiles.add(activeUnpackedFile);
		}

		List<String> migrationHistory = Utils.deserializeStringList(pref.getString("pref-migration-history", null));

		// Delete inactive unpacked directories
		//List<String> allRoots = new ArrayList<String>(migrationHistory);
		//allRoots.add(contentRootPath);

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

		File unpackedRoot = new File(contentRootPath, "/content-manager/unpacked");
		for (File unpackedDir : unpackedRoot.listFiles()) {
			if (!activeUnpackedFiles.contains(unpackedDir)) {
				deleteDir(unpackedDir, SAFEGUARD_STRING);
			}
		}

		hashCode();


		/*List<String> migrationHistory = Utils.deserializeStringList(pref.getString("pref-migration-history", null));

		// Delete inactive unpacked directories
		List<String> allRoots = new ArrayList<String>(migrationHistory);
		allRoots.add(contentRootPath);

		for (String contentRoot : allRoots) {
			File unpackedRoot = new File(contentRoot, "/content-manager/unpacked");
			for (File unpackedDirFile : unpackedRoot.listFiles()) {
				//UnpackedDir unpackedDir = new UnpackedDir(unpackedDirFile.getPath());
				if (!activeUnpackedPaths.contains(unpackedDirFile.getAbsolutePath())) {
					deleteDir(unpackedDirFile, SAFEGUARD_STRING);
				}
			}
		}

		// Delete historical packages that are not unpacked
		for (String contentRoot : migrationHistory) {
			File storageRoot = new File(contentRoot, "/content-manager/storage");
			UnpackedDir unpackedDir = new UnpackedDir(unpackedDirFile.getPath());

			//DirectoryStorage
		}

		hashCode();*/

	}

	@Override
	public void migrate(String newRootPath) {
		File fromDir = new File(contentRootPath);
		File toDir = new File(newRootPath);

		try {
			copyDirectory(fromDir, toDir);
			mainLocalStorage.migrate(newRootPath + "/content-manager/storage");

			// Update migration history
			addMigrationHistoryItem();

			contentRootPath = newRootPath;
		} catch (IOException e) {
			log.error("Can't migrate data directory");
			deleteDir(toDir, SAFEGUARD_STRING);
		}
	}

	private void addMigrationHistoryItem() {
		List<String> migrationHistory = Utils.deserializeStringList(
				pref.getString("pref-migration-history", null));
		migrationHistory.add(contentRootPath);
		pref.edit()
				.putString("pref-migration-history", Utils.serializeStringList(migrationHistory))
				.apply();
	}

	private void garbageItem(ContentItem contentItem) {
		UnpackedDir unpackedDir = new UnpackedDir(new File(contentRootPath), contentItem);
		if (unpackedDir.isUnpacked()) {
			unpackedDir.markGarbage();
		}
		digestCache.delete(contentItem.getName());
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

	private void refreshRemoteItemsList() {
		try {
			networkStorage.updateContentList();
			List<ContentItem> remoteItems = new ArrayList<ContentItem>();
			remoteItems.addAll(networkStorage.getContentList());
			remoteContentItems = remoteItems;
		} catch (IOException e) {
			//notifyErrorInitializing(e);
		} finally {
			//notifyRemoteListReady(remoteItems);
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

	private ContentItem deleteFromLocalList(ContentItem contentItem) {
		for (Iterator<ContentItem> iterator = localContentItems.iterator(); iterator.hasNext(); ) {
			ContentItem localItem = iterator.next();

			if (localItem.getName().equals(contentItem.getName())) {
				iterator.remove();
				return localItem;
			}
		}

		return null;
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
