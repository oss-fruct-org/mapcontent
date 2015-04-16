package org.fruct.oss.mapcontent.content.fragments;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SpinnerAdapter;
import android.widget.Toast;

import org.fruct.oss.mapcontent.R;
import org.fruct.oss.mapcontent.content.ContentItem;
import org.fruct.oss.mapcontent.content.ContentService;
import org.fruct.oss.mapcontent.content.connections.ContentServiceConnection;
import org.fruct.oss.mapcontent.content.connections.ContentServiceConnectionListener;
import org.fruct.oss.mapcontent.content.connections.GHContentServiceConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContentFragment extends Fragment
		implements AdapterView.OnItemClickListener,
		ContentDialog.Listener, ActionMode.Callback, DownloadProgressFragment.OnFragmentInteractionListener,
		ActionBar.OnNavigationListener,
		ContentServiceConnectionListener,
		ContentService.Listener, ContentService.ItemListener {
	private final static Logger log = LoggerFactory.getLogger(ContentFragment.class);

	public ContentFragment() {
		// Required empty public constructor
	}

	protected ContentServiceConnection remoteContentServiceConnection;
	private ContentService remoteContent;

	private ListView listView;
	private ContentAdapter adapter;

	// Last selected item
	private ContentListItem currentItem;
	private String currentItemName;

	private boolean isSuggestRequested;

	private LocalContentState filteredState;
	private List<ContentListItem> contentListItems;

	private List<ContentItem> localItems = Collections.emptyList();
	private List<ContentItem> remoteItems = Collections.emptyList();

	private SharedPreferences pref;

	private DownloadProgressFragment downloadFragment;

	private String[] rootUrls = {"http://oss.fruct.org/projects/roadsigns/root.xml"};

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
							 Bundle savedInstanceState) {
		// Inflate the layout for this fragment
		View view = inflater.inflate(R.layout.fragment_content, container, false);

		if (savedInstanceState != null)
			currentItemName = savedInstanceState.getString("current-item-idx");

		setupSpinner();

		adapter = new ContentAdapter(getActivity(), R.layout.file_list_item, Collections.<ContentListItem>emptyList());

		listView = (ListView) view.findViewById(R.id.list);
		listView.setOnItemClickListener(this);
		listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		listView.setAdapter(adapter);

		pref = PreferenceManager.getDefaultSharedPreferences(getActivity());

		return view;
	}


	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (remoteContentServiceConnection != null) {
			remoteContentServiceConnection = new ContentServiceConnection(this);
		}

		downloadFragment = (DownloadProgressFragment) getActivity().getSupportFragmentManager().findFragmentByTag("download-fragment");
		if (downloadFragment == null) {
			downloadFragment = new DownloadProgressFragment();
			getActivity().getSupportFragmentManager().beginTransaction()
					.add(R.id.download_fragment, downloadFragment, "download-fragment")
					.hide(downloadFragment)
					.addToBackStack(null)
					.commit();
		}

		ContentDialog contentDialog = (ContentDialog) getActivity().getSupportFragmentManager().findFragmentByTag("content-dialog");
		if (contentDialog != null) {
			contentDialog.setListener(this);
		}

		downloadFragment.setListener(this);
		setHasOptionsMenu(true);
	}

	@Override
	public void onResume() {
		super.onResume();
		remoteContentServiceConnection.bindService(getActivity());
	}

	@Override
	public void onPause() {
		if (remoteContent != null) {
			remoteContent.removeListener(this);
			remoteContent.removeItemListener(this);
			remoteContent = null;
		}

		remoteContentServiceConnection.unbindService(getActivity());

		super.onPause();
	}

	private void setupSpinner() {
		ActionBar actionBar = ((ActionBarActivity) getActivity()).getSupportActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		SpinnerAdapter spinnerAdapter = ArrayAdapter.createFromResource(getActivity(), R.array.content_spinner,
				R.layout.support_simple_spinner_dropdown_item);
		actionBar.setListNavigationCallbacks(spinnerAdapter, this);
		actionBar.setSelectedNavigationItem(getArguments() != null && getArguments().getBoolean("update") ? 2 : 0);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("current-item-idx", currentItemName);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		getActivity().getMenuInflater().inflate(R.menu.refresh, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_refresh) {
			if (remoteContent != null) {
				remoteContent.refresh(rootUrls, true);
			}
		}

		return super.onOptionsItemSelected(item);
	}

	private void setContentList(final List<ContentItem> localItems, final List<ContentItem> remoteItems) {
		new GenerateContentList(localItems, remoteItems, filteredState).execute();
	}

	private void showToast(final String string) {
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(getActivity(), string, Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	public void localListReady(List<ContentItem> list) {
		localItems = new ArrayList<>(list);
		setContentList(localItems, remoteItems);
	}

	@Override
	public void remoteListReady(List<ContentItem> list) {
		remoteItems = new ArrayList<>(list);
		setContentList(localItems, remoteItems);
	}

	@Override
	public void downloadStateUpdated(ContentItem item, int downloaded, int max) {
		downloadFragment.downloadStateUpdated(item, downloaded, max);
	}

	@Override
	public void downloadFinished(ContentItem localItem, ContentItem remoteItem) {
		showToast(getString(R.string.download_finished));
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				downloadFragment.stopDownload();
			}
		});
	}

	@Override
	public void errorDownloading(ContentItem item, IOException e) {
		showToast(getString(R.string.error_downloading));
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				downloadFragment.stopDownload();
			}
		});
	}

	@Override
	public void errorInitializing(Exception e) {
		showToast(getString(R.string.no_networks_offline));
	}

	@Override
	public void downloadInterrupted(ContentItem sItem) {
		showToast(getString(R.string.download_interrupted));
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				downloadFragment.stopDownload();
				adapter.notifyDataSetChanged();
			}
		});
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
	public void updateReady() {

	}

	@Override
	public void suggestedItemsReady(List<String> regionIds) {
		if (!isSuggestRequested) {
			return;
		}

		if (contentListItems == null) {
			return;
		}

		isSuggestRequested = false;

		for (String regionId : regionIds) {
			for (ContentListItem contentListItem : contentListItems) {
				if (contentListItem.regionId.equals(regionId)) {
					currentItem = contentListItem;

					final ContentDialog dialog = new ContentDialog();
					dialog.setListener(this);
					dialog.setStorageItems(contentListItem.contentSubItems);
					dialog.show(getFragmentManager(), "content-dialog");
					return;
				}
			}
		}
	}

	@Override
	public void downloadsSelected(List<ContentListSubItem> items) {
		if (currentItem == null)
			return;

		downloadFragment.startDownload();
		for (ContentListSubItem item : items)
			remoteContent.downloadItem(item.contentItem);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		currentItem = adapter.getItem(position);
		((ActionBarActivity) getActivity()).startSupportActionMode(this);
	}

	@Override
	public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
		actionMode.getMenuInflater().inflate(R.menu.online_content_action, menu);

		boolean hasDeletable = false;
		boolean hasDownloadable = false;

		for (ContentListSubItem contentSubItem : currentItem.contentSubItems) {
			if (contentSubItem.contentItem.isDownloadable() || contentSubItem.state == LocalContentState.NEEDS_UPDATE) {
				hasDownloadable = true;
			}

			if (!contentSubItem.contentItem.isReadonly()) {
				hasDeletable = true;
			}
		}

		if (!hasDeletable) {
			menu.findItem(R.id.action_delete).setVisible(false);
		}

		if (!hasDownloadable) {
			menu.findItem(R.id.action_download).setVisible(false);
		}

		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
		return false;
	}

	@Override
	public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
		if (menuItem.getItemId() == R.id.action_download) {
			final ContentDialog dialog = new ContentDialog();
			dialog.setListener(this);
			dialog.setStorageItems(currentItem.contentSubItems);
			dialog.show(getFragmentManager(), "content-dialog");
		} else if (menuItem.getItemId() == R.id.action_delete) {
			deleteContentItem(currentItem);
		} else if (menuItem.getItemId() == R.id.action_use) {
			useContentItem(currentItem);
		} else {
			return false;
		}

		actionMode.finish();

		return false;
	}

	protected void suggestItem() {
		isSuggestRequested = true;
	}

	private void deleteContentItem(ContentListItem currentItem) {
		boolean wasError = false;
		if (remoteContent != null && !currentItem.contentSubItems.isEmpty()) {
			for (ContentListSubItem subItem : currentItem.contentSubItems) {
				if (!remoteContent.deleteContentItem(subItem.contentItem))
					wasError = true;
			}
		}

		if (wasError) {
			Toast.makeText(getActivity(), getString(R.string.str_cant_delete_active_content), Toast.LENGTH_LONG).show();
		}
	}

	private void useContentItem(ContentListItem currentItem) {
		throw new IllegalStateException("Not implemented yet");
		//if (remoteContent != null && !currentItem.contentSubItems.isEmpty()) {
		//	remoteContent.activateRegionById(currentItem.contentSubItems.get(0).contentItem.getRegionId());
		//}
	}

	@Override
	public void stopButtonPressed() {
		if (remoteContent != null) {
			remoteContent.interrupt();
		}
	}

	@Override
	public void onDestroyActionMode(ActionMode actionMode) {
		listView.clearChoices();
		listView.setItemChecked(-1, true);
	}

	@Override
	public boolean onNavigationItemSelected(int i, long l) {
		switch (i) {
		case 0: // All
			new GenerateContentList(localItems, remoteItems, filteredState = null).execute();
			return true;

		case 1: // Local
			new GenerateContentList(localItems, remoteItems, filteredState = LocalContentState.UP_TO_DATE).execute();
			return true;

		case 2: // Updates
			new GenerateContentList(localItems, remoteItems, filteredState = LocalContentState.NEEDS_UPDATE).execute();
			return true;
		}

		return false;
	}

	@Override
	public void onContentServiceReady(ContentService contentService) {
		remoteContent = contentService;
		remoteContent.addListener(this);
		remoteContent.addItemListener(this);

		remoteContent.refresh(rootUrls, false);
		setContentList(localItems = new ArrayList<>(remoteContent.getLocalContentItems()),
				remoteItems = new ArrayList<>(remoteContent.getRemoteContentItems()));
	}

	@Override
	public void onContentServiceDisconnected() {
		remoteContent = null;
	}

	public void setRootUrls(String[] rootUrls) {
		this.rootUrls = rootUrls;
	}

	private class GenerateContentList extends AsyncTask<Void, Void, List<ContentListItem>> {
		private final LocalContentState filterState;
		private List<ContentItem> localItems;
		private List<ContentItem> remoteItems;

		private GenerateContentList(List<ContentItem> localItems, List<ContentItem> remoteItems, LocalContentState filterState) {
			this.localItems = localItems;
			this.remoteItems = remoteItems;
			this.filterState = filterState;
		}

		@Override
		protected List<ContentListItem> doInBackground(Void... params) {
			HashMap<String, ContentListSubItem> states = new HashMap<>(localItems.size());

			for (ContentItem item : localItems) {
				states.put(item.getName(), new ContentListSubItem(item, LocalContentState.DELETED_FROM_SERVER));
			}

			for (ContentItem remoteItem : remoteItems) {
				String name = remoteItem.getName();

				ContentListSubItem subItem = states.get(name);
				ContentItem localItem = subItem == null ? null : subItem.contentItem;

				LocalContentState newState;
				ContentItem saveItem = remoteItem;

				if (localItem == null) {
					newState = LocalContentState.NOT_EXISTS;
				} else if (!localItem.getHash().equals(remoteItem.getHash())) {
					newState = LocalContentState.NEEDS_UPDATE;
				} else {
					saveItem = localItem;
					newState = LocalContentState.UP_TO_DATE;
				}

				states.put(name, new ContentListSubItem(saveItem, newState));
			}

			HashMap<String, List<ContentListSubItem>> listViewMap = new HashMap<>();

			for (Map.Entry<String, ContentListSubItem> entry : states.entrySet()) {
				String rId = entry.getValue().contentItem.getRegionId();

				if (filterState != null
						&& filterState != entry.getValue().state
						&& ((filterState != LocalContentState.UP_TO_DATE
						|| entry.getValue().state != LocalContentState.NEEDS_UPDATE))) {
					continue;
				}

				List<ContentListSubItem> l = listViewMap.get(rId);

				if (l == null) {
					l = new ArrayList<>();
					listViewMap.put(rId, l);
				}

				l.add(entry.getValue());
			}

			List<ContentListItem> listViewItems = new ArrayList<>();
			for (Map.Entry<String, List<ContentListSubItem>> entry : listViewMap.entrySet()) {
				ContentListItem listViewItem = new ContentListItem();

				listViewItem.regionId = entry.getKey();
				listViewItem.name = entry.getValue().get(0).contentItem.getDescription();
				listViewItem.contentSubItems = entry.getValue();

				Collections.sort(listViewItem.contentSubItems);
				listViewItems.add(listViewItem);
			}

			Collections.sort(listViewItems);

			return listViewItems;
		}

		@Override
		protected void onPostExecute(List<ContentListItem> contentListItems) {
			if (contentListItems != null && adapter != null) {
				ContentFragment.this.contentListItems = contentListItems;
				adapter.setItems(contentListItems);

				if (isSuggestRequested) {
					remoteContent.requestSuggestedRegion();
				}
			}
		}
	}

	public enum LocalContentState {
		NOT_EXISTS, NEEDS_UPDATE, UP_TO_DATE, DELETED_FROM_SERVER
	}

}
