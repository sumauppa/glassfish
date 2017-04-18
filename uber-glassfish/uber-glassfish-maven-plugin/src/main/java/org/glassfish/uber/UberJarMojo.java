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
package org.glassfish.uber;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.components.io.fileselectors.FileInfo;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.util.FileUtils;
import org.glassfish.uber.bootstrap.Runner;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.glassfish.uber.descriptors.Pkg;
import org.glassfish.uber.descriptors.PkgMetadata;
import org.glassfish.uber.descriptors.PkgResolver;

@Mojo(
        name = "uber-jar", 
        defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
        requiresDependencyResolution = ResolutionScope.RUNTIME)
public class UberJarMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter( defaultValue = "${session}", required = true, readonly = true )
    protected MavenSession session;

    @Parameter(required = true, readonly = true)
    private String[] packages;

    @Parameter(readonly = true)
    private String mainClass;

    @Parameter( defaultValue = "${project.build.directory}", required = true)
    private File buildDirectory;

    @Parameter(defaultValue ="${project.build.directory}/stage", readonly = true)
    private File stageDir;

    @Parameter(defaultValue ="${project.build.outputDirectory}", readonly = true)
    private File classesDir;

    @Parameter(defaultValue = "${project.build.finalName}", required = true)
    private String finalName;

    @Parameter( property = "glassfish.uber.jar.classifier", defaultValue = "")
    private String classifier;

    @Parameter( property = "glassfish.uber.jar.skip")
    private boolean skip;

    @Component( role = Archiver.class, hint = "jar")
    private JarArchiver jarArchiver;

    @Component( role = UnArchiver.class, hint = "zip")
    private ZipUnArchiver zipUnArchiver;

    @Parameter(defaultValue = "${basedir}/src/main/webapp", required = true)
    private File warSourceDirectory;

    @Component
    private MavenProjectHelper projectHelper;

    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepos;

    @Parameter(defaultValue = "${plugin.artifacts}")
    private java.util.List<org.apache.maven.artifact.Artifact> pluginDependencies;

    private static final String UBER_BOOTSTRAP_GROUPID = "org.glassfish.uber";
    private static final String UBER_BOOTSTRAP_ARTIFACTID = "uber-bootstrap";
    private static final String FEATURESET_GROUPID = "org.glassfish.main.featuresets";
    private static final String FEATURESET_ARTIFACTID = "glassfish";
