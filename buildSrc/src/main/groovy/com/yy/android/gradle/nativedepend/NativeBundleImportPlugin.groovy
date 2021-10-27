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
package com.yy.android.gradle.nativedepend

import com.android.build.gradle.api.AndroidSourceSet
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.artifacts.ArtifactCollection
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.Configuration
import org.apache.commons.io.FileUtils
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.artifacts.component.ProjectComponentIdentifier

import static com.android.build.gradle.internal.publishing.AndroidArtifacts.ARTIFACT_TYPE
import org.gradle.api.Action
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.specs.Spec
import org.gradle.api.file.FileTree
import com.yy.android.gradle.nativedepend.util.DependenciesUtils

class NativeBundleImportPlugin implements Plugin<Project> {

    private static final String[] APP_ABIS = ["armeabi", "armeabi-v7a", "x86", "mips", "arm64-v8a", "x86_64", "mips64"]
    private static String intermediatesDirName = "nativeLib"
    protected Project project

    void apply(Project project) {
        this.project = project
        createExtension()
        File intermediatesDir = new File(project.buildDir, intermediatesDirName)
        File gradleMk = new File(intermediatesDir, "gradle.mk")
        defaultNativeBundle.ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK = gradleMk
        if (!gradleMk.parentFile.exists()) gradleMk.parentFile.mkdirs()
        gradleMk.createNewFile()

        def variants
        if (android.class.name.find("com.android.build.gradle.AppExtension") != null ||
                android.class.name.find("com.android.build.gradle.internal.dsl.BaseAppModuleExtension") != null) {
            variants = android.applicationVariants
        } else if (android.class.name.find("com.android.build.gradle.LibraryExtension") != null) {
            variants = android.libraryVariants
        } else {
            println(":${project.name}:Only support android gradle plugin")
            return
        }

        variants.whenObjectAdded { variant ->
            String gradleMkPath
            if (!variant.flavorName.isEmpty()) {
                gradleMkPath = "${variant.flavorName}/${variant.buildType.name}/gradle.mk"
            }else {
                gradleMkPath = "${variant.buildType.name}/gradle.mk"
            }

            gradleMk = new File(intermediatesDir, gradleMkPath)
            if (!gradleMk.parentFile.exists()) gradleMk.parentFile.mkdirs()
            gradleMk.createNewFile()
            String ndkDefine = "ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK=${gradleMk.path.replace("\\", "/")}"
            String cmakeDefine = "-DANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK=${gradleMk.path.replace("\\", "/")}"
            GradleApiAdapter.addArgumentToNativeBuildOption(project, variant, ndkDefine, cmakeDefine)
        }

        project.afterEvaluate {
            variants.all { variant ->
                hookVariant(variant)
            }
        }
        project.tasks.getByName("clean").doLast {
            // clean task will delete the files, so we should recreate
            if (!intermediatesDir.exists()) {
                intermediatesDir.mkdirs()
                println(":${project.name}:re pull native bundle file")
                variants.each { variant ->
                    hookVariant(variant)
                }
            }
        }
    }

