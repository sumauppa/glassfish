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
import java.io.FileFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.glassfish.uber.bootstrap.BootLoader.JarFilter;

/**
 * The parent classloader, that will load the bootstrap classes
 *
 * @author rgrecour
 */
public class BootLoader extends URLClassLoader {

    private final JarFilter jarFilter;
    private final DirFilter dirFilter;
    private final ResourceTranslator resourceTranslator;

    public BootLoader(URL[] urls, ClassLoader parent, URI repoUri, JarFilter filter, ResourceTranslator resourceTranslator) {
        super(urls, parent);
        this.jarFilter = filter;
        this.dirFilter = new DirFilter(jarFilter);
        this.resourceTranslator = resourceTranslator;
        try {
            if (Utils.isJar(repoUri)) {
                addJarResource(Utils.getFileFromUri(repoUri));
            } else if (Utils.isDir(repoUri)) {
                addDirResource(Utils.getFileFromUri(repoUri));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static interface JarFilter {
        boolean isIncluded(String name);
    }

    public static interface ResourceTranslator {
        boolean isTranslatedResource(String name);
        URL translateResource(String name);
    }

    private static class DirFilter implements FileFilter {

        private final JarFilter jarFilter;
        public DirFilter(JarFilter jarFilter) {
            this.jarFilter = jarFilter;
        }

        @Override
        public boolean accept(File file) {
            return jarFilter.isIncluded(file.getPath());
        }
    };

    @Override
    public URL getResource(String name) {
        if(name != null &&
                resourceTranslator.isTranslatedResource(name)){
            return resourceTranslator.translateResource(name);
        }
        return super.getResource(name);
    }

    @Override
    protected Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            Class clazz = findLoadedClass(name);
            if (clazz == null) {
                clazz = findClass(name);
                if (resolve) {
                    resolveClass(clazz);
                }
            }
            return clazz;
        } catch (ClassNotFoundException e) {
            return super.loadClass(name, resolve);
        }
    }

    private void addJarResource(File file) throws IOException {
        JarFile jarFile = new JarFile(file);
        addURL(file.toURL());
        Enumeration jarEntries = jarFile.entries();
        while (jarEntries.hasMoreElements()) {
            JarEntry jarEntry = (JarEntry) jarEntries.nextElement();
            if (!jarEntry.isDirectory() && Utils.isJarFileName(jarEntry.getName())) {
                if(jarFilter.isIncluded(jarEntry.getName())){
                    File f = Utils.unpackJarEntry(jarFile, jarEntry);
                    System.out.println("BootLoader: loading "+jarEntry.getName());
                    addURL(f.toURL());
                }
            }
        }
    }

    private void addDirResource(File root) throws IOException {
        for (File file : root.listFiles(dirFilter)) {
            if(file.isDirectory()){
                addDirResource(root);
            } else {
                addURL(file.toURL());
            }
        }
    }
}
