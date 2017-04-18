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
package com.sun.enterprise.glassfish.bootstrap.osgi;

import com.sun.enterprise.glassfish.bootstrap.Util;
import static com.sun.enterprise.glassfish.bootstrap.Util.whichJar;
import static com.sun.enterprise.glassfish.bootstrap.osgi.Constants.FILE_SCHEME;
import org.glassfish.embeddable.BootstrapProperties;
import org.glassfish.embeddable.GlassFishRuntime;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import org.glassfish.embeddable.GlassFishException;
import org.glassfish.embeddable.spi.RuntimeBuilder;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;

public class UberJarGlassFishRuntimeBuilder implements RuntimeBuilder {

    // all properties names
    public static final String INSTALL_ROOT_PROP_NAME = "com.sun.aas.installRoot";
    public static final String INSTALL_ROOT_URI_PROP_NAME = "com.sun.aas.installRootURI";
    public static final String INSTANCE_ROOT_PROP_NAME = "com.sun.aas.instanceRoot";
    public static final String INSTANCE_ROOT_URI_PROP_NAME = "com.sun.aas.instanceRootURI";
    public static final String EXTRA_CLASSPATH_PROP_NAME = "org.glassfish.uber.extra.classpath";
    public static final String HK2_CACHE_DIR_PROP_NAME = "com.sun.enterprise.hk2.cacheDir";
    public static final String GLASSFISH_PLATFORM_PROP_NAME = "GlassFish_Platform";
    public static final String GLASSFISH_BUILDER_NAME_PROP_NAME = "GlassFish.BUILDER_NAME";

    // a few prop values
    public static final String GLASSFISH_BUILDER_NAME_PROP_VALUE = "uber";
    public static final String GLASSFISH_PLATFORM_PROP_VALUE = "Felix";
    public static final String HK2_UBER_ENABLED_PROP_VALUE = "true";

    private static final String INSTALL_DIR = "glassfish4/glassfish/";
    private static final String MODULES_DIR = INSTALL_DIR + "modules/";
    private static final String JAR_SCHEME = "jar";

    // entries required for the JSP compiler classpath
    private static final String[] COMPILE_CLASSPATH_ENTRIES = new String[]{
        MODULES_DIR + "javax.servlet-api.jar",
        MODULES_DIR + "javax.servlet.jsp-api.jar",
        MODULES_DIR + "javax.servlet.jsp.jar",
        MODULES_DIR + "javax.servlet.jsp.jstl-api.jar",
         MODULES_DIR + "javax.servlet.jsp.jstl.jar"
    };

    // entries required to create the domain
    private static final String[] NUCLEUS_DOMAIN_ENTRIES = new String[]{
        "config/keyfile",
        "config/server.policy",
        "config/restrict.server.policy",
        "config/javaee.server.policy",
        "config/login.conf",
        "config/default-logging.properties"
    };
    private static final String[] SECURITY_DOMAIN_ENTRIES = new String[]{
      "config/admin-keyfile",
      "config/cacerts.jks",
      "config/keystore.jks"
    };
    private static final String DOMAIN_XML_ENTRY = "org/glassfish/embed/domain.xml";
    private static final String LOGGING_PROPERTIES_ENTRY = "config/logging.properties";
    private static final String OSGI_PROPERTIES_ENTRY = INSTALL_DIR + "config/osgi.properties";
    private static final String DEFAULT_WEB_XML_ENTRY = "org/glassfish/web/embed/default-web.xml";

    // location of specific jars
    private static final String SECURITY_JAR_LOCATION = resourcePath(MODULES_DIR + "security.jar");
    private static final String NUCLEUS_DOMAIN_JAR_LOCATION = resourcePath(INSTALL_DIR + "common/templates/gf/nucleus-domain.jar");
    private static final String KERNEL_JAR_LOCATION = resourcePath(MODULES_DIR + "kernel.jar");
    private static final String WEB_EMBED_API_JAR_LOCATION = resourcePath(MODULES_DIR + "web-embed-api.jar");

