/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2014 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ======================================================================================
 *
 *     IF YOU DECIDE TO CHOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     "This program is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation; either version 2
 *     of the License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *     As a special exception to the terms and conditions of version 2.0 of
 *     the GPL (or any later version), you may redistribute this Program in connection
 *     with Free/Libre and Open Source Software ("FLOSS") applications as described
 *     in Jahia's FLOSS exception. You should have received a copy of the text
 *     describing the FLOSS exception, also available here:
 *     http://www.jahia.com/license"
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ======================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 *
 *
 * ==========================================================================================
 * =                                   ABOUT JAHIA                                          =
 * ==========================================================================================
 *
 *     Rooted in Open Source CMS, Jahia’s Digital Industrialization paradigm is about
 *     streamlining Enterprise digital projects across channels to truly control
 *     time-to-market and TCO, project after project.
 *     Putting an end to “the Tunnel effect�?, the Jahia Studio enables IT and
 *     marketing teams to collaboratively and iteratively build cutting-edge
 *     online business solutions.
 *     These, in turn, are securely and easily deployed as modules and apps,
 *     reusable across any digital projects, thanks to the Jahia Private App Store Software.
 *     Each solution provided by Jahia stems from this overarching vision:
 *     Digital Factory, Workspace Factory, Portal Factory and eCommerce Factory.
 *     Founded in 2002 and headquartered in Geneva, Switzerland,
 *     Jahia Solutions Group has its North American headquarters in Washington DC,
 *     with offices in Chicago, Toronto and throughout Europe.
 *     Jahia counts hundreds of global brands and governmental organizations
 *     among its loyal customers, in more than 20 countries across the globe.
 *
 *     For more information, please visit http://www.jahia.com
 */
package org.jahia.utils.zip.legacy;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipException;

/**
 * This class implements an output stream filter for writing files in the
 * ZIP file format. Includes support for both compressed and uncompressed
 * entries.
 *
 * @author	David Connelly
 * @version	1.27, 02/07/03
 */
public
class ZipOutputStream extends DeflaterOutputStream implements ZipConstants {
    private ZipEntry entry;
    private List entries = new ArrayList();
    private Map names = new HashMap();
    private CRC32 crc = new CRC32();
    private long written;
    private long locoff = 0;
    private String comment;
    private int method = DEFLATED;
    private boolean finished;

    private boolean closed = false;

    /**
     * Check to make sure that this stream has not been closed
     */
    private void ensureOpen() throws IOException {
	if (closed) {
	    throw new IOException("Stream closed");
        }
    }
    /**
     * Compression method for uncompressed (STORED) entries.
     */
    public static final int STORED = ZipEntry.STORED;

    /**
     * Compression method for compressed (DEFLATED) entries.
     */
    public static final int DEFLATED = ZipEntry.DEFLATED;

    /**
     * Creates a new ZIP output stream.
     * @param out the actual output stream
     */
    public ZipOutputStream(OutputStream out) {
	super(out, new Deflater(Deflater.DEFAULT_COMPRESSION, true));
        usesDefaultDeflater = true;
    }

    /**
     * Sets the ZIP file comment.
     * @param comment the comment string
     * @exception IllegalArgumentException if the length of the specified
     *		  ZIP file comment is greater than 0xFFFF bytes
     */
    public void setComment(String comment) {
        if (comment != null && comment.length() > 0xffff/3
                                           && getUTF8Length(comment) > 0xffff) {
	    throw new IllegalArgumentException("ZIP file comment too long.");
	}
	this.comment = comment;
    }

    /**
     * Sets the default compression method for subsequent entries. This
     * default will be used whenever the compression method is not specified
     * for an individual ZIP file entry, and is initially set to DEFLATED.
     * @param method the default compression method
     * @exception IllegalArgumentException if the specified compression method
     *		  is invalid
     */
    public void setMethod(int method) {
	if (method != DEFLATED && method != STORED) {
	    throw new IllegalArgumentException("invalid compression method");
	}
	this.method = method;
    }

