/*
 * Copyright (c) 2009  Ryan Chute
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

package gov.lanl.util;

import java.io.File;

import org.apache.log4j.Logger;

public class ConcurrentEvictionFileDelete<K, V> implements ConcurrentLinkedHashMap.EvictionListener<String, String> {
	static Logger logger = Logger.getLogger(ConcurrentEvictionFileDelete.class);

	public void onEviction(java.lang.String key, java.lang.String value) {
		File f = new File(value);
		f.delete();
		logger.debug("deleted " + value);
	}
}
