/**
 * ==========================================================================================
 * =                        DIGITAL FACTORY v7.1 - Community Distribution                   =
 * ==========================================================================================
 *
 *     Rooted in Open Source CMS, Jahia's Digital Industrialization paradigm is about
 *     streamlining Enterprise digital projects across channels to truly control
 *     time-to-market and TCO, project after project.
 *     Putting an end to "the Tunnel effect", the Jahia Studio enables IT and
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
 *
 * JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION
 * ============================================
 *
 *     Copyright (C) 2002-2015 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==========================================================
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
 *     describing the FLOSS exception, and it is also available here:
 *     http://www.jahia.com/license"
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ==========================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.utils.zip.legacy;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.zeroturnaround.zip.ZipUtil;

/**
 * Utility class for to repackaging the large (>4GB) ZIP files, exported by Digital Factory 7.0.0.4 or earlier versions into JDK-compliant
 * ZIP files, which are supported by DF 7.0.0.5+.
 * 
 * @author Sergiy Shyrkov
 */
public class LegacyZipFormatConverter {

    private static void copyToFile(InputStream source, File destination) throws IOException {
        FileOutputStream output = FileUtils.openOutputStream(destination);
        try {
            IOUtils.copy(source, output);
            output.close();
        } finally {
            IOUtils.closeQuietly(output);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("[ERROR]: No ZIP file specified");
            return;
        }
        boolean repackage = args.length > 1 && (args[0].equals("-r") || args[0].equals("r"));
        File legacyZip = new File(args[args.length - 1]).getCanonicalFile();

        long startTime = System.currentTimeMillis();

        if (repackage) {
            out("Start repackaging " + legacyZip);
            File tmpUnzip = unzipLegacy(legacyZip, repackage);
            try {
                zip(tmpUnzip, new File(legacyZip.getParentFile(), FilenameUtils.getBaseName(legacyZip.getName())
                        + "-repackaged." + FilenameUtils.getExtension(legacyZip.getName())));
            } finally {
                FileUtils.deleteQuietly(tmpUnzip);
            }
        } else {
            out("Start unzipping " + legacyZip);
            File target = new File(legacyZip.getParentFile(), FilenameUtils.getBaseName(legacyZip.getName()));
            if (target.exists()) {
                throw new IOException("There is already a folder with extracted content at " + target
                        + ". You should delete it first before attempting to unzip again");
            }
            unzipLegacy(legacyZip, target, repackage);
        }

        out("Done in " + (System.currentTimeMillis() - startTime) + " ms");
    }

    private static void out(String message) {
        System.out.println(message);
    }

    public static File unzipLegacy(File legacyZip, boolean repackage) throws IOException {
        File target = new File(FileUtils.getTempDirectory(), System.currentTimeMillis() + "-" + legacyZip.getName());
        FileUtils.deleteQuietly(target);
        target.mkdirs();

        unzipLegacy(legacyZip, target, repackage);

        return target;
    }

    public static void unzipLegacy(File legacyZip, File targetDir, boolean repackage) throws IOException {
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(legacyZip), 1024 * 1024));

        try {
            String filename = null;
            ZipEntry zipentry = null;

            while ((zipentry = zis.getNextEntry()) != null) {
                try {
                    if (zipentry.isDirectory()) {
                        continue;
                    }
                    filename = zipentry.getName().replace('\\', '/');
                    File targetFile = new File(targetDir, filename).getCanonicalFile();
                    out("Extracting file " + filename);
                    if (repackage && filename.indexOf('/') == -1 && filename.endsWith(".zip")) {
                        File tmpZip = File.createTempFile("tmp", ".zip");
                        File unzippedTmp = null;
                        try {
                            copyToFile(zis, tmpZip);
                            out("Repackaging " + filename);
                            unzippedTmp = unzipLegacy(tmpZip, false);
                            zip(unzippedTmp, targetFile);
                        } finally {
                            FileUtils.deleteQuietly(tmpZip);
                            FileUtils.deleteQuietly(unzippedTmp);
                        }
                    } else {
                        copyToFile(zis, targetFile);
                    }
                } finally {
                    zis.closeEntry();
                }
            }
        } finally {
            if (zis != null) {
                zis.close();
            }
        }
    }

    public static void zip(File sourceDir, File targetFile) {
        out("Start compressing content of the " + sourceDir + " into ZIP file " + targetFile);
        ZipUtil.pack(sourceDir, targetFile);
        out("Done compressing into ZIP file " + targetFile);
    }

}
