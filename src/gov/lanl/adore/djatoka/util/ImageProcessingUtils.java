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

package gov.lanl.adore.djatoka.util;

import gov.lanl.adore.djatoka.io.FormatConstants;
import ij.io.Opener;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * Image Processing Utilities
 * @author Ryan Chute
 *
 */
public class ImageProcessingUtils {
	
    /**
     * Perform a rotation of the provided BufferedImage using degrees of
     * 90, 180, or 270.
     * @param bi BufferedImage to be rotated
     * @param degree 
     * @return rotated BufferedImage instance
     */
	public static BufferedImage rotate(BufferedImage bi, int degree) {
		int width = bi.getWidth();
		int height = bi.getHeight();

		BufferedImage biFlip;
		if (degree == 90 || degree == 270)
		    biFlip = new BufferedImage(height, width, bi.getType());
		else if (degree == 180)
			biFlip = new BufferedImage(width, height, bi.getType());
		else 
			return bi;

		if (degree == 90) {
			for (int i = 0; i < width; i++)
				for (int j = 0; j < height; j++)
					biFlip.setRGB(height- j - 1, i, bi.getRGB(i, j));
		}
		
		if (degree == 180) {
			for (int i = 0; i < width; i++)
				for (int j = 0; j < height; j++)
					biFlip.setRGB(width - i - 1, height - j - 1, bi.getRGB(i, j));
		}

		if (degree == 270) {
			for (int i = 0; i < width; i++)
				for (int j = 0; j < height; j++)
					biFlip.setRGB(j, width - i - 1, bi.getRGB(i, j));
		}
		
		bi.flush();
		bi = null;
		
		return biFlip;
	}
	
	/**
	 * Return the number of resolution levels the djatoka API will generate
	 * based on the provided pixel dimensions.
	 * @param w max pixel width
	 * @param h max pixel height
	 * @return number of resolution levels
	 */
	public static int getLevelCount(int w, int h) {
		int l = Math.max(w, h);
		int m = 96;
		int r = 0;
		int i;
		if (l > 0) {
			for (i = 1; l >= m; i++) {
				l = l / 2;
				r = i;
			}
		}
		return r;
	}
	
	/**
	 * Scale provided BufferedImage by the provided factor.
	 * A scaling factor value should be greater than 0 and less than 2.
	 * Note that scaling will impact performance and image quality.
	 * @param bi BufferedImage to be scaled.
	 * @param scale positive scaling factor
	 * @return scaled instance of provided BufferedImage
	 */
	public static BufferedImage scale(BufferedImage bi, double scale) {
		int w = (int)Math.ceil(bi.getWidth() * scale);
		int h = (int)Math.ceil(bi.getHeight() * scale);
		return scale(bi, w, h);
	}	
	
	/**
	 * Scale provided BufferedImage to the specified width and height dimensions.
	 * If a provided dimension is 0, the aspect ratio is used to calculate a value.
	 * @param bi BufferedImage to be scaled.
	 * @param w width the image is to be scaled to.
	 * @param h height the image is to be scaled to.
	 * @return scaled instance of provided BufferedImage
	 */
    public static BufferedImage scale(BufferedImage bi, int w, int h) {
    	if (w == 0 || h == 0) {
    		if (w == 0 && h == 0)
    			return bi;
    		if (w == 0) {
    			double n = new Double(h) / new Double(bi.getHeight());
    		    w = (int)Math.ceil(bi.getWidth() * n);
    		}
    		if (h == 0) {
    			double n = new Double(w) / new Double(bi.getWidth());
    		    h = (int)Math.ceil(bi.getHeight() * n);
    		}
    	}
		final Image scaledImage = bi.getScaledInstance(w, h, Image.SCALE_SMOOTH);
		final int type = (bi.getTransparency() == Transparency.OPAQUE) ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
		BufferedImage bImg = new BufferedImage(w, h, type);
		Graphics2D graphics = bImg.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		graphics.drawImage(scaledImage, null, null);
		graphics.dispose();
		scaledImage.flush();
		bi.flush();
		return bImg;
	}
    
	private static final String magic = "000c6a502020da87a";
	
	/**
	 * Read first 12 bytes from InputStream to determine if JP2 file.
	 * Note: Be sure to reset your stream after calling this method.
	 * @param in InputStream of possible JP2 codestream
	 * @return true is JP2 compatible format
	 */
	public final static boolean checkIfJp2(InputStream in) {
    	byte[] buf = new byte[12];
        try {
            in.read(buf, 0, 12);
        } catch (IOException e) {
                e.printStackTrace();
                return false;
        } 
	    StringBuffer sb = new StringBuffer(buf.length * 2);
	    for(int x = 0 ; x < buf.length ; x++) {
	       sb.append((Integer.toHexString(0xff & buf[x])));
	    }
	    String hexString = sb.toString();
	    return hexString.equals(magic);
	}
	
	public final static boolean isJp2Type(String mimetype) {
		if (mimetype == null)
			return false;
		if (mimetype.equals(FormatConstants.FORMAT_MIMEYPE_JP2)
			|| mimetype.equals(FormatConstants.FORMAT_MIMEYPE_JPX)
			|| mimetype.equals(FormatConstants.FORMAT_MIMEYPE_JPM))
			return true;
		else
			return false;
			
	}
}
