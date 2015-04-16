package org.fruct.oss.mapcontent.content;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

import org.fruct.oss.mapcontent.R;
import org.fruct.oss.mapcontent.content.connections.ContentServiceConnection;
import org.fruct.oss.mapcontent.content.contenttype.ContentType;
import org.fruct.oss.mapcontent.content.contenttype.GraphhopperContentType;
import org.fruct.oss.mapcontent.content.contenttype.MapsforgeContentType;
import org.fruct.oss.mapcontent.content.fragments.ContentFragment;
import org.fruct.oss.mapcontent.content.utils.DirUtil;
import org.fruct.oss.mapcontent.content.utils.RegionCache;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ContentService extends Service
		implements SharedPreferences.OnSharedPreferenceChangeListener,
		ContentManager.Listener {
	public static final String[] DEFAULT_ROOT_URLS = {"http://oss.fruct.org/projects/roadsigns/root.xml"};

	private Binder binder = new Binder();

	private KeyValue digestCache;
	private ContentManager contentManager;
	private RegionCache regionCache;

	private Handler handler;
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private SharedPreferences pref;

	private String dataPath;
	private final List<ContentServiceConnection> initializationListeners = new CopyOnWriteArrayList<>();
	private final List<ItemListener> itemListeners = new CopyOnWriteArrayList<>();
	private final List<Listener> listeners = new CopyOnWriteArrayList<>();

	private LocationManager locationManager;
	private Location lastLocation;
	private LocationListener locationListener = new ContentServiceLocationListener();

	private Future<?> initializationFuture;
	private final List<Future<?>> downloadTasks = new ArrayList<>();
	private boolean isSuggestItemRequested = false;

	private boolean disableRegions6;
	private String[] rootUrls = DEFAULT_ROOT_URLS;

	@Override
	public void onCreate() {
		super.onCreate();

		locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

		pref = PreferenceManager.getDefaultSharedPreferences(this);
		handler = new Handler(Looper.getMainLooper());

		dataPath = pref.getString(Settings.PREF_STORAGE_PATH, null);
		digestCache = new KeyValue(this, "digestcache");
		regionCache = new RegionCache(this, new File(getCacheDir(), "region-cache"));
		if (dataPath == null) {
			DirUtil.StorageDirDesc[] contentPaths = DirUtil.getPrivateStorageDirs(this);
			dataPath = contentPaths[0].path;
			pref.edit().putString(Settings.PREF_STORAGE_PATH, dataPath).apply();
		}

		pref.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onDestroy() {
		locationManager.removeUpdates(locationListener);

		pref.unregisterOnSharedPreferenceChangeListener(this);
		executor.shutdown();

		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_NOT_STICKY;
	}

	@NonNull
	public List<ContentItem> getLocalContentItems() {
		return contentManager.getLocalContentItems();
	}

	@NonNull
	public List<ContentItem> getRemoteContentItems() {
		return contentManager.getRemoteContentItems();
	}

	private void notifyInitialized() {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (ContentServiceConnection connection : initializationListeners) {
					connection.onContentServiceInitialized();
				}
			}
		});
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	public void initialize(final String[] requestedContentTypes, final String[] rootUrls, boolean enableRegions6) {
		this.disableRegions6 = !enableRegions6;
		this.rootUrls = rootUrls;

		initializationFuture = executor.submit(new Runnable() {
			@Override
			public void run() {
				HashMap<String, ContentType> contentTypes = new HashMap<>();

				for (String requestedContentType : requestedContentTypes) {
					switch (requestedContentType) {
					case ContentManagerImpl.GRAPHHOPPER_MAP:
						contentTypes.put(requestedContentType, new GraphhopperContentType());
						break;

					case ContentManagerImpl.MAPSFORGE_MAP:
						contentTypes.put(requestedContentType, new MapsforgeContentType(regionCache));
						break;
					}
				}

				contentManager = new ContentManagerImpl(ContentService.this,
						dataPath,
						digestCache,
						regionCache,
						contentTypes);
				try {
					contentManager.garbageCollect();
				} catch (Exception ex) {
					ex.printStackTrace();
				}

				((ContentManagerImpl) contentManager).setListener(ContentService.this);
				locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
						60000, 1000, locationListener, Looper.getMainLooper());

				notifyInitialized();
			}
		});
	}

	public boolean isInitialized() {
		return initializationFuture != null && initializationFuture.isDone() && !initializationFuture.isCancelled();
	}

	public void addInitializationListener(ContentServiceConnection initializationListener) {
		this.initializationListeners.add(initializationListener);
	}

	public void removeInitializationListener(ContentServiceConnection initializationListener) {
		this.initializationListeners.remove(initializationListener);
	}

	public void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	public void addListener(Listener listener) {
		listeners.add(listener);
	}

	public void addItemListener(ItemListener itemListener) {
		itemListeners.add(itemListener);
	}

	public void removeItemListener(ItemListener itemListener) {
		itemListeners.remove(itemListener);
	}

	public void interrupt() {
		synchronized (downloadTasks) {
			for (Future<?> task : downloadTasks) {
				task.cancel(true);
			}
			downloadTasks.clear();
		}
	}

	public boolean deleteContentItem(ContentItem contentItem) {
		return contentManager.deleteContentItem(contentItem);
	}

	/**
	 * Download content item, notifying about download state through listener
	 * @param contentItem remote content item to download
	 */
	public void downloadItem(final ContentItem contentItem) {
		final NetworkContentItem remoteItem = (NetworkContentItem) contentItem;

		Future<?> downloadTask = executor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					startForeground(0, 1, R.drawable.ic_stat_file_file_download,
							"Downloading " + remoteItem.getName());
					ContentItem localContentItem = contentManager.downloadContentItem(remoteItem);
					notifyDownloadFinished(contentItem, localContentItem);
					notifyLocalListReady(getLocalContentItems());
				} catch (InterruptedIOException e) {
					notifyDownloadInterrupted(contentItem);
				} catch (IOException e) {
					notifyErrorDownload(contentItem, e);
				} catch (Exception ex) {
					ex.printStackTrace();
				} finally {
					stopForeground(true);
				}
			}
		});

		synchronized (downloadTasks) {
			downloadTasks.add(downloadTask);
			for (Iterator<Future<?>> iterator = downloadTasks.iterator(); iterator.hasNext(); ) {
				Future<?> task = iterator.next();
				if (task.isDone()) {
					iterator.remove();
				}
			}
		}
	}

	/**
	 * Request content list update
	 * Listener will be used to notify about content list update
	 * @param forceRefresh if set to false, refresh will be skipped if data already loaded
	 */
	public void refresh(boolean forceRefresh) {
		if (!forceRefresh && !contentManager.getRemoteContentItems().isEmpty()) {
			return;
		}

		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					contentManager.refreshRemoteContentList(rootUrls);
					notifyRemoteListReady(contentManager.getRemoteContentItems());


					if (contentManager.checkUpdates()) {
						notifyUpdateReady();
					}
				} catch (Exception e) {
					notifyErrorInitializing(e);
				}
			}
		});
	}

	/**
	 * Get path to unpacked content item
	 * This method marks this item as active, preventing it from garbage collection
	 * @param contentItem local item to request
	 * @return path
	 */
	public String requestContentItem(ContentItem contentItem) {
		String unpackedItemPath = contentManager.activateContentItem(contentItem);

		if (unpackedItemPath == null) {
			return null;
		}

		File regions6Dir = new File(unpackedItemPath, "regions6");
		if (!disableRegions6 && regions6Dir.exists() && regions6Dir.isDirectory()) {
			regionCache.setAdditionalRegions(regions6Dir);
		}

		return unpackedItemPath;
	}

	/**
	 * Get path to content item downloaded file
	 * This method marks this item as active, preventing it from garbage collection
	 * @param contentItem local item to request
	 * @return path
	 */
	public String requestContentItemSource(ContentItem contentItem) {
		String unpacked = contentManager.activateContentItem(contentItem);
		if (unpacked != null) {
			return ((DirectoryContentItem) contentItem).getPath();
		} else {
			return null;
		}
	}

	public void requestRecommendedItem() {
		if (lastLocation == null) {
			// No need to use field isRecommendedItemRequested, because setLocation always check region
			return;
		}

		executor.execute(new Runnable() {
			@Override
			public void run() {
				checkRegion(lastLocation);
			}
		});
	}

	public void requestSuggestedRegion() {
		if (lastLocation == null) {
			isSuggestItemRequested = true;
			return;
		}

		executor.execute(new Runnable() {
			@Override
			public void run() {
				Set<String> ret = new HashSet<>(2);

				List<ContentItem> suggestedItems = contentManager.findSuggestedItems(lastLocation);
				for (ContentItem suggestedItem : suggestedItems) {
					ret.add(suggestedItem.getRegionId());
				}

				notifySuggestedItemsReady(new ArrayList<>(ret));
			}
		});
	}

	public RegionCache getRegionCache() {
		return regionCache;
	}

	private void startForeground(int pendingIntentRequestCode, int notificationCode,
								 @DrawableRes int icon, String contentText) {
		Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
		PendingIntent contentIntent = PendingIntent.getActivity(ContentService.this, pendingIntentRequestCode,
				launchIntent, PendingIntent.FLAG_CANCEL_CURRENT);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(ContentService.this);
		builder.setSmallIcon(icon)
				.setContentTitle("Social navigator")
				.setContentText(contentText)
				.setContentIntent(contentIntent);

		startForeground(notificationCode, builder.build());
	}

	private void setLocation(@NonNull final Location location) {
		lastLocation = location;
		executor.execute(new Runnable() {
			@Override
			public void run() {
				checkRegion(location);

				if (isSuggestItemRequested) {
					requestSuggestedRegion();
					isSuggestItemRequested = false;
				}
			}
		});
	}

	private void checkRegion(Location location) {
		Set<String> contentTypes = new HashSet<>(2);
		contentTypes.add(ContentManagerImpl.GRAPHHOPPER_MAP);
		contentTypes.add(ContentManagerImpl.MAPSFORGE_MAP);
		List<ContentItem> regionContentItems = contentManager.findContentItemsByRegion(location);
		for (ContentItem contentItem : regionContentItems) {
			if (contentTypes.remove(contentItem.getType())) {
				contentManager.unpackContentItem(contentItem);
				notifyRecommendedRegionItemReady(contentItem);
			}
		}

		for (String contentType : contentTypes) {
			notifyRecommendedRegionItemNotFound(contentType);
		}
	}

	private void notifyLocalListReady(final List<ContentItem> items) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Listener listener : listeners) {
					listener.localListReady(items);
				}
			}
		});
	}

	private void notifyRemoteListReady(final List<ContentItem> items) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Listener listener : listeners) {
					listener.remoteListReady(items);
				}
			}
		});
	}

	private void notifyDownloadStateUpdated(final ContentItem item, final int downloaded, final int max) {
		handler.post(new Runnable() {
			@Override
			public void run() {

				for (Listener listener : listeners) {
					listener.downloadStateUpdated(item, downloaded, max);
				}
			}
		});
	}

	private void notifyDownloadFinished(final ContentItem localItem, final ContentItem remoteItem) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Listener listener : listeners) {
					listener.downloadFinished(localItem, remoteItem);
				}
			}
		});
	}

	private void notifyDownloadInterrupted(final ContentItem remoteItem) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Listener listener : listeners) {
					listener.downloadInterrupted(remoteItem);
				}
			}
		});
	}

	private void notifyErrorDownload(final ContentItem remoteItem, final IOException ex) {
		handler.post(new Runnable() {
			@Override
			public void run() {

				for (Listener listener : listeners) {
					listener.errorDownloading(remoteItem, ex);
				}
			}
		});
	}

	private void notifyErrorInitializing(final Exception ex) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (Listener listener : listeners) {
					listener.errorInitializing(ex);
				}
			}
		});
	}

	private void notifyRecommendedRegionItemReady(final ContentItem localItem) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (ItemListener listener : itemListeners) {
					listener.recommendedRegionItemReady(localItem);
				}
			}
		});
	}

	private void notifyRecommendedRegionItemNotFound(final String contentType) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (ItemListener listener : itemListeners) {
					listener.recommendedRegionItemNotFound(contentType);
				}
			}
		});
	}

	private void notifyRequestContentReload() {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (ItemListener listener : itemListeners) {
					listener.requestContentReload();
				}
			}
		});
	}

	private void notifyUpdateReady() {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (ItemListener listener : itemListeners) {
					listener.updateReady();
				}
			}
		});
	}

	private void notifySuggestedItemsReady(final List<String> regionIds) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				for (ItemListener listener : itemListeners) {
					listener.suggestedItemsReady(regionIds);
				}
			}
		});

	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(Settings.PREF_STORAGE_PATH)) {
			final String newPath = sharedPreferences.getString(key, null);
			if (contentManager != null) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						try {
							startForeground(1, 2, R.drawable.ic_stat_content_content_copy,
									"Moving content to " + newPath);
							contentManager.migrate(newPath);
							notifyRequestContentReload();
						} finally {
							stopForeground(true);
						}
					}
				});
			}
		}
	}

	@Override
	public void downloadStateUpdated(ContentItem item, int downloaded, int max) {
		notifyDownloadStateUpdated(item, downloaded, max);
	}

	public class Binder extends android.os.Binder {
		public ContentService getService() {
			return ContentService.this;
		}
	}

	private class ContentServiceLocationListener implements LocationListener {
		@Override
		public void onLocationChanged(Location location) {
			setLocation(location);
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {

		}

		@Override
		public void onProviderEnabled(String provider) {

		}

		@Override
		public void onProviderDisabled(String provider) {

		}
	}

	public interface Listener {
		void localListReady(List<ContentItem> list);

		void remoteListReady(List<ContentItem> list);

		void downloadStateUpdated(ContentItem item, int downloaded, int max);

		void downloadFinished(ContentItem localItem, ContentItem remoteItem);

		void errorDownloading(ContentItem item, IOException e);

		void errorInitializing(Exception e);

		void downloadInterrupted(ContentItem item);
	}

	public interface ItemListener {
		void recommendedRegionItemReady(ContentItem contentItem);
		void recommendedRegionItemNotFound(String contentType);

		void updateReady();

		void suggestedItemsReady(List<String> regionIds);

		void requestContentReload();
	}
}
