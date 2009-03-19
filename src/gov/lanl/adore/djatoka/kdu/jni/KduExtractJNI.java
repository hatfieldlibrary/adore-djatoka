/*
 * Copyright (c) 2008  Los Alamos National Security, LLC.
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

package gov.lanl.adore.djatoka.kdu.jni;

import gov.lanl.adore.djatoka.DjatokaDecodeParam;
import gov.lanl.adore.djatoka.DjatokaException;
import gov.lanl.adore.djatoka.IExtract;
import gov.lanl.adore.djatoka.util.ImageRecord;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

import kdu_jni.Jp2_family_src;
import kdu_jni.Jp2_input_box;
import kdu_jni.Jp2_locator;
import kdu_jni.Jp2_source;
import kdu_jni.KduException;
import kdu_jni.Kdu_channel_mapping;
import kdu_jni.Kdu_codestream;
import kdu_jni.Kdu_compressed_source;
import kdu_jni.Kdu_coords;
import kdu_jni.Kdu_dims;

/**
 * Uses Kakadu Java Native Interface to extract JP2 regions.  
 * This is modified port of the kdu_expand app.
 * @author Ryan Chute
 *
 */
public class KduExtractJNI implements IExtract {

	/**
	 * Returns JPEG 2000 props in ImageRecord
	 * @param r ImageRecord containing absolute file path of JPEG 2000 image file.
	 * @return a populated ImageRecord object
	 * @throws DjatokaException
	 */
	public final ImageRecord getMetadata(ImageRecord r) throws DjatokaException {
		if (!new File(r.getImageFile()).exists())
			throw new DjatokaException("Image Does Not Exist");
		
		Jp2_source inputSource = new Jp2_source();
		Kdu_compressed_source kduIn = null;
		Jp2_family_src jp2_family_in = new Jp2_family_src();
		Jp2_locator loc = new Jp2_locator();

		try {
			jp2_family_in.Open(r.getImageFile(), true);
			inputSource.Open(jp2_family_in, loc);
			inputSource.Read_header();
			kduIn = inputSource;
			Kdu_codestream codestream = new Kdu_codestream();
			codestream.Create(kduIn);
			Kdu_channel_mapping channels = new Kdu_channel_mapping();
			if (inputSource.Exists())
				channels.Configure(inputSource, false);
			else
				channels.Configure(codestream);
			int ref_component = channels.Get_source_component(0);
			int minLevels = codestream.Get_min_dwt_levels();
			int minLayers= codestream.Get_max_tile_layers();
			Kdu_dims image_dims = new Kdu_dims();
			codestream.Get_dims(ref_component, image_dims);
			Kdu_coords imageSize = image_dims.Access_size();
			
			r.setWidth(imageSize.Get_x());
			r.setHeight(imageSize.Get_y());
			r.setLevels(minLevels);

			channels.Native_destroy();
			if (codestream.Exists())
				codestream.Destroy();
			kduIn.Native_destroy();
			inputSource.Native_destroy();
			jp2_family_in.Native_destroy();
		} catch (KduException e) {
			throw new DjatokaException(e);
		}

		return r;
	}

	/**
	 * Returns JPEG 2000 XML Box data in String[]
	 * @param input absolute file path of JPEG 2000 image file.
	 * @return an array of XML box values
	 * @throws DjatokaException
	 */
	public final String[] getXMLBox(String input) throws DjatokaException {
		if (!new File(input).exists())
			throw new DjatokaException("Image Does Not Exist");

		ArrayList<String> xmlList = null;
		Jp2_family_src jp2_family_in = new Jp2_family_src();
		Jp2_locator loc = new Jp2_locator();
		Jp2_input_box box = new Jp2_input_box();
		String[] values = null;
		try {
			jp2_family_in.Open(input, true);
			box.Open(jp2_family_in, loc);
			while (box != null && box.Get_box_bytes() > 0) {
				int x = (int) box.Get_box_type();
				String t = getType(x);
				if (t.startsWith("xml")) {
					if (xmlList == null)
						xmlList = new ArrayList<String>();
					String tmp = new String(getEntry(box));
					xmlList.add(tmp);
				    box.Close();
				} else {
					box.Close();
					box.Open_next();
				}
			}
		} catch (KduException e) {
			throw new DjatokaException(e);
		} finally {
			jp2_family_in.Native_destroy();
		}

		if (xmlList != null)
			values = new String[xmlList.size()];
			xmlList.toArray(values);
		
		return values;
	}

	private static byte[] getEntry(final Jp2_input_box box) throws KduException {
	    final int n = (int) box.Get_box_bytes();
	    final byte[] b = new byte[n];
	    int r = box.Read(b, n);
	    final byte[] out = new byte[r];
	    System.arraycopy(b, 0, out, 0, r);
	    return out;
	}
	
	private static String getType(int type) {
        byte[] buf = new byte[4];
        for (int i = 3; i >= 0; i--) {
            buf[i] = (byte) (type & 0xFF);
            type >>>= 8;
        }
        return new String(buf);
    }
	
	/**
	 * Extracts region defined in DjatokaDecodeParam as BufferedImage
	 * @param input absolute file path of JPEG 2000 image file.
	 * @param params DjatokaDecodeParam instance containing region and transform settings.
	 * @return extracted region as a BufferedImage
	 * @throws DjatokaException
	 */
	public BufferedImage process(String input, DjatokaDecodeParam params)
			throws DjatokaException {
		KduExtractProcessorJNI decoder = new KduExtractProcessorJNI(input, params);
		return decoder.extract();
	}

	/**
	 * Extracts region defined in DjatokaDecodeParam as BufferedImage
	 * @param input InputStream containing a JPEG 2000 image bitstream.
	 * @param params DjatokaDecodeParam instance containing region and transform settings.
	 * @return extracted region as a BufferedImage
	 * @throws DjatokaException
	 */
	public BufferedImage process(InputStream input, DjatokaDecodeParam params)
			throws DjatokaException {
		KduExtractProcessorJNI decoder = new KduExtractProcessorJNI(input, params);
		return decoder.extract();
	}
	
	/**
	 * Extracts region defined in DjatokaDecodeParam as BufferedImage
	 * @param input ImageRecord wrapper containing file reference, inputstream, etc.
	 * @param params DjatokaDecodeParam instance containing region and transform settings.
	 * @return extracted region as a BufferedImage
	 * @throws DjatokaException
	 */
	public BufferedImage process(ImageRecord input, DjatokaDecodeParam params)
			throws DjatokaException {
		if (input.getImageFile() != null)
			return process(input, params);
		else if (input.getObject() != null
				&& (input.getObject() instanceof InputStream))
			return process((InputStream) input.getObject(), params);
		else
			throw new DjatokaException(
					"File not defined and Input Object Type "
							+ input.getObject().getClass().getName()
							+ " is not supported");
	}
}
