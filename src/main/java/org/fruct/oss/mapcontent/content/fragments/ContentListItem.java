package org.fruct.oss.mapcontent.content.fragments;

import java.util.List;

class ContentListItem implements Comparable<ContentListItem> {
	List<ContentListSubItem> contentSubItems;
	String name;

	@Override
	public int compareTo(ContentListItem another) {
		return name.compareTo(another.name);
	}

}
