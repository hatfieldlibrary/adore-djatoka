/*
 * Copyright (c) 2007  Los Alamos National Security, LLC.
 *
 * Los Alamos National Laboratory
 * Research Library
 * Digital Library Research & Prototyping Team
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 * 
 */

package gov.lanl.adore.djatoka.openurl;

import java.io.File;
import java.util.*;

import org.apache.log4j.Logger;

/**
 * Implements an Least Recently Used (LRU) cache Manager.
 * 
 * @param max_cache
 *            the maximum cache size that will be kept in the cache.
 */
public class TileCacheManager {
	static Logger logger = Logger.getLogger(TileCacheManager.class);
	private static LinkedHashMap<String, String> cacheMap; // For fast search/remove

	private static boolean init = false;

	private static int max_cache;

	private static final float loadFactor = 0.75F;

	private static final boolean accessOrder = true; 

    public synchronized static void init(int size) {
		max_cache = size;
		cacheMap = new LinkedHashMap<String, String>(max_cache, loadFactor, accessOrder) {
			private static final long serialVersionUID = 1;
			protected synchronized boolean removeEldestEntry(Map.Entry<String, String> eldest) {
				boolean d = size() > TileCacheManager.max_cache;
				if (d) {
					File f = new File(eldest.getValue());
					logger.debug("deletingTile: " + eldest.getValue());
					if (f.exists())
						f.delete();
					remove(eldest.getKey());
				}
				return false;
			};
		};
		init = true;
    }
	
	public synchronized static String put(String key, String val) {
		return cacheMap.put(key, val);
	}

	public synchronized static String remove(String key) {
		File f = new File(get(key));
		if (f.exists())
			f.delete();
		return cacheMap.remove(key);
	}

	public static String get(String key) {
		return cacheMap.get(key);
	}

	public static boolean containsKey(String key) {
		return cacheMap.containsKey(key);
	}

	public synchronized static int size() {
		return cacheMap.size();
	}

	public synchronized static void clear() {
		cacheMap.clear();
	}
	
	public synchronized static boolean isInit() {
		return init;
	}
}