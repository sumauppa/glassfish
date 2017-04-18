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
package org.glassfish.uber.bootstrap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 *
 * @author rgrecour
 */
public abstract class Utils {

    public static final String JAR_FILE_EXT = ".jar";
    public static final String JAR_SCHEME = "jar";
    public static final String JAR_URI_SEPARATOR = "!/";
    private static final String MANIFEST_RESOURCE_PATH = "META-INF/MANIFEST.MF";

    public static File getFileFromUri(URI uri){
        try {
            if (isJar(uri)) {
                String fileUri = uri.getSchemeSpecificPart();
                if (fileUri.endsWith(JAR_URI_SEPARATOR)) {
                    fileUri = fileUri.substring(0, fileUri.length() - JAR_URI_SEPARATOR.length());
                }
                return new File(URI.create(fileUri).toURL().getFile());
            } else {
                return new File(uri.toURL().getFile());
            }
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    public static boolean isJar(URI uri) {
        return uri != null && JAR_SCHEME.equals(uri.getScheme());
    }

    public static boolean isJarFileName(String fname){
        return fname != null && fname.endsWith(JAR_FILE_EXT);
    }

    public static boolean isDirFileName(String fname){
        return fname != null && fname.endsWith("/");
    }

    public static boolean isDir(URI uri) {
        if(isJar(uri)){
            return false;
        }
        File file = new File(uri.toString());
        return file.exists() && file.isDirectory();
    }

    public static URI getRepoUri(String rootUrl) {
        if (rootUrl.endsWith(JAR_FILE_EXT)) {
            return URI.create(JAR_SCHEME + ":" + rootUrl + JAR_URI_SEPARATOR);
        } else {
            return URI.create("file:" + rootUrl);
        }
    }

    public static Attributes getManifestAttributes(URI uri) {
        try {
            InputStream is;
            is = new URL(uri.toString() + MANIFEST_RESOURCE_PATH).openStream();
            if (is != null) {
                Manifest manifest;
                manifest = new Manifest(is);
                return manifest.getMainAttributes();
            } else {
                throw new IllegalStateException("Unable to get manifest attributes");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to get manifest attributes", ex);
        }
    }

    public static File unpackJarEntry(JarFile jarFile, JarEntry jarEntry) throws IOException {
        InputStream input;
        OutputStream output;
        try {
            String name = jarEntry.getName().replace('/', '_');
            int i = name.lastIndexOf(".");
            String extension = i > -1 ? name.substring(i) : "";
            File file = File.createTempFile(
                    name.substring(0, name.length() - extension.length()) + ".",
                    extension);
            file.deleteOnExit();
            input = jarFile.getInputStream(jarEntry);
            output = new FileOutputStream(file);
            int readCount;
            byte[] buffer = new byte[4096];
            while ((readCount = input.read(buffer)) != -1) {
                output.write(buffer, 0, readCount);
            }
            input.close();
            output.close();
            return file;
        } finally {
        }
    }
}
