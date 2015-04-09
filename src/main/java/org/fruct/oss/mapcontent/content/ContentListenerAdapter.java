package org.fruct.oss.mapcontent.content;

import java.util.List;


public class ContentListenerAdapter implements ContentService.ItemListener {
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
