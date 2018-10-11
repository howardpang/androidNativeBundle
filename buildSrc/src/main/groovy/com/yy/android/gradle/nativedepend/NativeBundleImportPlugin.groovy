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

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.artifacts.ArtifactCollection
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import org.apache.commons.io.FileUtils

class NativeBundleImportPlugin implements Plugin<Project> {

    private static final String[] APP_ABIS = ["armeabi", "armeabi-v7a", "x86", "mips", "arm64-v8a", "x86_64", "mips64"]
    protected Project project

    void apply(Project project) {
        this.project = project
        createExtension()
        File intermediatesDir = new File(project.buildDir, "nativeLib")
        File gradleMk = new File(intermediatesDir, "gradle.mk")
        nativeBundle.ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK = gradleMk
        if (!gradleMk.parentFile.exists()) gradleMk.parentFile.mkdirs()
        gradleMk.createNewFile()

        android.defaultConfig.externalNativeBuild.ndkBuild.arguments("ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK=${gradleMk.path.replace("\\", "/")}")
        android.defaultConfig.externalNativeBuild.cmake.arguments("-DANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK=${gradleMk.path.replace("\\", "/")}")
        android.productFlavors.whenObjectAdded {
            gradleMk = new File(intermediatesDir, "${it.name}/gradle.mk")
            it.nativeBundleImport.ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK = gradleMk
            if (!gradleMk.parentFile.exists()) gradleMk.parentFile.mkdirs()
            gradleMk.createNewFile()
            it.externalNativeBuild.ndkBuild.arguments("ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK=${gradleMk.path.replace("\\", "/")}")
            it.externalNativeBuild.cmake.arguments("-DANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK=${gradleMk.path.replace("\\", "/")}")
        }

        def variants
        if (android.class.name.find("com.android.build.gradle.AppExtension") != null ||
            android.class.name.find("com.android.build.gradle.internal.dsl.BaseAppModuleExtension") != null) {
            variants = android.applicationVariants
        } else if (android.class.name.find("com.android.build.gradle.LibraryExtension") != null) {
            variants = android.libraryVariants
        } else {
            println("Only support android gradle plugin")
            return
        }

        variants.whenObjectAdded { variant ->
            hookVariant(variant, gradleMk, intermediatesDir)
        }
        project.tasks.getByName("preBuild").doFirst {
            // clean task will delete the files, so we should recreate
            if (!intermediatesDir.exists()) {
                intermediatesDir.mkdirs()
                println("re pull native bundle file")
                variants.each { variant->
                    hookVariant(variant, gradleMk, intermediatesDir)
                }
            }
        }
    }

