package org.fruct.oss.mapcontent.content.contenttype;

import android.location.Location;

import org.fruct.oss.mapcontent.content.ContentItem;
import org.fruct.oss.mapcontent.content.utils.Region;

import java.io.IOException;

public interface ContentType {
	void unpackContentItem(ContentItem contentItem, String contentItemPackageFile,
						   String unpackedPath) throws IOException;

	Region extractRegion(ContentItem item, String contentItemPackageFile);
	boolean checkRegion(ContentItem item, String contentItemPackageFile, Location location);
	String getName();
}
