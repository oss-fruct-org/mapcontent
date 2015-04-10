package org.fruct.oss.mapcontent.content.fragments;

import java.util.List;

public class ContentListItem implements Comparable<ContentListItem> {
	List<ContentListSubItem> contentSubItems;
	String name;
	String regionId;

	@Override
	public int compareTo(ContentListItem another) {
		return name.compareTo(another.name);
	}

}
