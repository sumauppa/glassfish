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

import aQute.libg.version.Version;
import aQute.libg.version.VersionRange;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.xml.bind.annotation.XmlAttribute;

/**
 *
 * @author rgrecour
 */
public class OSGiPackage implements Comparable<OSGiPackage>{

    @XmlAttribute
    private String javaPackageName;

    @XmlAttribute
    private String version;

    @XmlAttribute
    private String module;

    @XmlAttribute
    private String resolution;

    @XmlAttribute
    private Boolean fragment;

    public OSGiPackage() {
    }

    public OSGiPackage(String module, boolean fragment, String packageSpec) {
        this.module = module;
        this.fragment = fragment;
        String[] tokens = packageSpec.split(";");
        this.javaPackageName = tokens[0].trim();
        for (int i = 1; i < tokens.length; i++) {
            processAttribute(tokens[i].trim());
        }
    }

    public String getModule() {
        return module;
    }

    public String getJavaPackageName() {
        return javaPackageName;
    }

    public String getVersion() {
        return version;
    }

    public String getResolution() {
        return resolution;
    }

    boolean isFragment(){
        return fragment;
    }

    private void processAttribute(String token) {
        int start = token.indexOf('"') + 1;
        int end = token.indexOf('"', start);
        if (start < 0 || end < 0) {
            start = token.indexOf('=') + 1;
            end = token.length();
        }
        if (token.startsWith("version")) {
            version = token.substring(start, end);
        } else if(token.startsWith("resolution")){
            resolution = token.substring(start, end);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final OSGiPackage other = (OSGiPackage) obj;
        if (!Objects.equals(this.javaPackageName, other.javaPackageName)) {
            return false;
        }
        if (!Objects.equals(this.version, other.version)) {
            return false;
        }
        return Objects.equals(this.module, other.module);
    }

    /**
     * Assuming the current object is an export, match the provided import.
     * The current object is expected not to use version range.
     * The provided object may or may not use version range.
     * @param _import
     * @return 
     */
    public boolean matchImport(OSGiPackage _import){
        if (_import == null) {
            return false;
        }
        if (!javaPackageName.equals(_import.javaPackageName)){
            return false;
        }

        if(version == null && _import.version == null){
            return true;
        }

        // wildcard
        if(version == null || _import.version == null){
            return false;
        }

        VersionRange _range = new VersionRange(_import.version);
        if(_range.isRange()){
            Version _version = new Version(version);
            return _range.includes(_version);
        } else {
            return version.equals(_import.version);
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + (this.javaPackageName != null ? this.javaPackageName.hashCode() : 0);
        hash = 53 * hash + (this.version != null ? this.version.hashCode() : 0);
        return hash;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append(javaPackageName)
                .append(";version=")
                .append(version)
                .toString();
    }

    public static List<OSGiPackage> parse(String module, boolean fragment, String header) {
        List<OSGiPackage> packageSpecs = new ArrayList<>();
        int i = 0;
        while (i < header.length()) {
            int lastIndex = i;
            i = getNextPackageTerminator(header, lastIndex);
            OSGiPackage packageSpec = new OSGiPackage(module,fragment,header.substring(lastIndex, i++));
            if (packageSpec.javaPackageName.equals("*")) {
                continue;
            }
            packageSpecs.add(packageSpec);
        }
        return packageSpecs;
    }

    private static int getNextPackageTerminator(String header, int start) {
        boolean quotes = false;
        for (int i = start; i < header.length(); i++) {
            if (header.charAt(i) == '"') {
                quotes = !quotes;
            }
            if (!quotes && header.charAt(i) == ',') {
                return i;
            }
        }
        return header.length();
    }

    @Override
    public int compareTo(OSGiPackage o) {
        int ret = o.module.compareTo(module);
        if(ret == 0){
            return o.javaPackageName.compareTo(javaPackageName);
        }
        return ret;
    }
}
