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

import java.util.AbstractMap.SimpleEntry;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import org.glassfish.uber.descriptors.Pkg.Dependency;

/**
 *
 * @author rgrecour
 */
public class PkgResolver {
    private final List<Pkg> pkgs;
    private boolean resolved;

    public PkgResolver(List<Pkg> pkgs) {
        this.resolved = false;
        this.pkgs = pkgs;
    }

    public void resolve() throws PkgResolverException {
        for(Pkg pkg : pkgs){
            for(OSGiPackage _import : pkg.getMetadata().imports){
                Entry<Pkg,OSGiPackage> match = findPkgWithExport(_import, pkg);
                if(match != null){
                    pkg.addDependency(match.getKey(), _import, match.getValue());
                }
            }
            for(OSGiPackage dynamicImport :pkg.getMetadata().dynamicImports){
                Entry<Pkg,OSGiPackage> match = findPkgWithExport(dynamicImport, pkg);
                if(match != null){
                    pkg.addDependency(match.getKey(), dynamicImport, match.getValue());
                }
            }
        }
        resolved = true;
    }

    private Pkg getPkg(String name) throws PkgResolverException {
        for(Pkg pkg : pkgs){
            if(pkg.getName().equals(name)){
                return pkg;
            }
        }
        throw new PkgResolverException("Unable to find package with name "+name);
    }

    public Set<Pkg> resolvePackages(String[] pkgNames) throws PkgResolverException {
        if(!resolved){
            resolve();
        }
        Set<Pkg> resolvedPkgs = new HashSet<>();
        for (String pkgName : pkgNames) {
            Pkg pkg = getPkg(pkgName);
            resolvedPkgs.add(pkg);
            for(Dependency dep : pkg.getDependencies()){
                resolvedPkgs.add(dep.getPkg());
            }
        }
        return resolvedPkgs;
    }

    private void resolvePackages(Pkg pkg, Set<Pkg> resolvedPkgs) throws PkgResolverException {
        for(Dependency dep : pkg.getDependencies()){
            if(resolvedPkgs.contains(dep.getPkg())){
                continue;
            }
            resolvedPkgs.add(dep.getPkg());
            resolvePackages(dep.getPkg(), resolvedPkgs);
        }
    }

    public static class ExportMatch {
        private final Pkg pkg;
        private final OSGiPackage export;

        public ExportMatch(Pkg pkg, OSGiPackage export) {
            this.pkg = pkg;
            this.export = export;
        }

        public OSGiPackage getExport(){
            return export;
        }

        public Pkg getPkg(){
            return pkg;
        }
    }

    private Entry<Pkg, OSGiPackage> findPkgWithExport(OSGiPackage _import, Pkg self) {
        if("optional".equals(_import.getResolution())){
            return null;
        }
        for (Pkg pkg : pkgs) {
            if (pkg.getName().equals(self.getName())) {
                continue;
            }
            for (OSGiPackage export : pkg.getMetadata().exports) {
                if(export.isFragment()){
                    continue;
                }
                if (export.matchImport(_import)) {
                    return new SimpleEntry<>(pkg, export);
                }
            }
        }
        return null;
    }

    public static class PkgResolverException extends Exception {
        public PkgResolverException(String msg) {
            super(msg);
        }
    }
}
