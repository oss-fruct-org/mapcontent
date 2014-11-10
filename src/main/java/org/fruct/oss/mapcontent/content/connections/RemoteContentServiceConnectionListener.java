package org.fruct.oss.mapcontent.content.connections;

import org.fruct.oss.mapcontent.content.DataService;
import org.fruct.oss.mapcontent.content.RemoteContentService;

public interface RemoteContentServiceConnectionListener {
	void onRemoteContentServiceConnected(RemoteContentService remoteContentService);
	void onRemoteContentServiceDisconnected();
}