    private static final String EXTRA_CLASSPATH_DIR_NAME = "extra-classpath";
    private static final String DOCROOT_DIR_NAME = "docroot";
    private static final String CONFIGROOT_DIR_NAME = "config";

    private OSGiFrameworkLauncher osgiLauncher;
    private final URI jarUri;
    private final String installRootUri;
    private final Properties properties;
    private final URL domainJar;
    private final URL securityJar;
    private final URL kernelJar;
    private final URL webEmbedJar;
    private final URL[] webCompileClasspath;

    public UberJarGlassFishRuntimeBuilder() throws GlassFishException {
        this.jarUri = whichJar(getClass().getClassLoader().getClass());
        this.installRootUri = JAR_SCHEME + ":" + jarUri.toString() + "!/" + INSTALL_DIR;
        this.properties = new Properties();
        this.properties.setProperty(GLASSFISH_BUILDER_NAME_PROP_NAME, GLASSFISH_BUILDER_NAME_PROP_VALUE);
        this.properties.setProperty(INSTALL_ROOT_PROP_NAME, installRootUri);
        this.properties.setProperty(INSTALL_ROOT_URI_PROP_NAME, installRootUri);
        try {
            properties.setProperty(GLASSFISH_PLATFORM_PROP_NAME, GLASSFISH_PLATFORM_PROP_VALUE);
            properties.load(getClass().getResourceAsStream(resourcePath(OSGI_PROPERTIES_ENTRY)));
            Util.substVars(properties);
        } catch (IOException ex) {
            throw new GlassFishException(ex);
        }
        this.domainJar = getClass().getResource(NUCLEUS_DOMAIN_JAR_LOCATION);
        this.securityJar = getClass().getResource(SECURITY_JAR_LOCATION);
        this.kernelJar = getClass().getResource(KERNEL_JAR_LOCATION);
        this.webEmbedJar = getClass().getResource(WEB_EMBED_API_JAR_LOCATION);
        webCompileClasspath = new URL[COMPILE_CLASSPATH_ENTRIES.length];
        for(int i=0; i<COMPILE_CLASSPATH_ENTRIES.length;i++){
            webCompileClasspath[i] = getClass().getResource(
                    resourcePath(COMPILE_CLASSPATH_ENTRIES[i]));
        }
    }

