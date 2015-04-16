package org.fruct.oss.mapcontent.content.connections;

import org.fruct.oss.mapcontent.content.ContentManagerImpl;
import org.fruct.oss.mapcontent.content.ContentService;
import org.jetbrains.annotations.NotNull;

public class GHContentServiceConnection extends ContentServiceConnection {
	public GHContentServiceConnection(ContentServiceConnectionListener listener) {
		super(listener);
	}

	@Override
	protected void doInitialization(ContentService contentService) {
		contentService.initialize(new String[]{ContentManagerImpl.GRAPHHOPPER_MAP},
				ContentService.DEFAULT_ROOT_URLS,
				false);
	}
}
