package org.fruct.oss.mapcontent.content.contenttype2;

import android.location.Location;

import org.fruct.oss.mapcontent.content.ContentItem;
import org.fruct.oss.mapcontent.content.ContentManagerImpl;
import org.fruct.oss.mapcontent.content.utils.Region;

import java.io.IOException;

public class MapsforgeContentType implements ContentType {
	@Override
	public void unpackContentItem(ContentItem contentItem, String contentItemPackageFile, String unpackedPath) throws IOException {

	}

	@Override
	public Region extractRegion(ContentItem item, String contentItemPackageFile) {
		return null;
	}

	@Override
	public boolean checkRegion(ContentItem item, String contentItemPackageFile, Location location) {
		return false;
	}

	@Override
	public String getName() {
		return ContentManagerImpl.MAPSFORGE_MAP;
	}

}
