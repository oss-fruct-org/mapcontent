package org.fruct.oss.mapcontent.content;

import org.fruct.oss.mapcontent.content.utils.RegionCache;
import org.fruct.oss.mapcontent.content.utils.UrlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NetworkStorage implements ContentStorage {
	private static final Logger log = LoggerFactory.getLogger(NetworkStorage.class);

	private final String[] rootUrls;
	private final RegionCache regionCache;

	private List<ContentItem> items = new ArrayList<>();

	public NetworkStorage(String[] rootUrls, RegionCache regionCache) {
		this.rootUrls = rootUrls;
		this.regionCache = regionCache;
	}

	@Override
	public void updateContentList() throws IOException {
		boolean found = false;
		items.clear();

		for (String contentUrl : rootUrls) {
			try {
				found = true;

				loadContentList(new String[]{contentUrl}, new HashSet<String>());
				log.info("Content root url {} successfully downloaded", contentUrl);

				break;
			} catch (IOException ex) {
				log.warn("Content root url {} unavailable", contentUrl);
			}
		}

		if (!found) {
			throw new IOException("No one of remote content roots are available");
		}
	}

	private void loadContentList(String[] contentUrls, Set<String> visited) throws IOException {
		int countSuccessful = 0;
		for (String url : contentUrls) {
			if (visited.contains(url)) {
				countSuccessful++;
				continue;
			}

			visited.add(url);
			InputStream conn = null;

			try {
				conn = UrlUtil.getInputStream(url);

				NetworkContent content = NetworkContent.parse(new InputStreamReader(conn));

				for (NetworkContentItem item : content.getItems()) {
					if (item.getType().equals(ContentManagerImpl.GRAPHHOPPER_MAP)) {
						item.setNetworkStorage(this);
						items.add(item);
					}

					if (item.getType().equals(ContentManagerImpl.MAPSFORGE_MAP)) {
						item.setNetworkStorage(this);
						items.add(item);
					}
				}

				countSuccessful++;

				loadContentList(content.getIncludes(), visited);
				regionCache.updateDiskCache(content.getCacheUrls());
			} catch (IOException e) {
				log.warn("Content link " + url + " broken: ", e);
			} finally {
				if (conn != null)
					conn.close();
			}
		}

		if (countSuccessful == 0 && contentUrls.length > 0)
			throw new IOException("No one of remote content roots are available");
	}

	@Override
	public List<ContentItem> getContentList() {
		return items;
	}
}
