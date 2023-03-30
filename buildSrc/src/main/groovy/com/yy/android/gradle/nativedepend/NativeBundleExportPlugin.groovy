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
import org.gradle.api.Task
import org.gradle.api.file.FileCopyDetails
import org.gradle.api.file.FileTree
import org.gradle.api.file.CopySpec
import org.gradle.api.Action
import com.android.build.gradle.internal.api.LibraryVariantImpl
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

class NativeBundleExportPlugin implements Plugin<Project> {

    protected Project project

    void apply(Project project) {
        this.project = project
        createExtension()

        if (android.class.name.find("com.android.build.gradle.LibraryExtension") == null) {
            return
        }

        def variants = android.libraryVariants
        variants.whenObjectAdded { LibraryVariantImpl variant ->
            List<NativeBundleExportExtension> configs = []
            for (i in 0..<variant.getProductFlavors().size()) {
                configs.add(variant.getProductFlavors().get(i).nativeBundleExport)
            }
            configs.add(defaultNativeBundle)
            MergeNativeBundleExportExtension mergeNativeBundleExportExtension = merge(configs)
            hookBundleTask(GradleApiAdapter.getPackageLibraryTask(variant), variant, mergeNativeBundleExportExtension)
        }
    }

    protected void hookBundleTask(Task bundleTask, LibraryVariantImpl variant, MergeNativeBundleExportExtension config) {
        String variantName = variant.name
        String taskNameSuffix = variantName.capitalize()
        File bundleStaticOutputDir = new File("${project.buildDir}/intermediates/bundlesStatic/${variantName}")

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        LocalDateTime now = LocalDateTime.now();

        File linkOrderFile = new File("${project.buildDir}/intermediates/linkOrder/${variantName}/", "${project.name}_${dtf.format((now))}_link_order.txt")
        config.headerDirs.each {
            bundleTask.from(new File(it)) {
                exclude config.excludeHeaderFilter
                include config.includeHeaderFilter
                into "jni/include"
            }
        }
        bundleTask.from(linkOrderFile, new CopyAction("jni"))
        bundleTask.doFirst {
            linkOrderFile.parentFile.deleteDir()
            createLinkOrderTxt(config.linkOrder, linkOrderFile)
        }

        Task staticBundleTask = GradleApiAdapter.createBundleStaticTask(project, bundleStaticOutputDir, variantName, "bundleStaticLib${taskNameSuffix}")
        Task staticBundlePrepareTask = project.task("bundleStaticLibPrepare${taskNameSuffix}").doFirst {
            if (config.bundleStatic) {
                def et = project.tasks.getByName("externalNativeBuild${taskNameSuffix}")
                if (bundleStaticOutputDir.exists()) bundleStaticOutputDir.deleteDir()
                FileTree aar = project.zipTree(bundleTask.archivePath)
                project.copy {
                    from aar
                    exclude "**/**.so"
                    into bundleStaticOutputDir
                }
                if (et != null) {
                    def objDir = GradleApiAdapter.getExternalNativeBuildObjDir(et)
                    project.copy {
                        from objDir
                        include "**/**.a"
                        exclude "**/objs**"
                        exclude config.excludeStaticLibs
                        into "${bundleStaticOutputDir}/jni"
                    }

                    def nativeBuildConfigurationsJsons = GradleApiAdapter.getNativeBuildConfigurationsJson(et, variant);

                    nativeBuildConfigurationsJsons.each { File js ->
                        project.copy {
                            from js.parentFile
                            include "**.a"
                            exclude config.excludeStaticLibs
                            into "${bundleStaticOutputDir}/jni/${js.parentFile.name}"
                        }
                    }
                }
                config.extraStaticLibDirs.each { String extraStaticLibDir ->
                    project.copy {
                        from extraStaticLibDir
                        include "**/**.a"
                        exclude "**/objs**"
                        exclude config.excludeStaticLibs
                        into "${bundleStaticOutputDir}/jni"
                    }
                }
            }
        }
        staticBundleTask.dependsOn staticBundlePrepareTask
        staticBundlePrepareTask.dependsOn bundleTask
        if (config.bundleStatic) {
            bundleTask.finalizedBy("bundleStaticLib${taskNameSuffix}")
        }
    }

    private class CopyAction implements Action<CopySpec> {
        private String mSegment

        CopyAction(String segment) {
            mSegment = segment
        }
        void execute(CopySpec copySpec) {
            copySpec.eachFile(new Action<FileCopyDetails>() {
                @Override
                void execute(FileCopyDetails fileCopyDetails) {
                    fileCopyDetails.relativePath = fileCopyDetails.relativePath.prepend(mSegment)
                }
            }
            )
        }
    }

    protected void createExtension() {
        project.extensions.create('nativeBundleExport', NativeBundleExportExtension.class, project)
        android.productFlavors.whenObjectAdded { flavor ->
            flavor.extensions.create('nativeBundleExport', NativeBundleExportExtension.class, project)
        }
    }

    protected NativeBundleExportExtension getDefaultNativeBundle() {
        return (NativeBundleExportExtension) project.nativeBundleExport
    }

    protected com.android.build.gradle.BaseExtension getAndroid() {
        return project.android
    }

    private void createLinkOrderTxt(String linkOrder, File linkOrderFile) {
        if (linkOrder != null && linkOrder.split(":").length > 1) {
            if (!linkOrderFile.parentFile.exists())linkOrderFile.parentFile.mkdirs()
            def pw = linkOrderFile.newPrintWriter()
            pw.println(linkOrder)
            pw.flush()
            pw.close()
        }
    }

    MergeNativeBundleExportExtension merge(List<NativeBundleExportExtension> extensions) {
        if (extensions.empty) {
            return null
        }else {
            MergeNativeBundleExportExtension mergeExtension = new MergeNativeBundleExportExtension()
            mergeExtension.bundleStatic = extensions.get(0).bundleStatic;
            extensions.each {
                if (it.headerDir != null) {
                    mergeExtension.headerDirs.add(it.headerDir)
                }
                if (it.extraStaticLibDir != null) {
                    mergeExtension.extraStaticLibDirs.addAll(it.extraStaticLibDir)
                }
                mergeExtension.excludeHeaderFilter.addAll(it.excludeHeaderFilter)
                mergeExtension.includeHeaderFilter.addAll(it.includeHeaderFilter)
                mergeExtension.excludeStaticLibs.addAll(it.excludeStaticLibs)
                if (mergeExtension.linkOrder == null) {
                    mergeExtension.linkOrder = it.linkOrder
                }else {
                    if (it.linkOrder != null) {
                        mergeExtension.linkOrder = mergeExtension.linkOrder + ":" + it.linkOrder
                    }
                }
            }
            return mergeExtension
        }
    }

    static class MergeNativeBundleExportExtension {
        Set<String> headerDirs = []
        boolean bundleStatic
        Set<String> extraStaticLibDirs = []
        Set<String> excludeHeaderFilter = []
        Set<String> includeHeaderFilter = []
        Set<String> excludeStaticLibs = []
        String linkOrder = null

        MergeNativeBundleExportExtension() {

        }
    }


}
