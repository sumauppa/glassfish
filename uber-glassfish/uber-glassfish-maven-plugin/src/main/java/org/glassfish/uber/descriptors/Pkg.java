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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 *
 * @author rgrecour
 */
public class Pkg {

    private final String name;
    private final File file;
    private final PkgMetadata metadata;
    private final HashMap<String,Dependency> dependencies;

    public static class Wiring implements Comparable<Wiring>{
        private final OSGiPackage _import;
        private final OSGiPackage export;

        public Wiring(OSGiPackage _import, OSGiPackage export) {
            this._import = _import;
            this.export = export;
        }

        public OSGiPackage getExport() {
            return export;
        }

        public OSGiPackage getImport() {
            return _import;
        }

        @Override
        public int compareTo(Wiring o) {
            return o._import.compareTo(o.export);
        }
    }

    public static class Dependency {
        private final Pkg pkg;
        private final List<Wiring> wirings;

        public Dependency(Pkg pkg) {
            this.pkg = pkg;
            this.wirings = new ArrayList<>();
        }

        void addWiring(OSGiPackage _import, OSGiPackage export){
            this.wirings.add(new Wiring(_import,export));
        }

        public List<Wiring> getWirings(){
            return wirings;
        }

        public Pkg getPkg(){
            return pkg;
        }
    }

    public Pkg(PkgMetadata metadata, File file) {
        this.metadata = metadata;
        this.name = metadata.name;
        this.file = file;
        this.dependencies = new HashMap<>();
    }

    void addDependency(Pkg pkg, OSGiPackage _import, OSGiPackage export){
        if(pkg == null ){
            return;
        }
        if(_import == null){
            throw new IllegalArgumentException("import is null");
        }
        if(export == null){
            throw new IllegalArgumentException("export is null");
        }
        if(pkg == this){
            throw new IllegalArgumentException("Cannot add "+pkg.getName()+" to itself as dependency");
        }
        Dependency dep = dependencies.get(pkg.getName());
        if(dep == null){
            dep = new Dependency(pkg);
            dependencies.put(pkg.getName(),dep);
        }
        dep.addWiring(_import,export);
    }

    PkgMetadata getMetadata(){
        return metadata;
    }

    public Collection<Dependency> getDependencies(){
        return dependencies.values();
    }

    public String getName(){
        return name;
    }

    public File getFile() {
        return file;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + Objects.hashCode(this.name);
        hash = 47 * hash + Objects.hashCode(this.file);
        hash = 47 * hash + Objects.hashCode(this.metadata);
        hash = 47 * hash + Objects.hashCode(this.dependencies);
        return hash;
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
        final Pkg other = (Pkg) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        return Objects.equals(this.file, other.file);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Pkg {\n")
                .append("   name=").append(name).append("\n");
        Iterator<Dependency> it = dependencies.values().iterator();
        if (it.hasNext()) {
            sb.append("   dependencies=[\n");
            while (it.hasNext()) {
                Dependency dep = it.next();
                sb.append("      ").append(dep.pkg.getName()).append(" {\n");
                Collections.sort(dep.getWirings());
                for (Wiring wiring : dep.getWirings()) {
                    sb.append("         ")
                            .append(wiring.getImport().getModule())
                            .append(" imports ").append(wiring.getImport().getJavaPackageName())
                            .append(" from ").append(wiring.getExport().getModule())
                            .append("\n");
                }
                if (it.hasNext()) {
                    sb.append("      },\n");
                } else {
                    sb.append("      }\n");
                }
            }
            sb.append("   ]\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