    private void hookVariant(def variant) {
        Map<String, Set<File>> linkLibs = [:]
        Set<String> wholeStaticLibs = []
        Set<File> includeDirs = []
        Map<File, File> nativeLibs = [:]
        Map<File, File> sos = [:]
        Map<File, File> hars = [:]
        Set<String> excludeDependenciesList = []
        Set<Map> excludeDependencies = []
        Set<String> excludeLibs = []
        boolean cacheLibs = defaultNativeBundle.cacheLibs
        String wholeStaticLibsStr = defaultNativeBundle.wholeStaticLibs
        excludeDependenciesList.addAll(defaultNativeBundle.excludeDependencies)
        excludeLibs.addAll(defaultNativeBundle.excludeLibs)
        FilenameFilter excludeLibsFilter = new FilenameFilter() {
            @Override
            boolean accept(File dir, String name) {
                return !excludeLibs.contains(name)
            }
        }

        String varIntermediatesDirPath
        if (!variant.flavorName.isEmpty()) {
            varIntermediatesDirPath = "${intermediatesDirName}/${variant.flavorName}/${variant.buildType.name}"
        }else {
            varIntermediatesDirPath = "${intermediatesDirName}/${variant.buildType.name}"
        }
        File varIntermediatesDir = new File(project.buildDir, varIntermediatesDirPath)
        File gradleMk = new File(varIntermediatesDir, "gradle.mk")
        if (wholeStaticLibsStr != null) {
            wholeStaticLibs.addAll(wholeStaticLibsStr.split(":"))
        }
        variant.getProductFlavors().each {
            wholeStaticLibsStr = it.nativeBundleImport.wholeStaticLibs
            if (wholeStaticLibsStr != null) {
                wholeStaticLibs.addAll(wholeStaticLibsStr.split(":"))
            }
            excludeDependenciesList.addAll(it.nativeBundleImport.excludeDependencies)
            excludeLibs.addAll(it.nativeBundleImport.excludeLibs)
            cacheLibs |= it.nativeBundleImport.cacheLibs
        }

        excludeDependenciesList.each {
            String[] splitResult = it.split(":")
            if (splitResult.length > 1) {
                excludeDependencies.add(group:splitResult[0], name:splitResult[1])
            }
        }
        if (!gradleMk.parentFile.exists()) gradleMk.parentFile.mkdirs()
        gradleMk.createNewFile()

        AndroidSourceSet variantSourceSet =  variant.sourceSets.find {
            it.name == variant.name
        }

        File tmpMkFile = new File(gradleMk.parentFile, "tmp.mk")
        if (!gradleMk.exists()) {
            tmpMkFile = gradleMk
        }

        def pw = tmpMkFile.newPrintWriter()

        //gather 'implementation "ggggg:mmmm:vvvvv:armeabi-v7a@har", implementation "ggggg:mmmm:vvvvv:armeabi-v7a@so" ' native info
        def resolveDependencies = DependenciesUtils.getFirstLevelDependencies(project, "${variant.name}CompileClasspath")
        Set<DefaultProjectDependency> projectDependencies = []
        DependenciesUtils.collectProjectDependencies(project, projectDependencies)
        resolveDependencies.each { d ->
            if (projectDependencies.find { it.name == d.moduleName } != null) {
                return
            }

            ResolvedArtifact har = null
            if (d.moduleArtifacts.size() > 1) {
                har = d.moduleArtifacts.find { a ->
                    if (a.extension == "har") {
                        return true
                    }
                }
            }

            boolean isExclude = (excludeDependencies.find { it.group == d.moduleGroup && it.name == d.moduleName } != null)
            boolean haveArchive = false
            d.moduleArtifacts.each { lib ->
                if (lib.classifier != null && APP_ABIS.find { it == lib.classifier } != null) {
                    if (lib.extension == "a" || lib.extension == "so") {
                        haveArchive = true
                        File dstDir = new File(varIntermediatesDir, "${d.moduleGroup}/${d.moduleName}/jni")
                        pw.println("# ${lib.file.path}")
                        File libPath = new File(dstDir, "${lib.classifier}/lib${lib.name}.${lib.extension}")
                        sos.put(lib.file, libPath)
                        if (variantSourceSet != null) {
                            variantSourceSet.jniLibs.srcDirs += dstDir
                        }
                        if (har != null && !isExclude) {
                            List<File> archLibs = linkLibs.get(lib.classifier)
                            if (archLibs == null) {
                                archLibs = []
                                linkLibs.put(lib.classifier, archLibs)
                            }
                            archLibs.add(libPath)
                        }
                    }
                }
            }
            if (har != null && haveArchive) {
                pw.println("# ${har.file.path}")
                File dstDir = new File(varIntermediatesDir, "${d.moduleGroup}/${d.moduleName}/jni")
                File includePath = new File(dstDir, "include")
                hars.put(har.file, includePath)
                if (!isExclude) {
                    includeDirs.add(includePath)
                }
            }
        }

        pw.flush()
        pw.close()

        /*
        Configuration configuration = variant.variantData.getVariantDependency().getCompileClasspath().copyRecursive{
            return !(it instanceof DefaultProjectDependency)
        }
        ArtifactCollection aars = computeArtifactCollection(configuration, ArtifactScope.EXTERNAL, ArtifactType.EXPLODED_AAR)
        */

        //gather 'aar' native info
        ArtifactCollection aars = GradleApiAdapter.getArtifactCollection(variant, ConsumedConfigType.COMPILE_CLASSPATH, ArtifactScope.EXTERNAL, ArtifactType.EXPLODED_AAR)
        aars.artifacts.each { aar ->
            File aarDir = aar.file
            File includeDir = new File(aarDir, "jni/include")
            if (includeDir.exists()) {
                String[] linkOrder
                File linkOrderFile = new File(aarDir, "jni").listFiles().find { it.name.endsWith("link_order.txt") }
                if (linkOrderFile != null ) {
                    linkOrder = linkOrderFile.readLines().get(0).split(":")
                }

                String[] splitResult = aar.getId().componentIdentifier.displayName.split(":")
                def group = splitResult[0]
                def name = splitResult[1]
                def version = ""
                if (splitResult.length > 2) {
                    version = splitResult[2]
                }
                //Note dstDir should be same for every ndk build, because ndk-build will check the include files whether
                //modify to increment build
                File dstDir = new File(varIntermediatesDir, "${group}/${name}/jni")
                boolean isExclude = (excludeDependencies.find { it.group == group && it.name == name } != null)
                if (!isExclude) {
                    APP_ABIS.each {
                        File abiDir = new File(aarDir, "jni/${it}")
                        if (abiDir.exists()) {
                            Set<File> archLibs = linkLibs.get(it)
                            if (archLibs == null) {
                                archLibs = []
                                linkLibs.put(it, archLibs)
                            }
                            List libs = new ArrayList<File>()
                            libs.addAll(abiDir.listFiles(excludeLibsFilter))
                            if (linkOrder != null) {
                                linkOrder.each { libName ->
                                    File f = libs.find { libName == it.name }
                                    if (f != null) {
                                        archLibs.add(f)
                                        libs.remove(f)
                                    }
                                }
                            }
                            archLibs.addAll(libs)
                        }
                    }
                    includeDirs.add(new File(aarDir, "jni/include"))
                }
                nativeLibs.put(includeDir.parentFile, dstDir)
            }
        }

        if (android.externalNativeBuild.ndkBuild.path != null) {
            generateNdkBuildMk(tmpMkFile, includeDirs, linkLibs, wholeStaticLibs)
            println(":${project.name}:external ndk build ")
        } else if (android.externalNativeBuild.cmake.path != null) {
            generateCMakeBuildMk(tmpMkFile, includeDirs, linkLibs, wholeStaticLibs)
            println(":${project.name}:external cmake build ")
        } else {
            generateNdkBuildMk(tmpMkFile, includeDirs, linkLibs, wholeStaticLibs)
            println(":${project.name}:custom ndk build ")
        }

        // Compatibility with last version
        project.copy {
            from gradleMk
            into defaultNativeBundle.ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK.parentFile
        }

        if (!FileUtils.contentEquals(gradleMk, tmpMkFile)) {
            if (cacheLibs) {
                nativeLibs.each { src, dst ->
                    dst.deleteDir()
                    project.copy {
                        from src
                        into dst
                    }
                }
            }
            sos.each {src, dst ->
                if (!dst.parentFile.exists()) {
                    dst.parentFile.mkdirs()
                }
                project.copy {
                    from src
                    into dst.parentFile
                    rename src.name, dst.name
                }
            }
            hars.each {src, dst ->
                FileTree har = project.zipTree(src)
                project.copy {
                    from har
                    into dst
                    include "**/**.h"
                }
            }
            println(":${project.name}:update native bundle import make file ")
            project.copy {
                from tmpMkFile
                into tmpMkFile.parentFile
                rename tmpMkFile.name, gradleMk.name
            }
            // delete externalNativeBuild dir to force gradle recreate then the IDE can parse new native source code
            File externalBuildDir = new File(project.projectDir, ".externalNativeBuild")
            externalBuildDir.deleteDir()
            externalBuildDir = new File(project.projectDir, ".cxx")
            externalBuildDir.deleteDir()
            externalBuildDir = new File(project.buildDir, ".cxx")
            externalBuildDir.deleteDir()
        }
    }

