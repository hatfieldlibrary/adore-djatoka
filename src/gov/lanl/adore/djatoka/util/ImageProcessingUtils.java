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
import ij.ImagePlus;
import ij.io.FileInfo;
import ij.io.Opener;
import ij.io.TiffDecoder;
import ij.process.ImageProcessor;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorModel;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.apache.log4j.Logger;

import sun.awt.image.BufferedImageGraphicsConfig;

/**
 * Image Processing Utilities
 * @author Ryan Chute
 *
 */
public class ImageProcessingUtils {
	static Logger logger = Logger.getLogger(ImageProcessingUtils.class);
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
	 * Return the resolution level the djatoka API will use to extract
	 * an image for scaling.
	 * @param w max pixel width
	 * @param h max pixel height
	 * @param out_w max pixel width
	 * @param out_h max pixel height
	 */
	public static int getScalingLevel(int w, int h, int out_w, int out_h) {
		int levels = getLevelCount(w, h);
		int max_source = Math.max(w, h);
		int max_out = Math.max(out_w, out_h);
		int r = levels + 2;
		int i = max_source;
		while (i >= max_out) {
			i = i / 2;
			r--;
		}
		return r;
	}
	
	/**
	 * Scale provided BufferedImage by the provided factor.
	 * @param bi BufferedImage to be scaled.
	 * @param scale positive scaling factor
	 * @return scaled instance of provided BufferedImage
	 */
	public static BufferedImage scale(BufferedImage bi, double scale) {
		int w = (int) Math.ceil(((double) bi.getWidth() * scale));
		int h = (int) Math.ceil(((double) bi.getHeight() * scale));
		return getScaledInstance(bi, w, h, RenderingHints.VALUE_INTERPOLATION_BICUBIC, true);
	}	

	/**
     * Convenience method that returns a scaled instance of the
     * provided {@code BufferedImage}.
     *
     * @param img the original image to be scaled
     * @param targetWidth the desired width of the scaled instance,
     *    in pixels
     * @param targetHeight the desired height of the scaled instance,
     *    in pixels
     * @param hint one of the rendering hints that corresponds to
     *    {@code RenderingHints.KEY_INTERPOLATION} (e.g.
     *    {@code RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR},
     *    {@code RenderingHints.VALUE_INTERPOLATION_BILINEAR},
     *    {@code RenderingHints.VALUE_INTERPOLATION_BICUBIC})
     * @param higherQuality if true, this method will use a multi-step
     *    scaling technique that provides higher quality than the usual
     *    one-step technique (only useful in downscaling cases, where
     *    {@code targetWidth} or {@code targetHeight} is
     *    smaller than the original dimensions, and generally only when
     *    the {@code BILINEAR} hint is specified)
     * @return a scaled version of the original {@code BufferedImage}
     */
    public static BufferedImage getScaledInstance(BufferedImage img,
                                           int targetWidth,
                                           int targetHeight,
                                           Object hint,
                                           boolean higherQuality)
    {
        int type = (img.getTransparency() == Transparency.OPAQUE) ?
            BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
        BufferedImage ret = (BufferedImage)img;
        int w, h;
        if (higherQuality) {
            // Use multi-step technique: start with original size, then
            // scale down in multiple passes with drawImage()
            // until the target size is reached
            w = img.getWidth();
            h = img.getHeight();
        } else {
            // Use one-step technique: scale directly from original
            // size to target size with a single drawImage() call
            w = targetWidth;
            h = targetHeight;
        }
        
        do {
            if (higherQuality && w > targetWidth) {
                w /= 2;
                if (w < targetWidth) {
                    w = targetWidth;
                }
            }

            if (higherQuality && h > targetHeight) {
                h /= 2;
                if (h < targetHeight) {
                    h = targetHeight;
                }
            }

            BufferedImage tmp = new BufferedImage(w, h, type);
            Graphics2D g2 = tmp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, hint);
            g2.drawImage(ret, 0, 0, w, h, null);
            g2.dispose();

            ret = tmp;
        } while (w != targetWidth || h != targetHeight);

