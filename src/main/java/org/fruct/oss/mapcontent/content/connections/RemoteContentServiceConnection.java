package org.fruct.oss.mapcontent.content.connections;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.fruct.oss.mapcontent.content.DataService;
import org.fruct.oss.mapcontent.content.RemoteContentService;
import org.jetbrains.annotations.NotNull;

public class RemoteContentServiceConnection implements ServiceConnection {
	private final RemoteContentServiceConnectionListener listener;

	public RemoteContentServiceConnection(@NotNull RemoteContentServiceConnectionListener listener) {
		this.listener = listener;
	}
	
	public void bindService(@NotNull Context context) {
		Intent intent = new Intent(context, RemoteContentService.class);
		context.bindService(intent, this, Context.BIND_AUTO_CREATE);
	}

	public void unbindService(@NotNull Context context) {
		context.unbindService(this);
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		RemoteContentService remoteContentService = ((RemoteContentService.Binder) service).getService();
		listener.onRemoteContentServiceConnected(remoteContentService);
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		listener.onRemoteContentServiceDisconnected();
	}
}
