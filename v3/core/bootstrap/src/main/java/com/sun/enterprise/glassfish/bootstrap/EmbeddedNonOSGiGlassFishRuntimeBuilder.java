/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.glassfish.bootstrap;

import com.sun.enterprise.module.ModulesRegistry;
import com.sun.enterprise.module.bootstrap.Main;
import com.sun.enterprise.module.common_impl.AbstractFactory;
import org.glassfish.simpleglassfishapi.Constants;
import org.glassfish.simpleglassfishapi.GlassFishRuntime;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This {@link GlassFishRuntime.RuntimeBuilder} is responsible for setting up a
 * {@link GlassFishRuntime} when the user wants to launch GlassFish in
 * Static Mode wherein the user program neither uses uber jar, nor specifies the
 * installRoot as the existing regular installation. So, the case will be:
 * <p/>
 * Server.Buider.build() using glassfish-embedded-static-shell.jar or maven repository glassfish.jar
 * in the classpath, not specifying regular installroot and instanceroot.
 *
 * @author bhavanishankar@dev.java.net
 */

public class EmbeddedNonOSGiGlassFishRuntimeBuilder implements GlassFishRuntime.RuntimeBuilder {

    private static Logger logger = Util.getLogger();

    public boolean handles(Properties properties) {
        boolean handles = false;
        if (org.glassfish.simpleglassfishapi.Constants.Platform.Static.toString().equals(
                properties.getProperty(Constants.PLATFORM_PROPERTY_KEY))) {
            String installRoot = properties.getProperty(Constants.INSTALL_ROOT_PROP_NAME);
            String instanceRoot = properties.getProperty(Constants.INSTANCE_ROOT_PROP_NAME);
            try {
                ASMainHelper.verifyDomainRoot(new File(instanceRoot));
                // instanceRoot is valid, let us check if installRoot is valid.
                if(!isValidInstallRoot(installRoot)) {
                    // installRoot is not pointing to existing installation, so we handle.
                    handles = true;
                }
            } catch (Exception ex) {
                // instanceRoot is not pointing to existing installation, so we handle.
                handles = true;
            }
        }
        return handles;
    }

    public GlassFishRuntime build(Properties properties) throws Exception {
        // Create installRoot & instanceRoot directories.
        String installRoot = properties.getProperty(Constants.INSTALL_ROOT_PROP_NAME);
        if (installRoot == null) {
            installRoot = createDefaultInstallRoot();
            properties.setProperty(Constants.INSTALL_ROOT_PROP_NAME, installRoot);
            properties.setProperty(Constants.INSTALL_ROOT_URI_PROP_NAME,
                    new File(installRoot).toURI().toString());
        }
        provisionInstallRoot(new File(installRoot));

        String instanceRoot = properties.getProperty(Constants.INSTANCE_ROOT_PROP_NAME);
        if (instanceRoot == null) {
            instanceRoot = createDefaultInstanceRoot(installRoot);
            properties.setProperty(Constants.INSTANCE_ROOT_PROP_NAME, instanceRoot);
            properties.setProperty(Constants.INSTANCE_ROOT_URI_PROP_NAME,
                    new File(instanceRoot).toURI().toString());
        }
        provisionInstanceRoot(new File(instanceRoot), properties);

        // Copy the configFile to the instanceRoot/config
        copyConfigFile(properties.getProperty(Constants.CONFIG_FILE_URI_PROP_NAME), instanceRoot);

        /* Step 1. Build the classloader. */
        ClassLoader cl = getClass().getClassLoader(); // nothing to build, use the parent classloader.

        // Step 2. Setup the module subsystem.
        Main main = new EmbeddedMain();
        SingleHK2Factory.initialize(cl);
        ModulesRegistry modulesRegistry = AbstractFactory.getInstance().createModulesRegistry();
        modulesRegistry.setParentClassLoader(cl);

        // Step 3. Create NonOSGIGlassFishRuntime
        GlassFishRuntime glassFishRuntime = new NonOSGiGlassFishRuntime(main);

        logger.logp(Level.INFO, getClass().getName(), "build", "Created GlassFishRuntime {0} " +
                "with Bootstrap Properties {1}", new Object[]{glassFishRuntime, properties});
        return glassFishRuntime;
    }