    private static File extractJar(URL jar, Path targetDir) throws IOException {
        String fname = jar.getPath();
        fname = fname.substring(fname.lastIndexOf("/")+1);
        File target = targetDir.resolve(fname).toFile();
        InputStream in = jar.openStream();
        try (FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) >= 0) {
                out.write(buf, 0, len);
            }
        }
        return target;
    }

    private static File[] extractJarEntries(URL jar, String[] entryNames, Path targetDir) throws IOException {
        List<File> files = new ArrayList<>();
        for (String entryName : entryNames) {
            files.add(extractJarEntry(jar, entryName, targetDir));
        }
        return files.toArray(new File[files.size()]);
    }

    private static File extractJarEntry(URL jar, String entryName, Path targetDir) throws IOException {
        Path target = targetDir.resolve(entryName.substring(entryName.lastIndexOf('/')+1));
        JarInputStream is = new JarInputStream(jar.openStream());
        JarEntry entry;
        while ((entry = is.getNextJarEntry()) != null) {
            byte[] buffer = new byte[1024 * 4];
            int n = 0;
            boolean found = entry.getName().equals(entryName);
            FileOutputStream out = new FileOutputStream(target.toFile());
            while (-1 != (n = is.read(buffer))) {
                if (found) {
                    out.write(buffer, 0, n);
                }
            }
            if (found) {
                out.close();
                return target.toFile();
            }
        }
        throw new IOException("Entry not found: " + entryName);
    }

    private static String resourcePath(String str){
        if(!str.startsWith("/")){
            str = "/"+str;
        }
        return str;
    }

    @Override
    public boolean handles(BootstrapProperties bsOptions) {
        return GLASSFISH_BUILDER_NAME_PROP_VALUE.equals(
                bsOptions.getProperty(GLASSFISH_BUILDER_NAME_PROP_NAME));
    }

    public GlassFishRuntime build() throws GlassFishException {
        return build(new BootstrapProperties());
    }

    @Override
    public GlassFishRuntime build(BootstrapProperties bsOptions) throws GlassFishException {
        this.properties.putAll(bsOptions.getProperties());
        Path instanceRoot = createInstanceRoot();
        generateDomain(instanceRoot);
        setClassPathProperties(instanceRoot);
        setOSGiProperties(instanceRoot);
        System.getProperties().putAll(this.properties);
        setupOSGiFramework();
        return getService(GlassFishRuntime.class);
    }

    public <T> T getService(Class<T> type) throws GlassFishException {
        if(this.osgiLauncher == null){
            throw new IllegalStateException("OSGi launcher not initialized");
        }
        ServiceTracker tracker = new ServiceTracker(getBundleContext(), type.getName(), null);
        try {
            tracker.open(true);
            return type.cast(tracker.waitForService(0));
        } catch (InterruptedException ex) {
            throw new GlassFishException(ex);
        } finally {
            tracker.close(); // no need to track further
        }
    }

    private void setOSGiProperties(Path instanceRoot) {
        this.properties.setProperty(Constants.FRAMEWORK_BUNDLE_PARENT, Constants.FRAMEWORK_BUNDLE_PARENT_FRAMEWORK);
        this.properties.setProperty(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
        this.properties.setProperty("org.osgi.framework.bootdelegation", "dtds, schemas");
        this.properties.setProperty("org.osgi.framework.storage", instanceRoot + "/osgi-cache/felix");
        this.properties.setProperty(HK2_CACHE_DIR_PROP_NAME, instanceRoot.toString());
    }

    private BundleContext getBundleContext() throws GlassFishException{
        if(this.osgiLauncher == null){
            throw new IllegalStateException("OSGi launcher not initialized");
        }
        BundleContext ctx = this.osgiLauncher.getBundleContext();
        if (ctx == null) {
            throw new GlassFishException("Error while launching the OSGi framework");
        }
        return ctx;
    }

    private void setupOSGiFramework() throws GlassFishException {
        osgiLauncher = new OSGiFrameworkLauncher(this.properties);
        Framework framework;
        try {
            framework = osgiLauncher.launchOSGiFrameWork();
        } catch (Exception ex) { 
            throw new GlassFishException("Error while initializing the OSGi framework",ex);
        }
        BundleProvisioner provisioner;
        try {
            provisioner = new BundleProvisioner(getBundleContext(), new UberProvisionerCustomizer(properties));
        } catch (Exception ex) { 
            throw new GlassFishException("Error while provisioning the OSGi framework",ex);
        }
        provisioner.installBundles();
        provisioner.startBundles();
        try {
            framework.start();
        } catch (BundleException ex) {
            throw new GlassFishException("Error while starting the OSGi framework",ex);
        }
    }

    private void setClassPathProperties(Path instanceRoot) throws GlassFishException {
        try {
            Path classpathDir = instanceRoot.resolve(EXTRA_CLASSPATH_DIR_NAME);
            Files.createDirectories(classpathDir);
            File[] jars = new File[webCompileClasspath.length];
            for(int i=0;i<webCompileClasspath.length;i++){
                jars[i] = extractJar(webCompileClasspath[i], classpathDir);
            }
            StringBuilder classpath = new StringBuilder();
            for(int i=0 ; i <jars.length ; i++){
                classpath.append(jars[i].getAbsolutePath());
                if(i < jars.length -1){
                    classpath.append(":");
                }
            }
            this.properties.setProperty(EXTRA_CLASSPATH_PROP_NAME, classpath.toString());
        } catch (MalformedURLException ex) {
            throw new GlassFishException(ex);
        } catch (IOException ex) {
            throw new GlassFishException(ex);
        }
    }

    private Path createInstanceRoot() throws GlassFishException{
        Path instanceRoot;
        try {
            instanceRoot = Files.createTempDirectory("uber-glassfish-instanceRoot");
        } catch (IOException ex){
            throw new GlassFishException("Error while creating instanceRoot", ex);
        }
        return instanceRoot;
    }

    private Path generateDomain(Path instanceRoot) throws GlassFishException {
        try {
            this.properties.setProperty(INSTANCE_ROOT_PROP_NAME, instanceRoot.toFile().getAbsolutePath());
            this.properties.setProperty(INSTANCE_ROOT_URI_PROP_NAME, instanceRoot.toUri().toString());

            Path docRoot = instanceRoot.resolve(DOCROOT_DIR_NAME);
            Path configRoot = instanceRoot.resolve(CONFIGROOT_DIR_NAME);
            Files.createDirectories(instanceRoot);
            Files.createDirectories(docRoot);
            Files.createDirectories(configRoot);

            extractJarEntries(domainJar, NUCLEUS_DOMAIN_ENTRIES, configRoot);
            extractJarEntries(securityJar, SECURITY_DOMAIN_ENTRIES, configRoot);
            extractJarEntry(kernelJar, DOMAIN_XML_ENTRY, configRoot);
            extractJarEntry(webEmbedJar, DEFAULT_WEB_XML_ENTRY, configRoot);

            File logProps = extractJarEntry(domainJar, LOGGING_PROPERTIES_ENTRY, configRoot);
            System.setProperty("java.util.logging.config.file", logProps.getAbsolutePath());
        } catch (IOException ex) {
            throw new GlassFishException("Error while generating the domain", ex);
        } catch (Exception ex) {
            throw new GlassFishException(ex);
        }
        return instanceRoot;
    }

    private static class UberProvisionerCustomizer extends BundleProvisioner.DefaultCustomizer {

        public UberProvisionerCustomizer(Properties config) throws Exception {
            super(config);
        }

        @Override
        protected boolean isDirectory(URI uri) {
            if (FILE_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                try {
                    return new File(uri).isDirectory();
                } catch (Exception e) {
                    return false;
                }
            } else if (JAR_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                return uri.toString().endsWith("/");
            } else {
                throw new IllegalArgumentException("Unkown scheme:" + uri.getScheme());
            }
        }

        @Override
        protected List<? extends URI> listJarFiles(URI uri) {
            final List<URI> jarURIs = new ArrayList<>();
            if (FILE_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                new File(uri).listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File pathname) {
                        if (pathname.getName().endsWith(".jar") && !pathname.isDirectory()) {
                            jarURIs.add(pathname.toURI());
                            return true;
                        }
                        return false;
                    }
                });
            } else if (JAR_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                try {
                    URLConnection con = uri.toURL().openConnection();
                    if (con instanceof JarURLConnection) {
                        JarURLConnection jarCon = (JarURLConnection) con;
                        URI jarUri = jarCon.getJarFileURL().toURI();
                        String aDirectory = uri.toString()
                                .substring("jar:".length())
                                .substring(jarUri.toString().length() + "!/".length());
                        JarFile jarFile = ((JarURLConnection) con).getJarFile();
                        final Enumeration<JarEntry> entries = jarFile.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            if (!entry.isDirectory() && entry.getName().startsWith(aDirectory)) {
                                URI entryURI = new URI("jar:" + jarUri.toString() + "!/" + entry.getName());
                                jarURIs.add(entryURI);
                            }
                        }
                    }
                } catch (FileNotFoundException ex) {
                } catch (URISyntaxException | IOException ex) {
                    throw new RuntimeException(ex);
                }
            } else {
                throw new IllegalArgumentException("Unkown scheme: " + uri.getScheme());
            }
            return jarURIs;
        }
    }
}