    protected void generateNdkBuildMk(File mk, Set<File> includeDirs, Map<String, Set<File>> linkLibs, Set<String> wholeStaticLibs) {
        def pw = new PrintWriter(new FileOutputStream(mk, true))

        pw.println("LOCAL_C_INCLUDES += \\")

        includeDirs.each { it ->
            pw.println("    ${it.path.replace("\\", "/")} \\")
        }

        pw.println("")

        linkLibs.each { abi, v ->
            //String flag = "LOCAL_LDFLAGS += -Wl,--as-needed "
            String flag = "LOCAL_LDFLAGS += "
            Set<File> normalLib = v
            Set<File> wholeLib = null
            if (wholeStaticLibs != null && !wholeStaticLibs.isEmpty()) {
                wholeLib = []
                normalLib = []
                v.each {
                    if (wholeStaticLibs.contains(it.name)) {
                        wholeLib.add(it)
                    } else {
                        normalLib.add(it)
                    }
                }
            }

            if (wholeLib != null) {
                flag += " -Wl,--whole-archive "
                wholeLib.each {
                    flag += " ${it.path.replace("\\", "/")}"
                }
                flag += " -Wl,--no-whole-archive "
            }
            normalLib.each {
                flag += " ${it.path.replace("\\", "/")}"
            }

            pw.println("ifeq (\$(TARGET_ARCH_ABI), ${abi})")
            pw.println("    ${flag}")
            pw.println("endif")
        }
        pw.flush()
        pw.close()
    }

