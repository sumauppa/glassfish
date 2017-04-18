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
package org.glassfish.uber.descriptors;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map.Entry;
import java.util.jar.Attributes;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author rgrecour
 */
@XmlRootElement(name = "pkg-metadata")
public class PkgMetadata {

    public static final String METADATA_LOCATION = "META-INF/package-metadata.xml";

    @XmlAttribute
    String name;

    @XmlElement
    List<String> features = new ArrayList<>();

    @XmlElement
    List<OSGiPackage> exports = new ArrayList<>();

    @XmlElement
    List<OSGiPackage> imports = new ArrayList<>();

    @XmlElement
    List<OSGiPackage> dynamicImports = new ArrayList<>();

    private static Marshaller marshaller;
    private static Unmarshaller unmarshaller;
    private static JAXBContext ctxt;
    private static boolean initialized = false;
    private static final String ZIP_EXT = ".zip";
    private static final String JAR_EXT = ".jar";

    private static boolean isValidDirectory(final File dir) {
        if (!dir.exists()) {
            return false;
        }
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException(dir + " is not a directory.");
        }
        return true;
    }

    private static List<Entry<JarInputStream,String>> findJars(final File dir) throws IOException {
        if (!isValidDirectory(dir)) {
            return Collections.emptyList();
        }

        final ArrayList<Entry<JarInputStream,String>> result = new ArrayList<>();
        final File[] files = dir.listFiles();

        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    result.addAll(findJars(f));
                } else if (f.getName().endsWith(JAR_EXT)) {
                    String name = f.getName().substring(0,f.getName().length() - JAR_EXT.length());
                    result.add(new SimpleEntry<>(new JarInputStream(new FileInputStream(f)),name));
                }
            }
        }
        return result;
    }

    private static List<Entry<JarInputStream,String>> findJars(final ZipFile zipFile) throws IOException {
        final ArrayList<Entry<JarInputStream,String>> result = new ArrayList<>();
        final Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while(entries.hasMoreElements()){
            ZipEntry entry = entries.nextElement();
            if(!entry.isDirectory() && entry.getName().endsWith(JAR_EXT)){
                String name = entry.getName().substring(0,entry.getName().length() - JAR_EXT.length());
                result.add(new SimpleEntry<>(new JarInputStream(zipFile.getInputStream(entry)),name));
            }
        }
        return result;
    }

    public PkgMetadata() {
    }

    private PkgMetadata(List<Entry<JarInputStream,String>> jars, String name) throws IOException{
        this.name = name;
        if (jars != null && !jars.isEmpty()) {
            for (Entry<JarInputStream,String> entry : jars) {
                parseManifestJar(entry.getKey(),entry.getValue());
            }
        }
    }

    public PkgMetadata(File pkg, String name) throws IOException{
        this(findJars(new ZipFile(pkg)), name);
    }

    public PkgMetadata(String name, File stageDir) throws IOException {
        this(findJars(stageDir),name);
    }

    public final void parseManifestJar(JarInputStream jar, String name) throws IOException {
        Manifest m = jar.getManifest();
        Attributes attrs = m.getMainAttributes();
        boolean fragment = attrs.containsKey(new Attributes.Name("Fragment-Host"));
        for (Object k : attrs.keySet()) {
            Attributes.Name key = (Attributes.Name) k;
            switch (key.toString()) {
                case "Export-Package": {
                    List<OSGiPackage> packages = OSGiPackage.parse(name,fragment,(String) attrs.get(k));
                    exports.addAll(packages);
                    break;
                }
                case "Import-Package": {
                    List<OSGiPackage> packages = OSGiPackage.parse(name,fragment,(String) attrs.get(k));
                    imports.addAll(packages);
                    break;
                }
                case "DynamicImport-Package": {
                    List<OSGiPackage> packages = OSGiPackage.parse(name,fragment,(String) attrs.get(k));
                    dynamicImports.addAll(packages);
                    break;
                }
                default:
                    break;
            }
        }
    }

    public static void init() throws JAXBException {
        if (!initialized) {
            ctxt = JAXBContext.newInstance(PkgMetadata.class);
            initialized = true;
        }
    }

    public void writeXML(File output) throws JAXBException {
        init();
        if (marshaller == null) {
            marshaller = ctxt.createMarshaller();
        }
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.marshal(this, output);
    }

    public static PkgMetadata readXML(InputStream input) throws JAXBException {
        init();
        if (unmarshaller == null) {
            unmarshaller = ctxt.createUnmarshaller();
        }
        return (PkgMetadata) unmarshaller.unmarshal(input);
    }

    public static void main(String[] args) throws IOException{
        PkgMetadata test = new PkgMetadata();
        File pkg = new File(args[0]);
        test.parseManifestJar(new JarInputStream(new FileInputStream(pkg)),
                pkg.getName().substring(0,pkg.getName().length()- JAR_EXT.length()));
    }
}
