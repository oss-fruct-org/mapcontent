package org.fruct.oss.mapcontent.content;

import java.io.IOException;
import java.util.List;

interface ContentStorage {
	void updateContentList() throws IOException;
	List<ContentItem> getContentList();
}
