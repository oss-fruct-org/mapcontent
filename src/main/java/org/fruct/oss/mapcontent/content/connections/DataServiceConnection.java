package org.fruct.oss.mapcontent.content.connections;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.fruct.oss.mapcontent.content.DataService;
import org.jetbrains.annotations.NotNull;

public class DataServiceConnection implements ServiceConnection {
	private final DataServiceConnectionListener listener;

	public DataServiceConnection(@NotNull  DataServiceConnectionListener listener) {
		this.listener = listener;
	}
	
	public void bindService(@NotNull Context context) {
		Intent intent = new Intent(context, DataService.class);
		context.bindService(intent, this, Context.BIND_AUTO_CREATE);
	}

	public void unbindService(@NotNull Context context) {
		context.unbindService(this);
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service) {
		DataService dataService = ((DataService.Binder) service).getService();
		listener.onDataServiceConnected(dataService);
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		listener.onDataServiceDisconnected();
	}
}
