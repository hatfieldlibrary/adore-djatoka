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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import org.apache.log4j.Logger;

public class ConcurrentEvictionFileDelete<K, V> implements ConcurrentLinkedHashMap.EvictionListener {
	static Logger logger = Logger.getLogger(ConcurrentEvictionFileDelete.class);
	static ExecutorService executor = Executors.newFixedThreadPool(10);
	public void onEviction(Object key, Object value) {
		File f = new File((String) value);
		FutureTask future = new FutureTask(new DeleteFileThread(f), null);
		executor.execute(future);
	}

    class DeleteFileThread implements Runnable {
        File file;

        public DeleteFileThread(File file) {
            this.file = file;
        }
        
        public void run() {
    		logger.debug("deleteFile " + file.getAbsolutePath());
    		try {
    			file.delete();
    		} catch (Exception e) {}
        }
    }
}