    /**
     * Sets the compression level for subsequent entries which are DEFLATED.
     * The default setting is DEFAULT_COMPRESSION.
     * @param level the compression level (0-9)
     * @exception IllegalArgumentException if the compression level is invalid
     */
    public void setLevel(int level) {
	def.setLevel(level);
    }

    /**
     * Begins writing a new ZIP file entry and positions the stream to the
     * start of the entry data. Closes the current entry if still active.
     * The default compression method will be used if no compression method
     * was specified for the entry, and the current time will be used if
     * the entry has no set modification time.
     * @param e the ZIP entry to be written
     * @exception ZipException if a ZIP format error has occurred
     * @exception IOException if an I/O error has occurred
     */
    public void putNextEntry(ZipEntry e) throws IOException {
	ensureOpen();
	if (entry != null) {
	    closeEntry();	// close previous entry
	}
	if (e.time == -1) {
	    e.setTime(System.currentTimeMillis());
	}
	if (e.method == -1) {
	    e.method = method;	// use default method
	}
	switch (e.method) {
	case DEFLATED:
	    if (e.size == -1 || e.csize == -1 || e.crc == -1) {
		// store size, compressed size, and crc-32 in data descriptor
		// immediately following the compressed entry data
		e.flag = 8;
	    } else if (e.size != -1 && e.csize != -1 && e.crc != -1) {
		// store size, compressed size, and crc-32 in LOC header
		e.flag = 0;
	    } else {
		throw new ZipException(
		    "DEFLATED entry missing size, compressed size, or crc-32");
	    }
	    e.version = 20;
	    break;
	case STORED:
	    // compressed size, uncompressed size, and crc-32 must all be
	    // set for entries using STORED compression method
	    if (e.size == -1) {
		e.size = e.csize;
	    } else if (e.csize == -1) {
		e.csize = e.size;
	    } else if (e.size != e.csize) {
		throw new ZipException(
		    "STORED entry where compressed != uncompressed size");
	    }
	    if (e.size == -1 || e.crc == -1) {
		throw new ZipException(
		    "STORED entry missing size, compressed size, or crc-32");
	    }
	    e.version = 10;
	    e.flag = 0;
	    break;
	default:
	    throw new ZipException("unsupported compression method");
	}
	e.offset = written;
	if (names.put(e.name, e) != null) {
	    throw new ZipException("duplicate entry: " + e.name);
	}
        writeLOC(e);
	entries.add(e);
	entry = e;
    }

    /**
     * Closes the current ZIP entry and positions the stream for writing
     * the next entry.
     * @exception ZipException if a ZIP format error has occurred
     * @exception IOException if an I/O error has occurred
     */
    public void closeEntry() throws IOException {
	ensureOpen();
	ZipEntry e = entry;
	if (e != null) {
	    switch (e.method) {
	    case DEFLATED:
		def.finish();
		while (!def.finished()) {
		    deflate();
		}
		if ((e.flag & 8) == 0) {
		    // verify size, compressed size, and crc-32 settings
		    if (e.size != def.getTotalIn()) {
			throw new ZipException(
			    "invalid entry size (expected " + e.size +
			    " but got " + def.getTotalIn() + " bytes)");
		    }
		    if (e.csize != def.getTotalOut()) {
			throw new ZipException(
			    "invalid entry compressed size (expected " +
			    e.csize + " but got " + def.getTotalOut() +
			    " bytes)");
		    }
		    if (e.crc != crc.getValue()) {
			throw new ZipException(
			    "invalid entry CRC-32 (expected 0x" +
			    Long.toHexString(e.crc) + " but got 0x" +
			    Long.toHexString(crc.getValue()) + ")");
		    }
		} else {
		    e.size = def.getTotalIn();
		    e.csize = def.getTotalOut();
		    e.crc = crc.getValue();
		    writeEXT(e);
		}
		def.reset();
		written += e.csize;
		break;
	    case STORED:
		// we already know that both e.size and e.csize are the same
		if (e.size != written - locoff) {
		    throw new ZipException(
			"invalid entry size (expected " + e.size +
			" but got " + (written - locoff) + " bytes)");
		}
		if (e.crc != crc.getValue()) {
		    throw new ZipException(
			 "invalid entry crc-32 (expected 0x" +
			 Long.toHexString(e.crc) + " but got 0x" +
			 Long.toHexString(crc.getValue()) + ")");
		}
		break;
	    default:
		throw new InternalError("invalid compression method");
	    }
	    crc.reset();
	    entry = null;
	}
    }

