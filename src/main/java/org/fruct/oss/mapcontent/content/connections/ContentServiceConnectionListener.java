package org.fruct.oss.mapcontent.content.connections;

import org.fruct.oss.mapcontent.content.ContentService;

public interface ContentServiceConnectionListener {
	void onContentServiceReady(ContentService contentService);
	void onContentServiceDisconnected();
}
