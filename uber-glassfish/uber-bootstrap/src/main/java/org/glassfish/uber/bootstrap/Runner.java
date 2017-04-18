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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import org.glassfish.uber.bootstrap.url.VirtualWarURLHandlerFactory;
import org.glassfish.uber.bootstrap.BootLoader.JarFilter;
import org.glassfish.uber.bootstrap.BootLoader.ResourceTranslator;
import static org.glassfish.uber.bootstrap.Utils.getManifestAttributes;
import static org.glassfish.uber.bootstrap.Utils.getRepoUri;
import static org.glassfish.uber.bootstrap.Utils.isDirFileName;
import static org.glassfish.uber.bootstrap.Utils.isJarFileName;

/**
 * The initial Main-Class.
 * Setup the BootLoader and invokes the application Main-Class.
 *
 * @author rgrecour
 */
public class Runner {

    public static final String UBER_MAIN_CLASS = "Uber-Main-Class";
    public static final String VIRTUAL_WAR_URL = "Uber-VirtualWar-Url";
    private static final String MAIN_METHOD_NAME = "main";

    // bootstrap modules
    // that must be provided by our custom classloader
    private static final List<String> BOOTSTRAP_CLASSPATH = Arrays.asList(
            "lib/",
            "modules/simple-glassfish-api.jar",
            "modules/glassfish.jar",
            "felix.jar");

    // the filter used to create the custom classloader
    private static class BootStrapFilter implements JarFilter {
        @Override
        public boolean isIncluded(String name) {
            if (name == null || name.isEmpty()) {
                return true;
            }
            for (String entry : BOOTSTRAP_CLASSPATH) {
                if ((isDirFileName(name) && name.startsWith(entry))
                        || (isJarFileName(name) && name.endsWith(entry))) {
                    return true;
                }
            }
            return false;
        }
    }
    private static final BootStrapFilter BOOTSTRAP_FILTER = new BootStrapFilter();

    // a translator to delegate certain resources from /glassfish
    private static final class CustomResourceTranslator implements ResourceTranslator {

        private final URI repoURi;
        public CustomResourceTranslator(URI repoUri) {
            this.repoURi = repoUri;
        }

        @Override
        public boolean isTranslatedResource(String name) {
            return name != null && (name.startsWith("schemas/") || name.startsWith("dtds/"));
        }

        @Override
        public URL translateResource(String name) {
            try {
                return URI.create(repoURi.toString()+"glassfish4/glassfish/lib/"+name).toURL();
            } catch (MalformedURLException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private final BootLoader bootLoader;
    private final ResourceTranslator resourceTranslator;
    private final URI repoUri;
    private final String mainClass;
    private final String[] mainArgs;

    public Runner(String[] args) {
        // add support for virtualwar: URI
        URL.setURLStreamHandlerFactory(new VirtualWarURLHandlerFactory());

        // get repoUri
        String rootUrl = ((URLClassLoader) Runner.class.getClassLoader()).getURLs()[0].toString();
        this.repoUri = getRepoUri(rootUrl);
        this.resourceTranslator = new CustomResourceTranslator(repoUri);
        this.bootLoader = new BootLoader(
                ((URLClassLoader) ClassLoader.getSystemClassLoader()).getURLs(),
                Thread.currentThread().getContextClassLoader(),
                repoUri,
                BOOTSTRAP_FILTER,
                resourceTranslator
        );
        Attributes manifestAttrs = getManifestAttributes(repoUri);
        String _mainClass = manifestAttrs.getValue(UBER_MAIN_CLASS);
        String[] _mainArgs;
        if(_mainClass == null){
            _mainClass = Main.class.getName();
            _mainArgs = new String[] { manifestAttrs.getValue(VIRTUAL_WAR_URL) };
        } else {
            _mainArgs = new String[0];
        }
        this.mainClass = _mainClass;
        this.mainArgs = _mainArgs;
    }

    public void run() throws Exception{
        // get Main-Class
        Class clazz = bootLoader.loadClass(mainClass);

        // get main method
        Method method = clazz.getMethod(MAIN_METHOD_NAME, new Class[]{mainArgs.getClass()});
        method.setAccessible(true);
        int mods = method.getModifiers();
        if (method.getReturnType() != void.class || !Modifier.isStatic(mods) || !Modifier.isPublic(mods)) {
            throw new NoSuchMethodException(MAIN_METHOD_NAME);
        }

        // invoke main method
        try {
            method.invoke(null, new Object[]{mainArgs});
        } catch (IllegalAccessException e) {
            // This should not happen, as we have 
            // disabled access checks 
        }
    }

    public static void main(String[] args) throws Exception {
        Runner runner = new Runner(args);
        runner.run();
    }
}
