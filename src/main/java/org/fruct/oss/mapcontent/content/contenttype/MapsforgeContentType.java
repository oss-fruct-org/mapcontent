package org.fruct.oss.mapcontent.content.contenttype;

import android.location.Location;

import org.fruct.oss.mapcontent.content.ContentItem;
import org.fruct.oss.mapcontent.content.ContentManagerImpl;
import org.fruct.oss.mapcontent.content.DirectoryContentItem;
import org.fruct.oss.mapcontent.content.utils.Region;
import org.mapsforge.core.model.BoundingBox;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Tile;
import org.mapsforge.core.util.MercatorProjection;
import org.mapsforge.map.reader.MapDatabase;
import org.mapsforge.map.reader.MapReadResult;
import org.mapsforge.map.reader.header.FileOpenResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class MapsforgeContentType implements ContentType {
	private static final Logger log = LoggerFactory.getLogger(MapsforgeContentType.class);

	private final Map<String, Region> regionsCache;

	public MapsforgeContentType(Map<String, Region> regions) {
		this.regionsCache = regions;
	}

	@Override
	public void unpackContentItem(ContentItem contentItem, String contentItemPackageFile, String unpackedPath) throws IOException {
		// Nothing to unpack
	}

	@Override
	public Region extractRegion(ContentItem item, String contentItemPackageFile) {
		return null;
	}

	@Override
	public boolean checkRegion(ContentItem contentItem, String contentItemPackageFile, Location location) {
		boolean ret = false;

		Region region = regionsCache.get(contentItem.getRegionId());
		if (region != null) {
			return region.testHit(location.getLatitude(), location.getLongitude());
		}

		DirectoryContentItem dItem = (DirectoryContentItem) contentItem;
		MapDatabase mapDatabase = new MapDatabase();
		FileOpenResult result = mapDatabase.openFile(new File(dItem.getPath()));
		if (!result.isSuccess()) {
			log.error("Can't read map database {}", result.getErrorMessage());
			return false;
		}

		BoundingBox bbox = mapDatabase.getMapFileInfo().boundingBox;
		if (bbox.contains(new LatLong(location.getLatitude(), location.getLongitude()))) {
			// TODO: add precise detection
			final int zoom = 16;

			Tile tile = new Tile(MercatorProjection.longitudeToTileX(location.getLongitude(), zoom),
					MercatorProjection.latitudeToTileY(location.getLatitude(), zoom),
					(byte) zoom,
					mapDatabase.getMapFileInfo().tilePixelSize);

			MapReadResult mapReadResult = mapDatabase.readMapData(tile);
			if (mapReadResult != null)
				ret = true;
		}

		mapDatabase.closeFile();

		return ret;

	}

	@Override
	public String getName() {
		return ContentManagerImpl.MAPSFORGE_MAP;
	}

}