    private void hookVariant(def variant, File gradleMk, File intermediatesDir) {
        Map<String, List<File>> linkLibs = [:]
        Set<String> wholeStaticLibs = []
        Map<File, File> includeDirs = [:]
        String flavorDir = ""
        gradleMk = nativeBundle.ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK
        String wholeStaticLibsStr = nativeBundle.wholeStaticLibs

        if (!variant.flavorName.isEmpty()) {
            flavorDir = "${variant.flavorName}/"
            gradleMk = android.productFlavors.getByName(variant.flavorName).nativeBundleImport.ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK
            wholeStaticLibsStr = android.productFlavors.getByName(variant.flavorName).nativeBundleImport.wholeStaticLibs
        }
        if (wholeStaticLibsStr != null) {
            wholeStaticLibs.addAll(wholeStaticLibsStr.split(":"))
        }
        if (!gradleMk.parentFile.exists()) gradleMk.parentFile.mkdirs()
        gradleMk.createNewFile()

        //List<ResolvedDependency> dependencies =  DependenciesUtils.get3rdResolveDependencies(project, variant.runtimeConfiguration)
        ArtifactCollection aars = variant.variantData.scope.getArtifactCollection(ConsumedConfigType.RUNTIME_CLASSPATH, ArtifactScope.EXTERNAL, ArtifactType.EXPLODED_AAR)
        aars.artifacts.each { aar ->
            File aarDir = aar.file
            File includeDir = new File(aarDir, "jni/include")
            if (includeDir.exists()) {
                String[] linkOrder
                File linkOrderFile = new File(aarDir, NativeBundleExportPlugin.LINK_ORDER_TXT_FILE_NAME)
                if (linkOrderFile.exists()) {
                    linkOrder = linkOrderFile.readLines().get(0).split(":")
                }

                String[] splitResult = aar.getId().componentIdentifier.displayName.split(":")
                def group = splitResult[0]
                def name = splitResult[1]
                def version = ""
                if (splitResult.length > 2) {
                    version = splitResult[2]
                }

                APP_ABIS.each {
                    File abi = new File(aarDir, "jni/${it}")
                    if (abi.exists()) {
                        List<File> archLibs = linkLibs.get(it)
                        if (archLibs == null) {
                            archLibs = []
                            linkLibs.put(it, archLibs)
                        }
                        List libs = new ArrayList<File>()
                        libs.addAll(abi.listFiles())
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
                //Note dstDir should be same for every ndk build, because ndk-build will check the include files whether
                //modify to increment build
                File dstDir = new File(intermediatesDir, "${flavorDir}${group}/${name}/jni")
                includeDirs.put(includeDir.parentFile, dstDir)
            }
        }


        File tmpMkFile = new File(gradleMk.parentFile, "tmp.mk")
        if (!gradleMk.exists()) {
            tmpMkFile = gradleMk
        }

        if (android.externalNativeBuild.ndkBuild.path != null) {
            generateNdkBuildMk(tmpMkFile, includeDirs, linkLibs, wholeStaticLibs)
            println("external ndk build ")
        } else if (android.externalNativeBuild.cmake.path != null) {
            generateCMakeBuildMk(tmpMkFile, includeDirs, linkLibs, wholeStaticLibs)
            println("external cmake build ")
        } else {
            generateNdkBuildMk(tmpMkFile, includeDirs, linkLibs, wholeStaticLibs)
            println("custom ndk build ")
        }

        if (!FileUtils.contentEquals(gradleMk, tmpMkFile)) {
            includeDirs.each { src, dst ->
                dst.deleteDir()
                project.copy {
                    from src
                    into dst
                }
            }
            println("update native bundle import make file ")
            project.copy {
                from tmpMkFile
                into tmpMkFile.parentFile
                rename tmpMkFile.name, gradleMk.name
            }
        }
    }

    protected void generateNdkBuildMk(File mk, Map<File, File> includeDirs, Map<String, List<File>> linkLibs, Set<String> wholeStaticLibs) {
        def pw = mk.newPrintWriter()

        pw.println("LOCAL_C_INCLUDES += \\")

        includeDirs.each { src, dst->
            pw.println("    ${dst.path.replace("\\", "/")}/include \\")
        }

        pw.println("")

        linkLibs.each { k, v ->
            //String flag = "LOCAL_LDFLAGS += -Wl,--as-needed "
            String flag = "LOCAL_LDFLAGS += "
            List<File> normalLib = v
            List<File> wholeLib = null
            if (wholeStaticLibs != null && !wholeStaticLibs.isEmpty()) {
                wholeLib = []
                normalLib = []
                v.each {
                    if (wholeStaticLibs.contains(it.name)) {
                        wholeLib.add(it)
                    }else {
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

            pw.println("ifeq (\$(TARGET_ARCH_ABI), ${k})")
            pw.println("    ${flag}")
            pw.println("endif")
        }
        pw.flush()
        pw.close()
    }

    protected void generateCMakeBuildMk(File mk, Map<File, File> includeDirs, Map<String, List<File>> linkLibs , Set<String> wholeStaticLibs) {
        def pw = mk.newPrintWriter()
        if (includeDirs.size() > 0) {
            pw.println("include_directories (")
            includeDirs.each { src, dst->
                pw.println("${dst.path.replace("\\", "/")}/include")
            }
            pw.println(")")
        }

        linkLibs.each { k, v ->
            pw.println("if(\${ANDROID_ABI} STREQUAL \"${k}\")")
            pw.print("   set(ANDROID_GRADLE_NATIVE_MODULES ")
            List<File> normalLib = v
            List<File> wholeLib = null
            if (wholeStaticLibs != null && !wholeStaticLibs.isEmpty()) {
                wholeLib = []
                normalLib = []
                v.each {
                    if (wholeStaticLibs.contains(it.name)) {
                        wholeLib.add(it)
                    }else {
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

    protected NativeBundleImportExtension getNativeBundle() {
        return (NativeBundleImportExtension) project.nativeBundleImport
    }
}
