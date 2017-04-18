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

import static com.sun.enterprise.glassfish.bootstrap.Constants.INSTALL_ROOT_PROP_NAME;
import static com.sun.enterprise.glassfish.bootstrap.Constants.INSTALL_ROOT_URI_PROP_NAME;
import static com.sun.enterprise.glassfish.bootstrap.Constants.INSTANCE_ROOT_PROP_NAME;
import static com.sun.enterprise.glassfish.bootstrap.Constants.INSTANCE_ROOT_URI_PROP_NAME;
import static com.sun.enterprise.glassfish.bootstrap.osgi.UberJarGlassFishRuntimeBuilder.EXTRA_CLASSPATH_PROP_NAME;
import com.sun.enterprise.module.ModulesRegistry;
import com.sun.enterprise.module.bootstrap.BootException;
import com.sun.enterprise.module.bootstrap.Main;
import com.sun.enterprise.module.bootstrap.ModuleStartup;
import com.sun.enterprise.module.bootstrap.StartupContext;
import com.sun.enterprise.module.common_impl.AbstractModuleDefinition;
import java.io.File;
import java.net.URI;
import org.glassfish.embeddable.GlassFish;
import org.glassfish.embeddable.GlassFishException;
import org.glassfish.embeddable.GlassFishProperties;
import org.glassfish.hk2.api.ServiceLocator;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

/**
 *
 * @author rgrecour
 */
public class UberJarGlassFishRuntime extends EmbeddedOSGiGlassFishRuntime {

    private final BundleContext context;

    public UberJarGlassFishRuntime(BundleContext context) {
        super(context);
        this.context = context;
    }

    private void setProp(GlassFishProperties gfProps, String prop) {
        String value = context.getProperty(prop);
        if (value != null) {
            gfProps.getProperties().setProperty(prop, value);
        }
    }

    private void setProperties(GlassFishProperties props) {
        setProp(props, INSTALL_ROOT_PROP_NAME);
        setProp(props, INSTALL_ROOT_URI_PROP_NAME);
        setProp(props, INSTANCE_ROOT_PROP_NAME);
        setProp(props, INSTANCE_ROOT_URI_PROP_NAME);
    }

    private <T> T getService(Class<T> type) throws GlassFishException {
        ServiceTracker tracker = new ServiceTracker(this.context, type.getName(), null);
        try {
            tracker.open(true);
            return type.cast(tracker.waitForService(0));
        } catch (InterruptedException ex) {
            throw new GlassFishException(ex);
        } finally {
            tracker.close(); // no need to track further
        }
    }

    private <T> T getServiceByRef(Class<T> type) throws GlassFishException {
        return type.cast(context.getService(context.getServiceReference(type.getName())));
    }

    private void setExtraClasspath(ModulesRegistry registry) {
        String classpath = context.getProperty(EXTRA_CLASSPATH_PROP_NAME);
        if (classpath == null) {
            return;
        }
        String tokens[] = classpath.split(":");
        final URI[] _locations = new URI[tokens.length];
        for(int i=0; i<tokens.length;i++){
            _locations[i] = (new File(tokens[i]).toURI());
        }
        registry.add(new AbstractModuleDefinition( "UberJarExtraClasspath") {
            @Override
            public URI[] getLocations() {
                return _locations;
            }
        });
    }

    @Override
    public synchronized GlassFish newGlassFish(GlassFishProperties props) throws GlassFishException {
        setProperties(props);
        try {
            final Main main = getService(Main.class);
            final ModulesRegistry registry = getServiceByRef(ModulesRegistry.class);
            setExtraClasspath(registry);
            final StartupContext startupContext = new StartupContext(props.getProperties());
            ServiceLocator serviceLocator = main.createServiceLocator(registry, startupContext, null, null);
            final ModuleStartup gfKernel = main.findStartupService(registry, serviceLocator, null, startupContext);
            GlassFish glassFish = createGlassFish(gfKernel, serviceLocator, props.getProperties());
            gfs.add(glassFish);
            return glassFish;
        } catch (BootException ex) {
            throw new GlassFishException(ex);
        }
    }
}
