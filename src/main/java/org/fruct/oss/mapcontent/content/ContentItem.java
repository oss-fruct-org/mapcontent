package org.fruct.oss.mapcontent.content;

import java.io.IOException;
import java.io.InputStream;

public interface ContentItem {
	String getType();
	String getName();
	String getDescription();
	String getStorage();
	String getHash();
	String getRegionId();

	boolean isDownloadable();
	boolean isReadonly();
}
