package org.fruct.oss.mapcontent.content;

import java.io.IOException;
import java.util.List;


public class ContentListenerAdapter implements ContentService.Listener {
	@Override
	public void localListReady(List<ContentItem> list) {

	}

	@Override
	public void remoteListReady(List<ContentItem> list) {

	}

	@Override
	public void downloadStateUpdated(ContentItem item, int downloaded, int max) {

	}

	@Override
	public void downloadFinished(ContentItem localItem, ContentItem remoteItem) {

	}

	@Override
	public void errorDownloading(ContentItem item, IOException e) {

	}

	@Override
	public void errorInitializing(IOException e) {

	}

	@Override
	public void downloadInterrupted(ContentItem item) {

	}

	@Override
	public void recommendedRegionItemReady(ContentItem contentItem) {

	}

	@Override
	public void requestContentReload() {

	}

	@Override
	public void recommendedRegionItemNotFound(String contentType) {

	}

	@Override
	public void suggestedItemsReady(List<String> regionIds) {

	}
}