    /**
     * Writes an array of bytes to the current ZIP entry data. This method
     * will block until all the bytes are written.
     * @param b the data to be written
     * @param off the start offset in the data
     * @param len the number of bytes that are written
     * @exception ZipException if a ZIP file error has occurred
     * @exception IOException if an I/O error has occurred
     */
    public synchronized void write(byte[] b, int off, int len)
	throws IOException
    {
	ensureOpen();
        if (off < 0 || len < 0 || off > b.length - len) {
	    throw new IndexOutOfBoundsException();
	} else if (len == 0) {
	    return;
	}

	if (entry == null) {
	    throw new ZipException("no current ZIP entry");
	}
	switch (entry.method) {
	case DEFLATED:
	    super.write(b, off, len);
	    break;
	case STORED:
	    written += len;
	    if (written - locoff > entry.size) {
		throw new ZipException(
		    "attempt to write past end of STORED entry");
	    }
	    out.write(b, off, len);
	    break;
	default:
	    throw new InternalError("invalid compression method");
	}
	crc.update(b, off, len);
    }

    /**
     * Finishes writing the contents of the ZIP output stream without closing
     * the underlying stream. Use this method when applying multiple filters
     * in succession to the same output stream.
     * @exception ZipException if a ZIP file error has occurred
     * @exception IOException if an I/O exception has occurred
     */
    public void finish() throws IOException {
	ensureOpen();
	if (finished) {
	    return;
	}
	if (entry != null) {
	    closeEntry();
	}
	if (entries.size() < 1) {
	    throw new ZipException("ZIP file must have at least one entry");
	}
	// write central directory
	long off = written;
	Iterator e = entries.iterator();
	while (e.hasNext()) {
	    writeCEN((ZipEntry)e.next());
	}
	writeEND(off, written - off);
	finished = true;
    }

    /**
     * Closes the ZIP output stream as well as the stream being filtered.
     * @exception ZipException if a ZIP file error has occurred
     * @exception IOException if an I/O error has occurred
     */
    public void close() throws IOException {
        if (!closed) {
            super.close();
            closed = true;
        }
    }

    /*
     * Writes local file (LOC) header for specified entry.
     */
    private void writeLOC(ZipEntry e) throws IOException {
	writeInt(LOCSIG);	    // LOC header signature
	writeShort(e.version);      // version needed to extract
	writeShort(e.flag);         // general purpose bit flag
	writeShort(e.method);       // compression method
	writeInt(e.time);           // last modification time
	if ((e.flag & 8) == 8) {
	    // store size, uncompressed size, and crc-32 in data descriptor
	    // immediately following compressed entry data
	    writeInt(0);
	    writeInt(0);
	    writeInt(0);
	} else {
	    writeInt(e.crc);        // crc-32
	    writeInt(e.csize);      // compressed size
	    writeInt(e.size);       // uncompressed size
	}
	byte[] nameBytes = getUTF8Bytes(e.name);
	writeShort(nameBytes.length);
	writeShort(e.extra != null ? e.extra.length : 0);
	writeBytes(nameBytes, 0, nameBytes.length);
	if (e.extra != null) {
	    writeBytes(e.extra, 0, e.extra.length);
	}
	locoff = written;
    }

