package org.fruct.oss.mapcontent.content;

import android.location.Location;

import java.util.List;

public class ContentManagerImpl implements ContentManager {
	private String contentRootPath;

	public ContentManagerImpl(String contentRootPath) {
		this.contentRootPath = contentRootPath;
	}

	@Override
	public List<ContentItem> getLocalContentItems() {
		return null;
	}

	@Override
	public List<ContentItem> getRemoteContentItems() {
		return null;
	}

	@Override
	public List<ContentItem> findContentItemsByRegion(Location location) {
		return null;
	}

	@Override
	public void unpackContentItem(ContentItem contentItem) {

	}

	@Override
	public String activateContentItem(ContentItem contentItem) {
		return null;
	}

	@Override
	public ContentItem downloadContentItem(NetworkContentItem networkContentItem) {
		return null;
	}

	@Override
	public void garbageCollect() {

	}
}
