/*
 * Copyright 2018-present howard_pang@outlook.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.yy.android.gradle.nativedepend.util

import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency

import java.security.DigestInputStream
import java.security.MessageDigest
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType

/** Class to resolve project dependencies */
public final class DependenciesUtils {

    public static generateDependInfo(File dependFile, Set<ResolvedDependency> allDependencies ) {
        if (allDependencies != null && allDependencies.size() > 0) {
            def dependFilePw = new PrintWriter(dependFile.newWriter(false))

            allDependencies.each { d ->
                dependFilePw.println d.name
            }
            dependFilePw.flush()
            dependFilePw.close()
        }
    }

    public static generateDependInfo2(File dependFile, Set allDependencies ) {
        if (allDependencies != null && allDependencies.size() > 0) {
            def dependFilePw = new PrintWriter(dependFile.newWriter(false))

            allDependencies.each { d ->
                if (d.group != null && d.group.trim() == "project") {
                    return
                }
                dependFilePw.println "${d.group}:${d.name}:${d.version}"
            }
            dependFilePw.flush()
            dependFilePw.close()
        }
    }

    public static List<ResolvedDependency> getAllResolveDependencies(Project project, String config) {
        Configuration configuration
        try {
            configuration = project.configurations[config]
        } catch (UnknownConfigurationException ignored) {
            return null
        }

        return getAllResolveDependencies(configuration, true)
    }

    public static List<ResolvedDependency> getAllResolveDependencies2(Project project, String config) {
        Configuration configuration
        try {
            configuration = project.configurations[config]
        } catch (UnknownConfigurationException ignored) {
            return null
        }

        return getAllResolveDependencies(configuration, false)
    }

    public static Set<ResolvedDependency> get3rdResolveDependencies(Project project, String config) {
        Configuration configuration
        try {
            configuration = project.configurations[config]
        } catch (UnknownConfigurationException ignored) {
            return null
        }

        Set<DefaultProjectDependency> projectDepencies = []
        DependenciesUtils.collectProjectDependencies(project, projectDepencies)

        List<ResolvedDependency> allDependencies = getAllResolveDependencies(configuration, true)

        allDependencies.removeAll { d->
            if (projectDepencies.find { it.name == d.moduleName && it.group == d.moduleGroup} != null) {
                return true
            }
        }

        return allDependencies
    }

    public static List<ResolvedDependency> get3rdResolveDependencies(Project project, Configuration configuration) {

        Set<DefaultProjectDependency> projectDepencies = []
        DependenciesUtils.collectProjectDependencies(project, projectDepencies)

        List<ResolvedDependency> allDependencies = getAllResolveDependencies(configuration, true)

        allDependencies.removeAll { d->
            if (projectDepencies.find { it.name == d.moduleName && it.group == d.moduleGroup} != null) {
                return true
            }
        }

        return allDependencies
    }

    public static Set<ResolvedDependency> getFirstLevelDependencies(Project project, String config) {
        def configuration = project.configurations[config]
        ResolvedConfiguration resolvedConfiguration = configuration.resolvedConfiguration
        def firstLevelDependencies = resolvedConfiguration.firstLevelModuleDependencies
        return firstLevelDependencies
    }

    public static void collectAllDependencies(Project prj, Set<Dependency> allDependencies, String config ) {
        //Defining configuration names from which dependencies will be taken (debugCompile or releaseCompile and compile)
        try {
            prj.configurations["${config}"].allDependencies.each { depend ->
                if (allDependencies.find { addedNode -> addedNode.group == depend.group && addedNode.name == depend.name } == null) {
                    allDependencies.add(depend)
                }
                if (depend instanceof DefaultProjectDependency) {
                    depend.dependencyProject.evaluate()
                    collectAllDependencies(depend.dependencyProject, allDependencies, config)
                }
            }
        } catch (UnknownConfigurationException ignored) {
        }
    }