    /*
     * Writes extra data descriptor (EXT) for specified entry.
     */
    private void writeEXT(ZipEntry e) throws IOException {
	writeInt(EXTSIG);	    // EXT header signature
	writeInt(e.crc);	    // crc-32
	writeInt(e.csize);	    // compressed size
	writeInt(e.size);	    // uncompressed size
    }

    /*
     * Write central directory (CEN) header for specified entry.
     * REMIND: add support for file attributes
     */
    private void writeCEN(ZipEntry e) throws IOException {
	writeInt(CENSIG);	    // CEN header signature
	writeShort(e.version);	    // version made by
	writeShort(e.version);	    // version needed to extract
	writeShort(e.flag);	    // general purpose bit flag
	writeShort(e.method);	    // compression method
	writeInt(e.time);	    // last modification time
	writeInt(e.crc);	    // crc-32
	writeInt(e.csize);	    // compressed size
	writeInt(e.size);	    // uncompressed size
	byte[] nameBytes = getUTF8Bytes(e.name);
	writeShort(nameBytes.length);
	writeShort(e.extra != null ? e.extra.length : 0);
	byte[] commentBytes;
	if (e.comment != null) {
	    commentBytes = getUTF8Bytes(e.comment);
	    writeShort(commentBytes.length);
	} else {
	    commentBytes = null;
	    writeShort(0);
	}
	writeShort(0);		    // starting disk number
	writeShort(0);		    // internal file attributes (unused)
	writeInt(0);		    // external file attributes (unused)
	writeInt(e.offset);	    // relative offset of local header
	writeBytes(nameBytes, 0, nameBytes.length);
	if (e.extra != null) {
	    writeBytes(e.extra, 0, e.extra.length);
	}
	if (commentBytes != null) {
	    writeBytes(commentBytes, 0, commentBytes.length);
	}
    }

    /*
     * Writes end of central directory (END) header.
     */
    private void writeEND(long off, long len) throws IOException {
	writeInt(ENDSIG);	    // END record signature
	writeShort(0);		    // number of this disk
	writeShort(0);		    // central directory start disk
	writeShort(entries.size()); // number of directory entries on disk
	writeShort(entries.size()); // total number of directory entries
	writeInt(len);		    // length of central directory
	writeInt(off);		    // offset of central directory
	if (comment != null) {	    // zip file comment
	    byte[] b = getUTF8Bytes(comment);
	    writeShort(b.length);
	    writeBytes(b, 0, b.length);
	} else {
	    writeShort(0);
	}
    }

    /*
     * Writes a 16-bit short to the output stream in little-endian byte order.
     */
    private void writeShort(int v) throws IOException {
	OutputStream out = this.out;
	out.write((v >>> 0) & 0xff);
	out.write((v >>> 8) & 0xff);
	written += 2;
    }

    /*
     * Writes a 32-bit int to the output stream in little-endian byte order.
     */
    private void writeInt(long v) throws IOException {
	OutputStream out = this.out;
	out.write((int)((v >>>  0) & 0xff));
	out.write((int)((v >>>  8) & 0xff));
	out.write((int)((v >>> 16) & 0xff));
	out.write((int)((v >>> 24) & 0xff));
	written += 4;
    }

    /*
     * Writes an array of bytes to the output stream.
     */
    private void writeBytes(byte[] b, int off, int len) throws IOException {
	super.out.write(b, off, len);
	written += len;
    }

    static private String encoding = "UTF-8";
    /*
     * Returns the length of String's UTF8 encoding.
     */
    static int getUTF8Length(String s) {
        return getUTF8Bytes(s).length;
    }

    /*
     * Returns an array of bytes representing the UTF8 encoding
     * of the specified String.
     */
    private static byte[] getUTF8Bytes(String s) {
        try {
            return s.getBytes(encoding);
        } catch (UnsupportedEncodingException e) {
            return s.getBytes();
        }
    }
}
