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

package gov.lanl.adore.djatoka.kdu;

import gov.lanl.adore.djatoka.DjatokaDecodeParam;
import gov.lanl.adore.djatoka.DjatokaException;
import gov.lanl.adore.djatoka.IExtract;
import gov.lanl.adore.djatoka.io.reader.PNMReader;
import gov.lanl.adore.djatoka.kdu.jni.KduCompressedSource;
import gov.lanl.adore.djatoka.util.IOUtils;
import gov.lanl.adore.djatoka.util.ImageProcessingUtils;
import gov.lanl.adore.djatoka.util.ImageRecord;
import gov.lanl.util.ExecuteStreamHandler;
import gov.lanl.util.PumpStreamHandler;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.StringTokenizer;

import kdu_jni.Jp2_family_src;
import kdu_jni.Jp2_input_box;
import kdu_jni.Jp2_locator;
import kdu_jni.Jpx_input_box;
import kdu_jni.Jpx_source;
import kdu_jni.KduException;
import kdu_jni.Kdu_codestream;
import kdu_jni.Kdu_coords;
import kdu_jni.Kdu_dims;
import kdu_jni.Kdu_global;
import kdu_jni.Kdu_params;

/**
 * Java bridge for kdu_expand application
 * @author Ryan Chute
 *
 */
public class KduExtractExe implements IExtract {
	private static boolean isWindows = false;
	private static String env;
	private static String exe;
	private static String[] envParams;
	/** Extract App Name "kdu_expand" */
	public static final String KDU_EXPAND_EXE = "kdu_expand";
	/** UNIX/Linux Standard Out Path: "/dev/stdout" */
	public static String STDOUT = "/dev/stdout";
	public static String STDIN = "/dev/stdin";

	static {
		env = System.getProperty("kakadu.home")
				+ System.getProperty("file.separator");
		exe = env
				+ ((System.getProperty("os.name").contains("Win")) ? KDU_EXPAND_EXE
						+ ".exe"
						: KDU_EXPAND_EXE);
		if (System.getProperty("os.name").startsWith("Mac")) {
			envParams = new String[] { "DYLD_LIBRARY_PATH="
					+ System.getProperty("DYLD_LIBRARY_PATH") };
		} else if (System.getProperty("os.name").startsWith("Win")) {
			isWindows = true;
		} else if (System.getProperty("os.name").startsWith("Linux")) {
			envParams = new String[] { "LD_LIBRARY_PATH="
					+ System.getProperty("LD_LIBRARY_PATH") };
		} else if (System.getProperty("os.name").startsWith("Solaris")) {
			envParams = new String[] { "LD_LIBRARY_PATH="
					+ System.getProperty("LD_LIBRARY_PATH") };
		}
	}

	/**
	 * Extracts region defined in DjatokaDecodeParam as BufferedImage
	 * @param input InputStream containing a JPEG 2000 image bitstream.
	 * @param params DjatokaDecodeParam instance containing region and transform settings.
	 * @return extracted region as a BufferedImage
	 * @throws DjatokaException
	 */
	public BufferedImage processUsingTemp(InputStream input, DjatokaDecodeParam params)
			throws DjatokaException {
		File in;
		// Copy to tmp file
		try {
			in = File.createTempFile("tmp", ".jp2");
			FileOutputStream fos = new FileOutputStream(in);
			in.deleteOnExit();
			IOUtils.copyStream(input, fos);
		} catch (IOException e) {
			throw new DjatokaException(e);
		}

		BufferedImage bi = process(in.getAbsolutePath(), params);

		if (in != null)
			in.delete();

		return bi;
	}