    public void destroy() throws Exception {
        // TODO : do any clean up
    }

    private boolean isValidInstallRoot(File installRoot) {
        return installRoot == null ? false : isValidInstallRoot(installRoot.getAbsolutePath());
    }

    private boolean isValidInstallRoot(String installRootPath) {
        if (installRootPath == null || !new File(installRootPath).exists()) {
            return false;
        }
        if (!new File(installRootPath, "modules").exists()) {
            return false;
        }
        if (!new File(installRootPath, "lib/dtds").exists()) {
            return false;
        }
        return true;
    }

    private String createDefaultInstallRoot() throws Exception {
        String tmpDir = System.getProperty("glassfish.embedded.tmpdir");
        if (tmpDir == null) {
            tmpDir = System.getProperty("user.dir");
        }
        File installRoot = File.createTempFile("gfembed", "tmp", new File(tmpDir));
        installRoot.delete();
        installRoot.mkdir(); // convert the file into a directory.
        return installRoot.getAbsolutePath();
    }

    private void provisionInstallRoot(File installRoot) {
        // If the regular installation location can be discovered,
        // discover it and copy the necessary configuration files.
        File discoveredInstallRoot = ASMainHelper.findInstallRoot();
        if (isValidInstallRoot(discoveredInstallRoot)) {
            copy(discoveredInstallRoot, installRoot, "lib/dtds", "lib/schemas");
        }
    }

    private String createDefaultInstanceRoot(String installRoot) throws Exception {
        // File instanceRoot = new File(installRoot, "domains/domain1");
        // instanceRoot.mkdirs();
        // Ideally instanceRoot should be different from installRoot, but the existing Server.Builder uses the same for both.
        File instanceRoot = new File(installRoot);
        return instanceRoot.getAbsolutePath();
    }

    private void provisionInstanceRoot(File instanceRoot, Properties props) {
        new File(instanceRoot, "config").mkdirs();
        new File(instanceRoot, "docroot").mkdirs();
        try {
            // If the regular installation location can be discovered,
            // discover it and copy the necessary configuration files.
            File discoveredInstallRoot = ASMainHelper.findInstallRoot();
            File discoveredInstanceRoot = ASMainHelper.findInstanceRoot(discoveredInstallRoot, props);
            copy(discoveredInstanceRoot, instanceRoot, "config", "docroot");
        } catch (Exception ex) {
            logger.warning(ex.getMessage());
        }
    }

    // Copy the subDirs under srcDir to dstDir
    private void copy(final File srcDir, final File dstDir, final String... subDirs) {
        srcDir.listFiles(new FileFilter() {
            public boolean accept(File path) {
                if (path.isDirectory()) {
                    path.listFiles(this);
                } else {
                    for (String subDir : subDirs) {
                        String srcPath = new File(srcDir, subDir).getPath();
                        if (path.getPath().startsWith(srcPath)) {
                            File dstFile = new File(dstDir, path.getPath().substring(srcDir.getPath().length()));
                            if (!dstFile.exists()) {
                                dstFile.getParentFile().mkdirs();
                                try {
                                    Util.copyFile(path, dstFile);
//                                    logger.info("Copied " + path + " to " + dstFile);
                                } catch (Exception ex) {
                                    logger.warning(ex.getMessage());
                                }
                            }
                        }
                    }
                }
                return false;
            }
        });
    }

    private void copyConfigFile(String configFileURI, String instanceRoot) throws Exception {
        if (configFileURI != null && instanceRoot != null) {
            URI configFile = URI.create(configFileURI);
            InputStream stream = configFile.toURL().openConnection().getInputStream();
            File domainXml = new File(instanceRoot, "config/domain.xml");
            System.out.println("domainXML uri = " + configFileURI + ", size = " + stream.available());
            if(!domainXml.toURI().equals(configFile)) {
            Util.copy(stream, new FileOutputStream(domainXml), stream.available());
                System.out.println("Created " + domainXml);
            } else {
                System.out.println("Skipped creation of " + domainXml);
            }

        }
    }

}