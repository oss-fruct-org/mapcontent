package org.fruct.oss.mapcontent.content;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static org.fruct.oss.mapcontent.content.utils.XmlUtil.readText;
import static org.fruct.oss.mapcontent.content.utils.XmlUtil.skip;


public class NetworkContent {
	private String[] includes;
	private NetworkContentItem[] items;
	private String[] cacheUrls;

	public NetworkContent(List<String> includeList,
						  List<NetworkContentItem> networkContentItemList,
						  List<String> cacheUrls) {
		this.includes = includeList.toArray(new String[includeList.size()]);
		this.items = networkContentItemList.toArray(new NetworkContentItem[networkContentItemList.size()]);
		this.cacheUrls = cacheUrls.toArray(new String[cacheUrls.size()]);
	}

	public String[] getIncludes() {
		return includes;
	}

	public NetworkContentItem[] getItems() {
		return items;
	}

	public String[] getCacheUrls() {
		return cacheUrls;
	}

	public static NetworkContent parse(InputStreamReader reader) {
		try {
			XmlPullParser parser = Xml.newPullParser();
			parser.setInput(reader);
			parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);

			parser.nextTag();
			return readContent(parser);
		} catch (XmlPullParserException ex) {
			throw new RuntimeException("Can't parser content data", ex);
		} catch (IOException ex) {
			throw new RuntimeException("Can't parser content data", ex);
		}
	}

	private static NetworkContent readContent(XmlPullParser parser) throws IOException, XmlPullParserException {
		ArrayList<NetworkContentItem> items = new ArrayList<>();
		ArrayList<String> includes = new ArrayList<>();
		ArrayList<String> cacheUrls = new ArrayList<>();

		parser.require(XmlPullParser.START_TAG, null, "content");
		while (parser.next() != XmlPullParser.END_TAG) {
			if (parser.getEventType() != XmlPullParser.START_TAG)
				continue;

			String name = parser.getName();
			switch (name) {
			case "file":
				items.add(NetworkContentItem.readFile(parser));
				break;
			case "include":
				includes.add(readText(parser));
				break;
			case "region-cache":
				cacheUrls.add(readText(parser));
				break;
			default:
				skip(parser);
				break;
			}
		}
		parser.require(XmlPullParser.END_TAG, null, "content");

		return new NetworkContent(includes, items, cacheUrls);
	}
}
