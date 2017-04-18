/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.uber.bootstrap.url;

import java.io.*;
import java.util.*;
import java.util.zip.*;

public class VirtualWar implements Closeable {

    public static final String DEFAULT_DO_NOT_COPY = "CVS|\\.svn|\\.git|\\.DS_Store";
    public static final String EMPTY_HEADER = "<<EMPTY>>";
    private final Map<String, VirtualWarEntry> entries = new TreeMap<>();
    private final Map<String, Map<String, VirtualWarEntry>> directories = new TreeMap<>();
    private ZipFile zipFile;

    public VirtualWar(String... entryNames) throws IOException {
        for (String entryName : entryNames) {
            // make sure entryName starts with /
            if (!entryName.startsWith("/")) {
                entryName = "/" + entryName;
            }
            try {
                InputStream entryIs = getClass().getResourceAsStream(entryName);
                if (entryIs != null) {
                    entries.put(entryName, new VirtualWarEntry(asByteArray(entryIs)));
                }
            } catch (IOException ioe) {
                throw new IllegalArgumentException(entryName, ioe);
            }
        }
    }

    private byte[] asByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    public void write(OutputStream out) throws Exception {
        ZipOutputStream zout = new ZipOutputStream(out);
        Set<String> _directories = new HashSet<>();
        for (Map.Entry<String, VirtualWarEntry> entry : entries.entrySet()) {
            writeEntry(zout, _directories, entry.getKey(), entry.getValue());
        }
        zout.finish();
    }

    @Override
    public void close() {
        if (zipFile != null) {
            try {
                zipFile.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        entries.clear();
        directories.clear();
    }

    public boolean addEntry(String path, byte data[]) {
        return addEntry(path, data, true);
    }

    public boolean addEntry(String path, byte data[], boolean overwrite) {
        while (path.startsWith("/")) {
            path = path.substring(1);
        }

        String dir = getDirectory(path);
        Map<String, VirtualWarEntry> s = directories.get(dir);
        if (s == null) {
            s = new TreeMap<>();
            directories.put(dir, s);
            int n = dir.lastIndexOf('/');
            while (n > 0) {
                String dd = dir.substring(0, n);
                if (directories.containsKey(dd)) {
                    break;
                }
                directories.put(dd, null);
                n = dd.lastIndexOf('/');
            }
        }
        boolean duplicate = s.containsKey(path);
        if (!duplicate || overwrite) {
            VirtualWarEntry entry = new VirtualWarEntry(data);
            entries.put(path, entry);
            s.put(path, entry);
        }
        return duplicate;
    }

    private static String getDirectory(String path) {
        int n = path.lastIndexOf('/');
        if (n < 0) {
            return "";
        }
        return path.substring(0, n);
    }

    private static void writeEntry(ZipOutputStream jout, Set<String> directories, String path, VirtualWarEntry entry)
            throws Exception {
        if (entry == null) {
            return;
        }
        try {
            createDirectories(directories, jout, path);
            if (path.endsWith(EMPTY_HEADER)) {
                return;
            }
            ZipEntry ze = new ZipEntry(path);
            ze.setMethod(ZipEntry.DEFLATED);
            long _lastModified = entry.lastModified();
            if (_lastModified == 0L) {
                _lastModified = System.currentTimeMillis();
            }
            ze.setTime(_lastModified);
            jout.putNextEntry(ze);
            entry.write(jout);
            jout.closeEntry();
        } catch (Exception e) {
            throw new Exception("Problem writing entry " + path, e);
        }
    }

    private static void createDirectories(Set<String> directories, ZipOutputStream zip, String name) throws IOException {
        int index = name.lastIndexOf('/');
        if (index > 0) {
            String path = name.substring(0, index);
            if (directories.contains(path)) {
                return;
            }
            createDirectories(directories, zip, path);
            ZipEntry ze = new ZipEntry(path + '/');
            zip.putNextEntry(ze);
            zip.closeEntry();
            directories.add(path);
        }
    }
}