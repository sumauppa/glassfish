/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
package org.glassfish.admin.amx.impl.config;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jvnet.hk2.config.ConfigBean;
import org.jvnet.hk2.config.ConfigBeanProxy;

/**
    A registry of ConfigBeanJMXSupport, for efficiency in execution time and scalability
    for large numbers of MBeans which share the same underlying type of @Configured.
 */
final class ConfigBeanJMXSupportRegistry
{
    private ConfigBeanJMXSupportRegistry() {}
    
    /**
        Map an interface to its helper.
     */
    private static final ConcurrentMap<Class<? extends ConfigBeanProxy>,ConfigBeanJMXSupport>  INSTANCES =
        new ConcurrentHashMap<Class<? extends ConfigBeanProxy>,ConfigBeanJMXSupport>();
    
    /**
        Return null if no instance yet; createInstance() must be called to create one.
     */
        public static ConfigBeanJMXSupport
    getInstance( final Class<? extends ConfigBeanProxy> intf )
    {
        ConfigBeanJMXSupport helper = INSTANCES.get(intf);
        if ( helper == null )
        {
            // don't cache it, we can't be sure about its key
            helper = new ConfigBeanJMXSupport(intf, null);
        }
        return helper;
    }
    
        public static ConfigBeanJMXSupport
    getInstance( final ConfigBean configBean )
    {
        ConfigBeanJMXSupport helper = INSTANCES.get( configBean.getProxyType() );
        if ( helper == null )
        {
            helper = addInstance(configBean);
        }
        return helper;
    }
    
        private static synchronized ConfigBeanJMXSupport
    addInstance( final ConfigBean configBean )
    {
        final Class<? extends ConfigBeanProxy> intf = configBean.getProxyType();
        ConfigBeanJMXSupport helper = INSTANCES.get(intf);
        if ( helper == null )
        {
            helper = new ConfigBeanJMXSupport(configBean);
            INSTANCES.put( intf, helper );
        }
        return helper;
    }
 }