    protected void generateCMakeBuildMk(File mk, Set<File> includeDirs, Map<String, Set<File>> linkLibs, Set<String> wholeStaticLibs) {
        def pw = new PrintWriter(new FileOutputStream(mk, true))
        if (includeDirs.size() > 0) {
            pw.println("include_directories (")
            includeDirs.each { it ->
                pw.println("${it.path.replace("\\", "/")}")
            }
            pw.println(")")
        }

        linkLibs.each { k, v ->
            pw.println("if(\${ANDROID_ABI} STREQUAL \"${k}\")")
            pw.print("   set(ANDROID_GRADLE_NATIVE_MODULES ")
            Set<File> normalLib = v
            Set<File> wholeLib = null
            if (wholeStaticLibs != null && !wholeStaticLibs.isEmpty()) {
                wholeLib = []
                normalLib = []
                v.each {
                    if (wholeStaticLibs.contains(it.name)) {
                        wholeLib.add(it)
                    } else {
                        normalLib.add(it)
                    }
                }
            }

            if (wholeLib != null) {
                pw.print(" -Wl,--whole-archive ")
                wholeLib.each {
                    pw.print(" \"${it.path.replace("\\", "/")}\"")
                }
                pw.print(" -Wl,--no-whole-archive ")
            }
            normalLib.each {
                pw.print(" \"${it.path.replace("\\", "/")}\"")
            }
            pw.println(")")
            pw.println("endif(\${ANDROID_ABI} STREQUAL \"${k}\")")
        }

        pw.flush()
        pw.close()
    }

    protected com.android.build.gradle.BaseExtension getAndroid() {
        return project.android
    }

    protected void createExtension() {
        project.extensions.create('nativeBundleImport', NativeBundleImportExtension.class, project)
        android.productFlavors.whenObjectAdded { flavor ->
            flavor.extensions.create('nativeBundleImport', NativeBundleImportExtension.class, project)
        }
    }

    protected NativeBundleImportExtension getDefaultNativeBundle() {
        return (NativeBundleImportExtension) project.nativeBundleImport
    }

    private static Spec<ComponentIdentifier> getComponentFilter(
            ArtifactScope scope) {
        switch (scope) {
            case ArtifactScope.ALL:
                return null;
            case ArtifactScope.EXTERNAL:
                // since we want both Module dependencies and file based dependencies in this case
                // the best thing to do is search for non ProjectComponentIdentifier.
                //return id -> !(id instanceof ProjectComponentIdentifier);
                return new Spec<ComponentIdentifier>() {
                    @Override
                    boolean isSatisfiedBy(ComponentIdentifier componentIdentifier) {
                        return !(componentIdentifier instanceof ProjectComponentIdentifier)
                    }
                }
            case ArtifactScope.MODULE:
                return new Spec<ComponentIdentifier>() {
                    @Override
                    boolean isSatisfiedBy(ComponentIdentifier componentIdentifier) {
                        return componentIdentifier instanceof ProjectComponentIdentifier
                    }
                }
        //return id -> id instanceof ProjectComponentIdentifier;
            default:
                throw new RuntimeException("unknown ArtifactScope value")
        }
    }

    private ArtifactCollection computeArtifactCollection(
            Configuration configuration,
            ArtifactScope scope,
            ArtifactType artifactType) {
        Action<AttributeContainer> attributes = new Action<AttributeContainer>() {
            @Override
            void execute(AttributeContainer attributeContainer) {
                attributeContainer.attribute(ARTIFACT_TYPE, artifactType.getType())
            }
        }

        Spec<ComponentIdentifier> filter = getComponentFilter(scope)

        boolean lenientMode = true
        //Boolean.TRUE.equals(
        //       globalScope.getProjectOptions().get(BooleanOption.IDE_BUILD_MODEL_ONLY))

        return configuration
                .getIncoming()
                .artifactView(
                new Action<ArtifactView.ViewConfiguration>() {
                    @Override
                    void execute(ArtifactView.ViewConfiguration viewConfiguration) {
                        viewConfiguration.attributes(attributes)
                        if (filter != null) {
                            viewConfiguration.componentFilter(filter)
                        }
                        // TODO somehow read the unresolved dependencies?
                        viewConfiguration.lenient(lenientMode)
                    }
                }
        ).getArtifacts()
    }
}
