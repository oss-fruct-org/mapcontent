package org.fruct.oss.mapcontent.content.fragments;

import org.fruct.oss.mapcontent.content.ContentItem;

public class ContentListSubItem implements Comparable<ContentListSubItem> {
	ContentListSubItem(ContentItem contentItem, ContentFragment.LocalContentState state) {
		this.contentItem = contentItem;
		this.state = state;
	}

	ContentItem contentItem;
	ContentFragment.LocalContentState state;
	Object tag;

	@Override
	public int compareTo(ContentListSubItem another) {
		return contentItem.getType().compareTo(another.contentItem.getType());
	}
}
