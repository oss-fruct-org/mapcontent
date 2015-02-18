package org.fruct.oss.mapcontent.content.connections;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.fruct.oss.mapcontent.content.ContentService;
import org.jetbrains.annotations.NotNull;

public class ContentServiceConnection implements ServiceConnection {
	private final ContentServiceConnectionListener listener;

	private ContentService contentService;

	public ContentServiceConnection(@NotNull ContentServiceConnectionListener listener) {
		this.listener = listener;
	}


	public void bindService(@NotNull Context context) {
		Intent intent = new Intent(context, ContentService.class);
		context.bindService(intent, this, Context.BIND_AUTO_CREATE);
	}

	public void unbindService(@NotNull Context context) {
		context.unbindService(this);
		if (contentService != null) {
			contentService.removeInitializationListener(this);
		}
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		contentService = ((ContentService.Binder) service).getService();
		if (contentService.isInitialized()) {
			listener.onContentServiceReady(contentService);
		} else {
			contentService.addInitializationListener(this);
		}
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		listener.onContentServiceDisconnected();
	}

	public void onContentServiceInitialized() {
		listener.onContentServiceReady(contentService);
	}
}