//    private static final String FEATURESET_ARTIFACTID = "embedded-full";
    private static final String GLASSFISH_PACKAGER_GROUPID = "org.glassfish.main.packager";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if (skip) {
            getLog().info("Skipping uber jar generation.");
            return;
        }

        getLog().debug(" ======= UberJarMojo settings =======");
        getLog().debug("outputDirectory[" + buildDirectory + "]");
        getLog().debug("finalName[" + finalName + "]");
        getLog().debug("classifier[" + classifier + "]");
        if(getLog().isDebugEnabled()){
            getLog().debug("packages ["+arrayAsString(packages)+"]");
        }

        // resolve maven dependencies for all glassfish pkgs
        // for each pkg, read manifest of all jars to parse OSGi metadata
        List<Pkg> allPkgs;
        try {
            allPkgs = getAllGlassFishPackages();
        } catch (DependencyResolutionException | DependencyCollectionException ex) {
            throw new MojoExecutionException("Error retrieve GlassFish packages", ex);
        } catch (IOException ex) {
            throw new MojoExecutionException("Error reading package metadata", ex);
        }

        // resolve the set of pkg required for the user app
        // unpack the resolved pkgs into the stage dir
        try {
            resolveAndUnpackPackages(allPkgs);
        } catch(PkgResolver.PkgResolverException ex){
            throw new MojoExecutionException("Error resolving GlassFish packages", ex);
        }

        // resolve maven dependency of uber-bootstrap
        // and unpack from it the bootstrap classes into the stage dir
        stageBootstrapClasses();

        // stages the webapp files
        // resources from src/main/webapp
        // classes files into WEB-INF/classes, expect user main class, if any
        // returns the virtualWarUri that enumerates all the webapp files inside the uberjar.
        String virturlWarUri;
        try {
            virturlWarUri = stageWebapp();
        } catch (IOException ex) {
            throw new MojoExecutionException("Error staging webapp files",ex);
        }

        // stages the user project dependencies
        // i.e, everything that is not <scope>provided</scope> or <optional>true</optional>
        // each artifact is staged under lib/
        // they will be loaded by the uberjar classloader
        // We could put them under WEB-INF/lib, too
        try {
            stageProjectRuntimeDependencies();
        } catch (IOException ex) {
            throw new MojoExecutionException("Error copying runtime dependencies",ex);
        }

        // jar the stage dir
        File jarFile = getJarFile(buildDirectory, finalName, classifier);
        try {
            MavenArchiver archiver = new MavenArchiver();
            archiver.setArchiver(jarArchiver);
            archiver.setOutputFile(jarFile);
            archiver.getArchiver().addDirectory(stageDir);
            archiver.createArchive(session, project, getMavenArchiveConfiguration(virturlWarUri));
        } catch (ManifestException | IOException | DependencyResolutionRequiredException ex) {
            throw new MojoExecutionException("Error assembling uber jar",ex);
        }

        if (classifier != null) {
            projectHelper.attachArtifact(project, "jar", classifier, jarFile);
        } else {
            project.getArtifact().setFile(jarFile);
        }
    }

    private void resolveAndUnpackPackages(List<Pkg> allPkgs) throws PkgResolver.PkgResolverException {
        PkgResolver pkgResolver = new PkgResolver(allPkgs);
        pkgResolver.resolve();
        Set<Pkg> resolvedPkgs = pkgResolver.resolvePackages(packages);
        getLog().info("Extracting resolved packages");
        getLog().debug("Resolved packages: ");
        stageDir.mkdirs();
        for (Pkg pkg : resolvedPkgs) {
            getLog().debug(pkg.toString());
            getLog().info("Extracting " + pkg.getName());
            zipUnArchiver.setSourceFile(pkg.getFile());
            zipUnArchiver.setFileSelectors(new FileSelector[]{
                new FileSelector() {
                    @Override
                    public boolean isSelected(FileInfo fi) throws IOException {
                        if (!((fi.getName().startsWith("bin")
                                || fi.getName().startsWith("glassfish/bin")
                                || fi.getName().startsWith("pkg_proto.py")
                                || fi.getName().startsWith("META-INF")))) {
                            getLog().debug("Including " + fi.getName());
                            return true;
                        } else {
                            getLog().debug("Excluding " + fi.getName());
                            return false;
                        }
                    }
                }});
            zipUnArchiver.setDestDirectory(stageDir);
            zipUnArchiver.setDestFile(null);
            zipUnArchiver.setOverwrite(true);
            zipUnArchiver.extract();
        }
    }

    private List<Pkg> getAllGlassFishPackages() throws DependencyResolutionException, DependencyCollectionException, IOException {
        // resolve pkg zip files
        List<Entry<File,String>> packagesFiles = getAllGlassFishPackagesFile();

        // read pkg metadata
        List<Pkg> pkgs = new ArrayList<>();
        for (Entry<File,String> entry : packagesFiles) {
            PkgMetadata metadata = new PkgMetadata(entry.getKey(),entry.getValue());
            pkgs.add(new Pkg(metadata, entry.getKey()));
        }
        return pkgs;
    }

    private List<Entry<File,String>> getAllGlassFishPackagesFile() throws DependencyResolutionException, DependencyCollectionException {
        DefaultArtifact featureset = new DefaultArtifact(
                FEATURESET_GROUPID,
                FEATURESET_ARTIFACTID,
                "pom",
                getEmbeddedFeatureSetVersion());
        Dependency dependency = new Dependency(featureset, "compile");

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(dependency);
        collectRequest.setRepositories(remoteRepos);
        DependencyNode node;

        node = repoSystem.collectDependencies(repoSession, collectRequest).getRoot();
        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setRoot(node);
        dependencyRequest.setFilter(new DependencyFilter() {
            @Override
            public boolean accept(DependencyNode node, List<DependencyNode> parents) {
                Dependency dep = node.getDependency();
                if (dep != null) {
                    return dep.getArtifact().getGroupId().equals(GLASSFISH_PACKAGER_GROUPID)
                            && dep.getArtifact().getExtension().equals("zip");
                }
                return false;
            }
        });
        repoSystem.resolveDependencies(repoSession, dependencyRequest);
        PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
        node.accept(nlg);
        List<Entry<File,String>> packagesFiles = new ArrayList<>();
        for (Artifact a : nlg.getArtifacts(false)) {
            packagesFiles.add(new SimpleEntry<File,String>(a.getFile(),a.getArtifactId()));
        }
        return packagesFiles;
    }

    private String getEmbeddedFeatureSetVersion() {
        for (org.apache.maven.artifact.Artifact pluginDependency : pluginDependencies) {
            if (pluginDependency.getGroupId().equals(FEATURESET_GROUPID)
                    && pluginDependency.getArtifactId().equals(FEATURESET_ARTIFACTID)) {
                return pluginDependency.getVersion();
            }
        }
        throw new IllegalStateException("Unable to find plugin dependency "
                + FEATURESET_GROUPID + ":" + FEATURESET_ARTIFACTID);
    }

    private File getUberBootstrapJarFile(){
        for (org.apache.maven.artifact.Artifact pluginDependency : pluginDependencies) {
            if (pluginDependency.getGroupId().equals(UBER_BOOTSTRAP_GROUPID)
                    && pluginDependency.getArtifactId().equals(UBER_BOOTSTRAP_ARTIFACTID)) {
                return pluginDependency.getFile();
            }
        }
        throw new IllegalStateException("Unable to find plugin dependency "
                + UBER_BOOTSTRAP_GROUPID + ":" + UBER_BOOTSTRAP_ARTIFACTID);
    }

    private static void copy(File source, File relativeParent, File targetDirectory, Set<File> collector) throws IOException {
        Path fileRelativePath = relativeParent.toPath().relativize(source.toPath());
        Path targetPath = targetDirectory.toPath().resolve(fileRelativePath);
        if(source.isDirectory()){
            Files.createDirectories(targetPath);
            for(File file : source.listFiles()){
                copy(file, relativeParent, targetDirectory, collector);
            }
        } else {
            Files.copy(source.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            collector.add(targetPath.toFile());
        }
    }

    private String stageWebapp() throws IOException {

        Set<File> allFiles = new HashSet<>();

        // copy files from src/main/webapp to target/stage
        for(File file : warSourceDirectory.listFiles()){
            copy(file, warSourceDirectory, stageDir,allFiles);
        }

        // copy files from target/classes to target/stage/WEB-INF/classes
        File webInfDir = new File(stageDir,"WEB-INF");
        File webInfClassesDir = new File(webInfDir,"classes");

        // class file path to the main class
        String mainClassFilepath;
        if(mainClass != null && !mainClass.isEmpty()){
            mainClassFilepath = mainClass.replace(".", "/") + ".class";
        } else {
            mainClassFilepath = null;
        }

        for(File file : classesDir.listFiles()){
            // stage user supplied main class to target/stage
            if(mainClassFilepath != null && file.getPath().endsWith(mainClassFilepath)){
                copy(file, classesDir, stageDir, allFiles);
            } else {
                // stage under target/stage/WEB-INF/classes
                copy(file, classesDir, webInfClassesDir, allFiles);
            }
        }

        // build the virtualwar uri
        StringBuilder sb = new StringBuilder("virtualwar:");
        Iterator<File> warSourcesIt = allFiles.iterator();
        while(warSourcesIt.hasNext()){
            File file = warSourcesIt.next();
            String relativeName = file.getAbsolutePath().substring(
                    stageDir.getAbsolutePath().length()+1);
            sb.append(relativeName);
            if(warSourcesIt.hasNext()){
                sb.append(",");
            }
        }
        return sb.toString();
    }

    private void stageBootstrapClasses() {
        zipUnArchiver.setSourceFile(getUberBootstrapJarFile());
        zipUnArchiver.setDestDirectory(stageDir);
        zipUnArchiver.setDestFile(null);
        zipUnArchiver.setOverwrite(true);
        zipUnArchiver.setFileSelectors(new FileSelector[]{
            new FileSelector() {
                @Override
                public boolean isSelected(FileInfo fi) throws IOException {
                    return fi.isFile() && fi.getName().endsWith(".class");
                }
            }});
        zipUnArchiver.extract();
    }

    private void stageProjectRuntimeDependencies() throws IOException {
        @SuppressWarnings("unchecked")
        Set<org.apache.maven.artifact.Artifact> artifacts = project.getArtifacts();
        for (org.apache.maven.artifact.Artifact artifact : artifacts) {

            ScopeArtifactFilter filter = new ScopeArtifactFilter(org.apache.maven.artifact.Artifact.SCOPE_RUNTIME);
            if (!artifact.isOptional() && filter.include(artifact)
                    && artifact.getArtifactHandler().isAddedToClasspath()) {
                getLog().info("Copying artifact[" + artifact.getGroupId() + ", " + artifact.getId() + ", "
                        + artifact.getScope() + "]");
                FileUtils.copyFileToDirectory(artifact.getFile(), new File(stageDir, "lib"));
            }
        }
    }

    private MavenArchiveConfiguration getMavenArchiveConfiguration(String virturlWarUri) {
        MavenArchiveConfiguration archive = new MavenArchiveConfiguration();
        if(mainClass != null){
            archive.addManifestEntry(Runner.UBER_MAIN_CLASS, mainClass);
        } else {
            archive.addManifestEntry(Runner.VIRTUAL_WAR_URL, virturlWarUri);
        }
        archive.addManifestEntry("Main-Class", Runner.class.getName());
        archive.setCompress(true);
        archive.setRecompressAddedZips(true);
        archive.setAddMavenDescriptor(false);
        return archive;
    }

    private static File getJarFile(File basedir, String finalName, String classifier) {
        if (classifier == null) {
            classifier = "";
        } else if (classifier.trim().length() > 0 && !classifier.startsWith("-")) {
            classifier = "-" + classifier;
        }
        return new File(basedir, finalName + classifier + ".jar");
    }

    private static String arrayAsString(Object[] array) {
        StringBuilder sb = new StringBuilder();
        for(int i=0; i<array.length;i++){
            sb.append(array[i].toString());
            if (i < array.length -1){
                sb.append(", ");
            }
        }
        return sb.toString();
    }
}