        return ret;
    }

	
	/**
	 * Scale provided BufferedImage to the specified width and height dimensions.
	 * If a provided dimension is 0, the aspect ratio is used to calculate a value.
	 * Also, if either contains -1, the positive value will be used as for the long
	 * side.
	 * @param bi BufferedImage to be scaled.
	 * @param w width the image is to be scaled to.
	 * @param h height the image is to be scaled to.
	 * @return scaled instance of provided BufferedImage
	 */
    public static BufferedImage scale(BufferedImage bi, int w, int h) {
    	// If either w,h are -1, then calculate based on long side.
    	if (w == -1 || h == -1) {
    		int tl = Math.max(w, h);
    		if (bi.getWidth() > bi.getHeight()) {
    			w = tl;
    			h = 0;
    		} else {
    		    h = tl;
    		    w = 0;
    		}
    	}
    	// Calculate dim. based on aspect ratio
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

		return getScaledInstance(bi, w, h, RenderingHints.VALUE_INTERPOLATION_BICUBIC, true);
	}
    
	private static final String magic = "000c6a502020da87a";
	
	/**
	 * Read first 12 bytes from File to determine if JP2 file.
	 * @param f Path to JPEG 2000 image file
	 * @return true is JP2 compatible format
	 */
	public final static boolean checkIfJp2(String f) {
		boolean isJP2 = false;
		try {
			BufferedInputStream bi = new BufferedInputStream(new FileInputStream(new File(f)));
			isJP2 = checkIfJp2(bi);
			bi.close();
		} catch (FileNotFoundException e) {
			logger.error(e + " attempting to access: " + f);
		} catch (IOException e) {
			logger.error(e + " attempting to access: " + f);
		}
		return isJP2;
	}
	
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
	
	/**
	 * Given a mimetype, indicates if mimetype is JP2 compatible.
	 * @param mimetype mimetype to check if JP2 compatible
	 * @return true is JP2 compatible
	 */
	public final static boolean isJp2Type(String mimetype) {
		if (mimetype == null)
			return false;
		mimetype = mimetype.toLowerCase();
		if (mimetype.equals(FormatConstants.FORMAT_MIMEYPE_JP2)
			|| mimetype.equals(FormatConstants.FORMAT_MIMEYPE_JPX)
			|| mimetype.equals(FormatConstants.FORMAT_MIMEYPE_JPM))
			return true;
		else
			return false;
			
	}
	
	/**
	 * Attempt to determine if file is a TIFF using the file header.
	 * @param file
	 * @return true if the file is a TIFF
	 */
	public final static boolean checkIfTiff(String file) {
		return new Opener().getFileType(file) == Opener.TIFF;
	}
	
	/**
	 * Attempt to determine is file is an uncompressed TIFF
	 * @param file File path for image to check
	 * @return true if file is an uncompressed TIFF
	 */
	public static boolean isUncompressedTiff(String file) {
		File f = new File(file);
		FileInfo[] fi = null;
		TiffDecoder ti = new TiffDecoder(f.getParent() + "/", f.getName());
		try {
			fi = ti.getTiffInfo();
		} catch (IOException e) {
			return false;
		}
		if (fi[0].compression == 1)
		    return true;
		else
			return false;
	}
	
	/** 
	 * Populates a BufferedImage from a RenderedImage
	 * Source: http://www.jguru.com/faq/view.jsp?EID=114602
	 * @param img RenderedImage to be converted to BufferedImage
	 * @return BufferedImage with complete raster data
	 */
	public static BufferedImage convertRenderedImage(RenderedImage img) {
		if (img instanceof BufferedImage) {
			return (BufferedImage)img;	
		}	
		ColorModel cm = img.getColorModel();
		int width = img.getWidth();
		int height = img.getHeight();
		WritableRaster raster = cm.createCompatibleWritableRaster(width, height);
		boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
		Hashtable properties = new Hashtable();
		String[] keys = img.getPropertyNames();
		if (keys!=null) {
			for (int i = 0; i < keys.length; i++) {
				properties.put(keys[i], img.getProperty(keys[i]));
			}
		}
		BufferedImage result = new BufferedImage(cm, raster, isAlphaPremultiplied, properties);
		img.copyData(raster);
		return result;
	}
}
