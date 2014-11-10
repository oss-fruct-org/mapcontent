package org.fruct.oss.mapcontent.content.connections;

import org.fruct.oss.mapcontent.content.DataService;

public interface DataServiceConnectionListener {
	void onDataServiceConnected(DataService dataService);
	void onDataServiceDisconnected();
}
