package org.fruct.oss.mapcontent.content.fragments;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.fruct.oss.mapcontent.R;
import org.fruct.oss.mapcontent.content.ContentItem;
import org.fruct.oss.mapcontent.content.ContentManagerImpl;

import java.util.List;

class ContentAdapter extends BaseAdapter {
	private List<ContentListItem> items;
	private final Context context;
	private final int resource;

	public ContentAdapter(Context context, int resource, List<ContentListItem> objects) {
		this.resource = resource;
		this.items = objects;
		this.context = context;
	}

	public void setItems(List<ContentListItem> items) {
		this.items = items;
		notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		return items.size();
	}

	@Override
	public ContentListItem getItem(int position) {
		return items.get(position);
	}

	@Override
	public long getItemId(int position) {
		return getItem(position).name.hashCode();
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ContentListItem item = getItem(position);
		LayoutInflater inflater = ((Activity) context).getLayoutInflater();
		View view = null;
		Holder tag = null;

		if (convertView != null && convertView.getTag() != null) {
			tag = (Holder) convertView.getTag();
			if (tag instanceof Holder) {
				view = convertView;
			}
		}

		if (view == null) {
			view = inflater.inflate(resource, parent, false);
			assert view != null;

			tag = new Holder();
			tag.text1 = (TextView) view.findViewById(android.R.id.text1);
			tag.text2 = (TextView) view.findViewById(android.R.id.text2);
			tag.text3 = (TextView) view.findViewById(R.id.text3);
			view.setTag(tag);
			tag.parent = view;
		}

		tag.text1.setText(item.name);

		int idx = 0;
		TextView[] views = {tag.text2, tag.text3};
		for (TextView view2 : views)
			view2.setVisibility(View.GONE);

		for (ContentListSubItem subItem : item.contentSubItems) {
			subItem.tag = tag;
			ContentFragment.LocalContentState state = subItem.state;
			ContentItem sItem = subItem.contentItem;

			boolean active = false;
			boolean needUpdate = false;

			if (state == ContentFragment.LocalContentState.NOT_EXISTS) {
				active = true;
			} else if (state == ContentFragment.LocalContentState.NEEDS_UPDATE) {
				active = true;
				needUpdate = true;
			}

			if (idx >= views.length)
				break;

			if (idx == 0)
				tag.item1 = sItem;
			else
				tag.item2 = sItem;

			String text = "";
			if (sItem.getType().equals(ContentManagerImpl.GRAPHHOPPER_MAP)) {
				text = context.getString(R.string.navigation_data);
			} else if (sItem.getType().equals(ContentManagerImpl.MAPSFORGE_MAP)) {
				text = context.getString(R.string.offline_map);
			}

			if (needUpdate)
				text += " (" + context.getString(R.string.update_availabe) + ")";
			views[idx].setText(text);

			views[idx].setVisibility(View.VISIBLE);
			if (active)
				views[idx].setTypeface(null, Typeface.BOLD);
			else
				views[idx].setTypeface(null, Typeface.NORMAL);

			idx++;
		}

		return view;
	}

	class Holder {
		View parent;
		TextView text1;
		TextView text2;
		TextView text3;

		// Item corresponding second text line
		ContentItem item1;

		// Item corresponding third text line
		ContentItem item2;
	}
}
