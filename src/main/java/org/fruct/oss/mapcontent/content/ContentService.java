package org.fruct.oss.mapcontent.content;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import org.fruct.oss.mapcontent.R;
import org.fruct.oss.mapcontent.content.connections.ContentServiceConnection;
import org.fruct.oss.mapcontent.content.contenttype.*;
import org.fruct.oss.mapcontent.content.contenttype.ContentType;
import org.fruct.oss.mapcontent.content.utils.DirUtil;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ContentService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener, ContentManager.Listener {
	private Binder binder = new Binder();

	private KeyValue digestCache;
	private ContentManager contentManager;

	private Handler handler;
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private SharedPreferences pref;


	private String dataPath;
	private List<ContentServiceConnection> initializationListeners = new ArrayList<ContentServiceConnection>();
	private final List<Listener> listeners = new ArrayList<Listener>();

	private Location previousLocation;

	private Future<?> initializationFuture;
	private final List<Future<?>> downloadTasks = new ArrayList<Future<?>>();

	@Override
	public void onCreate() {
		super.onCreate();

		pref = PreferenceManager.getDefaultSharedPreferences(this);
		handler = new Handler(Looper.getMainLooper());

		dataPath = pref.getString(Settings.PREF_STORAGE_PATH, null);
		digestCache = new KeyValue(this, "digestcache");

		if (dataPath == null) {
			DirUtil.StorageDirDesc[] contentPaths = DirUtil.getPrivateStorageDirs(this);
			dataPath = contentPaths[0].path;
			pref.edit().putString(Settings.PREF_STORAGE_PATH, dataPath).apply();
		}

		initializationFuture = executor.submit(new Runnable() {
			@Override
			public void run() {
				HashMap<String, ContentType> contentTypes = new HashMap<String, ContentType>();
				contentTypes.put(ContentManagerImpl.GRAPHHOPPER_MAP, new GraphhopperContentType());
				contentManager = new ContentManagerImpl(ContentService.this, dataPath, digestCache, contentTypes);
				((ContentManagerImpl) contentManager).setListener(ContentService.this);
				notifyInitialized();
			}
		});

		pref.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onDestroy() {
		pref.unregisterOnSharedPreferenceChangeListener(this);
		executor.shutdown();

		super.onDestroy();
	}

	public List<ContentItem> getLocalContentItems() {
		return contentManager.getLocalContentItems();
	}

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

	public void interrupt() {

	}

	public boolean deleteContentItem(ContentItem contentItem) {
		return false;
	}

	public void downloadItem(final ContentItem contentItem) {
		final NetworkContentItem remoteItem = (NetworkContentItem) contentItem;

		Future<?> downloadTask = executor.submit(new Runnable() {
			@Override
			public void run() {
				try {
					Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
					PendingIntent contentIntent = PendingIntent.getActivity(ContentService.this, 0,
							launchIntent, PendingIntent.FLAG_CANCEL_CURRENT);

					NotificationCompat.Builder builder = new NotificationCompat.Builder(ContentService.this);
					builder.setSmallIcon(R.drawable.ic_launcher)
							.setContentTitle("Social navigator")
							.setContentText("Downloading " + remoteItem.getName())
							.setContentIntent(contentIntent);

					startForeground(1, builder.build());
					ContentItem localContentItem = contentManager.downloadContentItem(remoteItem);
					notifyDownloadFinished(contentItem, localContentItem);
					notifyLocalListReady(getLocalContentItems());
					if (previousLocation != null) {
						checkRegion(previousLocation);
					}
				} catch (InterruptedIOException e) {
					notifyDownloadInterrupted(contentItem);
				} catch (IOException e) {
					notifyErrorDownload(contentItem, e);
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

	public void refresh() {
		synchronized (downloadTasks) {
			for (Future<?> task : downloadTasks) {
				task.cancel(true);
			}
			downloadTasks.clear();
		}
	}

	public void setLocation(final Location location) {
		if (previousLocation == null || location.distanceTo(previousLocation) > 10000) {
			previousLocation = location;
			executor.execute(new Runnable() {
				@Override
				public void run() {
					checkRegion(location);
				}
			});
		}
	}

	public String requestContentItem(ContentItem contentItem) {
		return contentManager.activateContentItem(contentItem);
	}

	private void checkRegion(Location location) {
		Set<String> contentTypes = new HashSet<String>(2);
		contentTypes.add(ContentManagerImpl.GRAPHHOPPER_MAP);
		contentTypes.add(ContentManagerImpl.MAPSFORGE_MAP);
		List<ContentItem> regionContentItems = contentManager.findContentItemsByRegion(location);
		for (ContentItem contentItem : regionContentItems) {
			if (contentTypes.remove(contentItem.getType())) {
				contentManager.unpackContentItem(contentItem);
				notifyRecommendedRegionItemReady(contentItem);
			}
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
				synchronized (listeners) {
					for (Listener listener : listeners) {
						listener.remoteListReady(items);
					}
				}
			}
		});
	}

	private void notifyDownloadStateUpdated(final ContentItem item, final int downloaded, final int max) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				synchronized (listeners) {

					for (Listener listener : listeners) {
						listener.downloadStateUpdated(item, downloaded, max);
					}
				}
			}
		});
	}

	private void notifyDownloadFinished(final ContentItem localItem, final ContentItem remoteItem) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				synchronized (listeners) {
					for (Listener listener : listeners) {
						listener.downloadFinished(localItem, remoteItem);
					}
				}
			}
		});
	}

	private void notifyDownloadInterrupted(final ContentItem remoteItem) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				synchronized (listeners) {
					for (Listener listener : listeners) {
						listener.downloadInterrupted(remoteItem);
					}
				}
			}
		});
	}

	private void notifyErrorDownload(final ContentItem remoteItem, final IOException ex) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				synchronized (listeners) {

					for (Listener listener : listeners) {
						listener.errorDownloading(remoteItem, ex);
					}
				}
			}
		});
	}

	private void notifyErrorInitializing(final IOException ex) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				synchronized (listeners) {
					for (Listener listener : listeners) {
						listener.errorInitializing(ex);
					}
				}
			}
		});
	}

	private void notifyRecommendedRegionItemReady(final ContentItem localItem) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				synchronized (listeners) {
					for (Listener listener : listeners) {
						listener.recommendedRegionItemReady(localItem);
					}
				}
			}
		});
	}

	private void notifyRequestContentReload() {
		handler.post(new Runnable() {
			@Override
			public void run() {
				synchronized (listeners) {
					for (Listener listener : listeners) {
						listener.requestContentReload();
					}
				}
			}
		});
	}


	public void test() {
		Location location = new Location("test");
		location.setLatitude(61.78);
		location.setLongitude(34.35);

		List<ContentItem> contentItemsByRegion = contentManager.findContentItemsByRegion(location);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(Settings.PREF_STORAGE_PATH)) {
			final String newPath = sharedPreferences.getString(key, null);
			if (contentManager != null) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						contentManager.migrate(newPath);
						notifyRequestContentReload();
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

	public static interface Listener {
		void localListReady(List<ContentItem> list);
		void remoteListReady(List<ContentItem> list);

		void downloadStateUpdated(ContentItem item, int downloaded, int max);
		void downloadFinished(ContentItem localItem, ContentItem remoteItem);

		void errorDownloading(ContentItem item, IOException e);
		void errorInitializing(IOException e);

		void downloadInterrupted(ContentItem item);

		void recommendedRegionItemReady(ContentItem contentItem);

		void requestContentReload();
	}
}
