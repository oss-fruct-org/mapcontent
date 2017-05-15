package org.fruct.oss.mapcontent.content.helper;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.widget.Toast;

import org.fruct.oss.mapcontent.R;
import org.fruct.oss.mapcontent.content.ContentItem;
import org.fruct.oss.mapcontent.content.ContentManagerImpl;
import org.fruct.oss.mapcontent.content.ContentService;
import org.fruct.oss.mapcontent.content.connections.ContentServiceConnection;
import org.fruct.oss.mapcontent.content.connections.ContentServiceConnectionListener;
import org.fruct.oss.mapcontent.content.utils.Utils;

import java.util.List;

public class ContentHelper {
	private static final String PREF_WARN_PROVIDERS = "warn_providers_disabled";
	private static final String PREF_WARN_NETWORK = "warn_network_disabled";
	private static final String PREF_WARN_CONTENT = "warn_navigation_data_disabled";
	private static final String PREF_WARN_UPDATES = "warn_update_ready";

	private final FragmentActivity activity;

	private final ContentServiceConnectionListener listener;
	private final ContentServiceConnection contentServiceConnection;
	private final ContentService.ItemListener itemListener;

	private boolean isLocationNotificationsEnabled;
	private boolean isContentNotificationsEnabled;
	private boolean isNetworkNotificationsEnabled;
	private boolean isUpdateNotificationsEnabled;

	private boolean isChecked;

	private PendingIntent onConfigureContentIntent;
	private PendingIntent onConfigureUpdateIntent;

	private ContentService contentService;

	public ContentHelper(FragmentActivity activity, ContentServiceConnection connection) {
		this.activity = activity;
		this.listener = new ConnectionListener();
		this.itemListener = new ItemListener();
		this.contentServiceConnection = connection;
		this.contentServiceConnection.setListener(listener);
	}

	public void setRootURLs(String[] urls) {
        if (this.contentServiceConnection != null) {
            this.contentServiceConnection.setRootURLs(urls);
        }
    }

	public void onStart(boolean skipChecks) {
		contentServiceConnection.bindService(activity);

		if (!isChecked && !skipChecks) {
			if (isLocationNotificationsEnabled) {
				checkLocationProviderEnabled();
			}

			if (isNetworkNotificationsEnabled) {
				checkNetworkEnabled();
			}

			isChecked = true;
		}
	}

	public void onStop() {
		if (contentService != null) {
			contentService.removeItemListener(itemListener);
		}
		contentServiceConnection.unbindService(activity);
	}


	public void enableContentNotifications(PendingIntent onConfigureContentIntent) {
		this.onConfigureContentIntent = onConfigureContentIntent;
		isContentNotificationsEnabled = true;
	}


	public void enableUpdateNotifications(PendingIntent onConfigureUpdateIntent) {
		this.onConfigureUpdateIntent = onConfigureUpdateIntent;
		isUpdateNotificationsEnabled = true;
	}


	public void enableNetworkNotifications() {
		isNetworkNotificationsEnabled = true;
	}

	public void enableLocationProviderNotifications() {
		isLocationNotificationsEnabled = true;
	}

	private void checkLocationProviderEnabled() {
		LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
		boolean isProvidersEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
				|| locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

		if (!isProvidersEnabled) {
			if (checkWarnEnabled(PREF_WARN_PROVIDERS)) {
				WarnDialog warnDialog = WarnDialog.newInstance(R.string.alert_location_not_available,
						R.string.alert_location_not_available_title,
						PREF_WARN_PROVIDERS,
						PendingIntent.getActivity(activity, 0,
								new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
								PendingIntent.FLAG_ONE_SHOT));
				warnDialog.show(activity.getSupportFragmentManager(), "warn-dialog-location");
			} else {
				Toast.makeText(activity, R.string.alert_location_not_available, Toast.LENGTH_SHORT).show();
			}
		}
	}

	private void checkNetworkEnabled() {
		boolean networkActive = Utils.checkNetworkAvailability(activity);

		if (!networkActive) {
			if (checkWarnEnabled(PREF_WARN_NETWORK)) {
				WarnDialog warnDialog = WarnDialog.newInstance(R.string.alert_network_not_available,
						R.string.alert_network_not_available_title,
						PREF_WARN_NETWORK,
						PendingIntent.getActivity(activity, 1,
								new Intent(Settings.ACTION_WIRELESS_SETTINGS),
								PendingIntent.FLAG_ONE_SHOT));
				warnDialog.show(activity.getSupportFragmentManager(), "warn-dialog-network");
			} else {
				Toast.makeText(activity, R.string.alert_network_not_available, Toast.LENGTH_SHORT).show();
			}
		}
	}

	private void showContentDialog() {
		if (checkWarnEnabled(PREF_WARN_CONTENT)) {
			WarnDialog warnDialog = WarnDialog.newInstance(R.string.alert_content_not_available,
					R.string.alert_content_not_available_title,
					PREF_WARN_CONTENT,
					onConfigureContentIntent);
			warnDialog.show(activity.getSupportFragmentManager(), "warn-dialog-content");
		} else {
			Toast.makeText(activity, R.string.alert_content_not_available, Toast.LENGTH_SHORT).show();
		}
	}

	private void showUpdateDialog() {
		if (checkWarnEnabled(PREF_WARN_UPDATES)) {
			WarnDialog warnDialog = WarnDialog.newInstance(R.string.alert_update_ready,
					R.string.alert_update_ready_title,
					PREF_WARN_UPDATES,
					onConfigureUpdateIntent);
			warnDialog.show(activity.getSupportFragmentManager(), "warn-dialog-update");
		} else {
			Toast.makeText(activity, R.string.alert_update_ready, Toast.LENGTH_SHORT).show();
		}
	}


	private boolean checkWarnEnabled(String key) {
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);
		return !pref.getBoolean(key, false);
	}

	private class ConnectionListener implements ContentServiceConnectionListener {
		@Override
		public void onContentServiceReady(ContentService contentService) {
			ContentHelper.this.contentService = contentService;
			contentService.addItemListener(itemListener);
			contentService.refresh(false);
			contentService.requestRecommendedItem();
		}

		@Override
		public void onContentServiceDisconnected() {

		}
	}

	private class ItemListener implements ContentService.ItemListener {
		@Override
		public void recommendedRegionItemReady(ContentItem contentItem) {
		}

		@Override
		public void recommendedRegionItemNotFound(String contentType) {
			if (contentType.equals(ContentManagerImpl.GRAPHHOPPER_MAP) && isContentNotificationsEnabled) {
				isContentNotificationsEnabled = false;
				showContentDialog();
			}
		}

		@Override
		public void updateReady() {
			if (isUpdateNotificationsEnabled) {
				isUpdateNotificationsEnabled = false;
				showUpdateDialog();
			}
		}

		@Override
		public void suggestedItemsReady(List<String> regionIds) {

		}

		@Override
		public void requestContentReload() {

		}
	}
}
