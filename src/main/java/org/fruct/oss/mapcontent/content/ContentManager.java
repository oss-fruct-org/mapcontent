package org.fruct.oss.mapcontent.content;

import android.location.Location;

import java.io.IOException;
import java.util.List;

public interface ContentManager {
	/**
	 * Refresh remote content list with given root urls
	 * @param rootUrls
	 */
	void refreshRemoteContentList(String[] rootUrls) throws IOException;


	/**
	 * Returns all local content items that ready for use
	 * @return local content items
	 */
	List<ContentItem> getLocalContentItems();

	/**
	 * Returns all remote content items that ready for download
	 * @return remote content items
	 */
	List<ContentItem> getRemoteContentItems();

	/**
	 * Return all content items in location
	 * @param location location to search content items
	 * @return content items list
	 */
	List<ContentItem> findContentItemsByRegion(Location location);

	/**
	 * Prepare content item to use
	 * @param contentItem content item
	 */
	void unpackContentItem(ContentItem contentItem);

	/**
	 * Mark content item as used. Only one item of each content type can be active at a time
	 *
	 * @param contentItem content item
	 * @return path to content item or null if content item is not unpacked
	 */
	String activateContentItem(ContentItem contentItem);

	/**
	 * Download remote content item
	 *
	 * @param networkContentItem remote content item
	 * @return local content item
	 */
	ContentItem downloadContentItem(NetworkContentItem networkContentItem) throws IOException;

	/**
	 * Delete all previously unpacked content items that not active ({@link #activateContentItem}).
	 */
	void garbageCollect();

	/**
	 * Migrate all content from content root to new root
	 * @param newRootPath new root
	 */
	void migrate(String newRootPath);

	/**
	 * Delete local content item
	 *
	 * @param contentItem content item
	 */
	boolean deleteContentItem(ContentItem contentItem);

	/**
	 * Find remote items for region
	 */
	List<ContentItem> findSuggestedItems(Location location);

	interface Listener {
		void downloadStateUpdated(ContentItem item, int downloaded, int max);
	}
}