    protected static List<ResolvedDependency> getAllResolveDependencies(Configuration configuration, boolean exclude) {
        ResolvedConfiguration resolvedConfiguration = configuration.resolvedConfiguration
        def firstLevelDependencies = resolvedConfiguration.firstLevelModuleDependencies
        List<ResolvedDependency> allDependencies = new ArrayList<>()
        firstLevelDependencies.each {
            collectDependencies(it, allDependencies, exclude)
        }
        return allDependencies
    }

    private static void collectDependencies(ResolvedDependency node, List<ResolvedDependency> out, boolean exclude) {
        if (exclude) {
            if (out.find { addedNode -> addedNode.name == node.name } == null) {
                out.add(node)
            }
        }else {
            out.add(node)
        }
        // Recursively
        node.children.each { newNode ->
            collectDependencies(newNode, out, exclude)
        }
    }

    static void collectProjectDependencies(Project prj, allDependencies ) {
        //Defining configuration names from which dependencies will be taken (debugCompile or releaseCompile and compile)
        prj.evaluate()
        def projectDenpendencies = []
        if (prj.configurations.findByName("compile")) {
            projectDenpendencies += prj.configurations['compile'].dependencies.withType(DefaultProjectDependency.class)
        }
        if (prj.configurations.findByName("implementation")) {
            projectDenpendencies += prj.configurations['implementation'].dependencies.withType(DefaultProjectDependency.class)
        }
        if (prj.configurations.findByName("api")) {
            projectDenpendencies += prj.configurations['api'].dependencies.withType(DefaultProjectDependency.class)
        }
        if (projectDenpendencies != null) {
            projectDenpendencies.each { depend ->
                if (allDependencies.find { addedNode -> addedNode.group == depend.group && addedNode.name == depend.name } == null) {
                    allDependencies.add(depend)
                    collectProjectDependencies(depend.dependencyProject, allDependencies)
                }
            }
        }
    }

    public static void collectAars(File d, Set outAars) {
        if (!d.exists()) return
        d.eachLine { line ->
            def splitResult = line.split(':')
            def version = ""
            def name = ""
            def group = ""
            if (splitResult.length > 2) {
                version = splitResult[2]
                name = splitResult[1]
                group = splitResult[0]
            } else if (splitResult.length > 1) {
                name = splitResult[1]
                group = splitResult[0]
            } else if (splitResult.length > 0) {
                name = splitResult[0]
            }
            if (group.trim() == "project" && version.isEmpty()) {
                return
            }
            def aar = outAars.find { it.group == group && it.name == name }
            if (aar == null) {
                aar = [group: group, name: name, version: version, artifacesMd5s:null]
                outAars.add(aar)
            }else {
                aar.version = version
            }
        }
    }

    public static String generateMD5(File file) {
        file.withInputStream {
            new DigestInputStream(it, MessageDigest.getInstance('MD5')).withStream {
                it.eachByte {}
                it.messageDigest.digest().encodeHex() as String
            }
        }
    }

    public static File getAarDependencyDir(Project project, String group, String name, String version, String variantName) {
        com.android.build.gradle.BaseExtension android = project.android
        boolean isApp = false
        if (android.class.name.find("com.android.build.gradle.AppExtension") != null) {
            isApp = true
        } else if (android.class.name.find("com.android.build.gradle.LibraryExtension") == null) {
            return
        }

        def variants
        if (isApp) {
            variants = android.applicationVariants
        } else {
            variants = android.libraryVariants
        }
        BaseVariant variant = variants.find { it.name.capitalize() == variantName.capitalize()}
        if (variant != null) {
            ArtifactCollection aars =  variant.variantData.scope.getArtifactCollection(ConsumedConfigType.RUNTIME_CLASSPATH, ArtifactScope.ALL, ArtifactType.EXPLODED_AAR)
            def findResult = aars.artifacts.find {
                String[] splitResult = it.getId().componentIdentifier.displayName.split(":")
                def g = splitResult[0]
                def n = splitResult[1]
                def v = ""
                if (splitResult.length > 2) {
                    v = splitResult[2]
                }
                if (g == group && n == name) {
                    return true
                }
            }
            if (findResult != null) {
                return findResult.file
            }
            return null
        }
    }
}