	/**
	 * Extracts region defined in DjatokaDecodeParam as BufferedImage
	 * @param is InputStream containing a JPEG 2000 image bitstream.
	 * @param params DjatokaDecodeParam instance containing region and transform settings.
	 * @return extracted region as a BufferedImage
	 * @throws DjatokaException
	 */
	public BufferedImage process(final InputStream is, DjatokaDecodeParam params) throws DjatokaException {
		if (isWindows)
			return processUsingTemp(is,params);
		
		ArrayList<Double> dims = null;
		if (params.getRegion() != null) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			IOUtils.copyStream(is, baos);
			dims = getRegionMetadata(new ByteArrayInputStream(baos.toByteArray()), params);
			return process(new ByteArrayInputStream(baos.toByteArray()), dims, params);
		} else 
			return process(is, dims, params);
	}
	/**
	 * Extracts region defined in DjatokaDecodeParam as BufferedImage
	 * @param is InputStream containing a JPEG 2000 image bitstream.
	 * @param dims region extraction dimensions
	 * @param params DjatokaDecodeParam instance containing region and transform settings.
	 * @return extracted region as a BufferedImage
	 * @throws DjatokaException
	 */
	public BufferedImage process(final InputStream is, ArrayList<Double> dims, DjatokaDecodeParam params) throws DjatokaException {
		String input = STDIN;
		String output = STDOUT;
		BufferedImage bi = null;
		try {
			final String command = getKduExtractCommand(input, output, dims, params);
			final Process process = Runtime.getRuntime().exec(command, envParams, new File(env));
			ByteArrayOutputStream stdout = new ByteArrayOutputStream();
			ByteArrayOutputStream stderr = new ByteArrayOutputStream();
			ExecuteStreamHandler streamHandler = new PumpStreamHandler(stdout, stderr, is);
	        try {
	            streamHandler.setProcessInputStream(process.getOutputStream());
	            streamHandler.setProcessOutputStream(process.getInputStream());
	            streamHandler.setProcessErrorStream(process.getErrorStream());
	        } catch (IOException e) {
	            process.destroy();
	            throw e;
	        }
	        streamHandler.start();

	        try {
	        	waitFor(process);
	            final ByteArrayInputStream bais = new ByteArrayInputStream(stdout.toByteArray());
	            bi = new PNMReader().open(bais);
	            streamHandler.stop();
	        } catch (ThreadDeath t) {
	            process.destroy();
	            throw t;
	        } finally {
				if (process != null) {
					closeStreams(process);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new DjatokaException(e);
		} 
		return bi;
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
		String output = STDOUT;
		File winOut = null;
		if (isWindows) {
			try {
				winOut = File.createTempFile("pipe_", ".ppm");
				winOut.deleteOnExit();
			} catch (IOException e) {
				throw new DjatokaException(e);
			}
			output = winOut.getAbsolutePath();
		}
		Runtime rt = Runtime.getRuntime();
		try {
			ArrayList<Double> dims = getRegionMetadata(input, params);
			String command = getKduExtractCommand(input, output, dims, params);
			final Process process = rt.exec(command, envParams, new File(env));
			
			if (output != null) {
				try {
					if (output.equals(STDOUT)) {
						return new PNMReader().open(new BufferedInputStream(
								process.getInputStream()));
					} else if (isWindows) {
						// Windows tests indicated need for delay (< 100ms failed)
						Thread.sleep(100);
						BufferedImage bi = null;
						try {
							bi = new PNMReader().open(new BufferedInputStream(new FileInputStream(new File(output))));
						} catch (Exception e) {
						    if (winOut != null)
								winOut.delete();
						    throw e;
						}
						if (winOut != null)
							winOut.delete();
						return bi;
				    } 
				} catch (Exception e) {
					String error = null;
					try {
						error = new String(IOUtils.getByteArray(process.getErrorStream()));
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				    if (error != null)
					    throw new DjatokaException(error);
				    else 
				    	throw new DjatokaException(e);
				} 
			}
			if (process != null) {
				process.getInputStream().close();
				process.getOutputStream().close();
				process.getErrorStream().close();
				process.destroy();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} 
		return null;
	}

	/**
	 * Gets Kdu Extract Command-line based on dims and params
	 * @param input absolute file path of JPEG 2000 image file.
	 * @param output absolute file path of PGM output image
	 * @param dims array of region parameters (i.e. y,x,h,w)
	 * @param params contains rotate and level extraction information
	 * @return command line string to extract region using kdu_extract
	 */
	public final String getKduExtractCommand(String input, String output,
			ArrayList<Double> dims, DjatokaDecodeParam params) {
		StringBuffer command = new StringBuffer(exe);
		if (input.equals(STDIN))
			command.append(" -no_seek");
		command.append(" -quiet -i ").append(escape(new File(input).getAbsolutePath()));
		command.append(" -o ").append(escape(new File(output).getAbsolutePath()));
		command.append(" ").append(toKduCompressArgs(params));
		if (dims != null && dims.size() == 4) {
			StringBuffer region = new StringBuffer();
			region.append("{").append(dims.get(0)).append(",").append(
					dims.get(1)).append("}").append(",");
			region.append("{").append(dims.get(2)).append(",").append(
					dims.get(3)).append("}");
			command.append("-region ").append(region.toString()).append(" ");
		}
		return command.toString();
	}
	
	/**
	 * Returns populated JPEG 2000 ImageRecord instance
	 * @param r ImageRecord containing file path the JPEG 2000 image
	 * @return a populated JPEG 2000 ImageRecord instance
	 * @throws DjatokaException
	 */
	public final ImageRecord getMetadata(ImageRecord r) throws DjatokaException {
		if (!new File(r.getImageFile()).exists())
			throw new DjatokaException("Image Does Not Exist");
		if (!checkIfJp2(r.getImageFile()))
			throw new DjatokaException("Not a JP2 image.");
		
		Jpx_source inputSource = new Jpx_source();
		Jp2_family_src jp2_family_in = new Jp2_family_src();
		
		int ref_component = 0;
		try {
			jp2_family_in.Open(r.getImageFile(), true);
			inputSource.Open(jp2_family_in, true);
			Kdu_codestream codestream = new Kdu_codestream();
			codestream.Create(inputSource.Access_codestream(ref_component).Open_stream());

			int minLevels = codestream.Get_min_dwt_levels();
			int depth = codestream.Get_bit_depth(ref_component);
			int colors = codestream.Get_num_components();
			int[] frames = new int[1];
			inputSource.Count_compositing_layers(frames);
			Kdu_dims image_dims = new Kdu_dims();
			codestream.Get_dims(ref_component, image_dims);
			Kdu_coords imageSize = image_dims.Access_size();
			
			r.setWidth(imageSize.Get_x());
			r.setHeight(imageSize.Get_y());
			r.setLevels(minLevels);
			r.setBitDepth(depth);
			r.setNumChannels(colors);
			r.setCompositingLayerCount(frames[0]);
			
			int[] v = new int[1];
			Kdu_params p = codestream.Access_siz().Access_cluster("COD");
			if (p != null) {
			    p.Get(Kdu_global.Clayers,0,0,v,true, true, true);
			    if (v[0] > 0)
			        r.setQualityLayers(v[0]);
			}
			
			if (codestream.Exists())
				codestream.Destroy();
			inputSource.Native_destroy();
			jp2_family_in.Native_destroy();
		} catch (KduException e) {
			throw new DjatokaException(e);
		}

		return r;
	}
	
	/**
	 * Returns populated JPEG 2000 ImageRecord instance
	 * @param is an InputStream containing the JPEG 2000 codestream
	 * @return a populated JPEG 2000 ImageRecord instance
	 * @throws DjatokaException
	 * 
	 * TODO: Current I/O method is a bit gross, but the KduCompressedSource approach
	 * is actually slower.  More work need to be done to improve the performance of
	 * the KduCompressedSource implementation.
	 *  
	 */
	public final ImageRecord getMetadata(final InputStream is) throws DjatokaException {
		File in = null;
		// Copy to tmp file
		try {
			in = File.createTempFile("tmp", ".jp2");
			FileOutputStream fos = new FileOutputStream(in);
			in.deleteOnExit();
			IOUtils.copyStream(is, fos);
		} catch (IOException e) {
			throw new DjatokaException(e);
		}
		ImageRecord r = getMetadata(new ImageRecord(in.getAbsolutePath()));

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
		if (!checkIfJp2(input))
			throw new DjatokaException("Not a JP2 image.");
		ArrayList<String> xmlList = null;
		Jp2_family_src jp2_family_in = new Jp2_family_src();
		Jp2_locator loc = new Jp2_locator();
		Jpx_input_box box = new Jpx_input_box();
		String[] values = new String[0];
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
				    box.Open_next();
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

		if (xmlList != null) {
			values = new String[xmlList.size()];
			xmlList.toArray(values);
		}
		
		return values;
	}

	/**
	 * Returns JPEG 2000 XML Box data in String[] from an InputStream
	 * @param is InputStream containing JP2 images
	 * @return an array of XML box values
	 * @throws DjatokaException
	 */
	public final String[] getXMLBox(final InputStream is) throws DjatokaException {
		ArrayList<String> xmlList = null;
		KduCompressedSource comp_src = null;
		try {
			comp_src = new KduCompressedSource(IOUtils.getByteArray(is));
		} catch (Exception e) {
			throw new DjatokaException(e);
		}
		Jp2_family_src jp2_family_in = new Jp2_family_src();
		Jp2_locator loc = new Jp2_locator();
		Jpx_input_box box = new Jpx_input_box();
		String[] values = null;
		try {
			jp2_family_in.Open(comp_src);
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
			comp_src = null;
		}

		if (xmlList != null) {
			values = new String[xmlList.size()];
			xmlList.toArray(values);
		}
		
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
	
	private final String magic = "000c6a502020da87a";
	private final boolean checkIfJp2(String input) {
    	InputStream in = null;
    	byte[] buf = new byte[12];
        try {
            in = new BufferedInputStream(new FileInputStream(new File(input)));
            in.read(buf, 0, 12);
            in.close();
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
	
	private final ArrayList<Double> getRegionMetadata(InputStream input,
			DjatokaDecodeParam params) throws DjatokaException {
		ImageRecord r = getMetadata(input);
		return getRegionMetadata(r, params);
	}
	
	private final ArrayList<Double> getRegionMetadata(String input,
			DjatokaDecodeParam params) throws DjatokaException {
		ImageRecord r = getMetadata(new ImageRecord(input));
		return getRegionMetadata(r, params);
	}
	
	private final ArrayList<Double> getRegionMetadata(ImageRecord r, DjatokaDecodeParam params)
			throws DjatokaException {
		if (params.getLevel() >= 0) {
			int levels = ImageProcessingUtils.getLevelCount(r.getWidth(), r.getHeight());
			levels = (r.getLevels() < levels) ? r.getLevels() : levels;
			int reduce = levels - params.getLevel();
			params.setLevelReductionFactor((reduce >= 0) ? reduce : 0);
		}

		int reduce = 1 << params.getLevelReductionFactor();
		ArrayList<Double> dims = new ArrayList<Double>();

		if (params.getRegion() != null) {
			StringTokenizer st = new StringTokenizer(params.getRegion(), "{},");
			String token;
			// top
			if ((token = st.nextToken()).contains("."))
				dims.add(Double.parseDouble(token));
			else
				dims.add(Double.parseDouble(token) / r.getHeight());
			// left
			if ((token = st.nextToken()).contains(".")) {
				dims.add(Double.parseDouble(token));
			} else
				dims.add(Double.parseDouble(token) / r.getWidth());
			// height
			if ((token = st.nextToken()).contains(".")) {
				dims.add(Double.parseDouble(token));
			} else
				dims.add(Double.parseDouble(token)
						/ (Double.valueOf(r.getHeight()) / Double
								.valueOf(reduce)));
			// width
			if ((token = st.nextToken()).contains(".")) {
				dims.add(Double.parseDouble(token));
			} else
				dims.add(Double.parseDouble(token)
						/ (Double.valueOf(r.getWidth()) / Double
								.valueOf(reduce)));
		}

		return dims;
	}
	
	private static String toKduCompressArgs(DjatokaDecodeParam params) {
		StringBuffer sb = new StringBuffer();
	    if (params.getLevelReductionFactor() > 0)
	        sb.append("-reduce ").append(params.getLevelReductionFactor()).append(" ");
	    if (params.getRotationDegree() > 0)
	    	sb.append("-rotate ").append(params.getRotationDegree()).append(" ");
	    if (params.getCompositingLayer() > 0)
	    	sb.append("-jpx_layer ").append(params.getCompositingLayer()).append(" ");
		return sb.toString();
	}
	
	private ImageRecord parseRecord(String record, ImageRecord r) {
		String[] list = record.split("\n");
		for (String kv : list) {
			if (kv.startsWith("Ssize")) {
				String v = kv.split("=")[1];
		        r.setWidth(Integer.parseInt(v.substring(v.indexOf(",") + 1,v.indexOf("}"))));
			    r.setHeight(Integer.parseInt(v.substring(1,v.indexOf(","))));
			}
			if (kv.startsWith("Clevels"))
			    r.setLevels(Integer.parseInt(kv.split("=")[1]));
			if (kv.startsWith("Sprecision"))
			    r.setBitDepth(Integer.parseInt(kv.split("=")[1].split(",")[0]));
			if (kv.startsWith("Scomponents"))
		    	r.setNumChannels(Integer.parseInt(kv.split("=")[1]));
			if (kv.startsWith("Clayers"))
			    r.setQualityLayers(Integer.parseInt(kv.split("=")[1]));
		}
		return r;
	}

	private static final String escape(String path) {
		if (path.contains(" "))
			path = "\"" + path + "\"";
		return path;
	}
	
	// Process Handler Utils
    private int waitFor(Process process) {
        try {
            process.waitFor();
            return process.exitValue();
        } catch (InterruptedException e) {
            process.destroy();
        }
        return 2;
    }
    private static void closeStreams(Process process) {
        close(process.getInputStream());
        close(process.getOutputStream());
        close(process.getErrorStream());
        process.destroy();
    }
    private static void close(InputStream device) {
        if (device != null) {
            try {
                device.close();
            } catch (IOException ioex) {
            }
        }
    }
    private static void close(OutputStream device) {
        if (device != null) {
            try {
                device.close();
            } catch (IOException ioex) {
            }
        }
    }

}
