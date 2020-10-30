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
import org.gradle.api.tasks.bundling.Jar
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
            NativeBundleExportExtension config
            if(variant.flavorName.isEmpty()) {
                config = nativeBundle
            }else {
                config = android.productFlavors.getByName(variant.flavorName).nativeBundleExport
            }
            hookBundleTask(GradleApiAdapter.getPackageLibraryTask(variant), variant, config)
        }
    }

    protected void hookBundleTask(Task bundleTask, LibraryVariantImpl variant, NativeBundleExportExtension config) {
        String variantName = variant.name
        String taskNameSuffix = variantName.capitalize()
        File bundleStaticOutputDir = new File("${project.buildDir}/intermediates/bundlesStatic/${variantName}")

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        LocalDateTime now = LocalDateTime.now();

        File linkOrderFile = new File("${project.buildDir}/intermediates/linkOrder/${variantName}/", "${project.name}_${dtf.format((now))}_link_order.txt")
        if (config.headerDir != null) {
            bundleTask.from(new File(config.headerDir)) {
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

        Task staticBundleTask = project.task("bundleStaticLib${taskNameSuffix}", type: Jar) {
            from bundleStaticOutputDir
            extension 'aar'
            baseName "${project.name}-static-${variantName}"
            destinationDir new File(project.buildDir, 'outputs/aar')
            version ''
        }
        Task staticBundlePrepareTask = project.task("bundleStaticLibPrepare${taskNameSuffix}").doFirst {
            def et = project.tasks.getByName("externalNativeBuild${taskNameSuffix}")
            if (config.bundleStatic) {
                if (bundleStaticOutputDir.exists()) bundleStaticOutputDir.deleteDir()
                FileTree aar = project.zipTree(bundleTask.archivePath)
                project.copy {
                    from aar
                    exclude "**/**.so"
                    into bundleStaticOutputDir
                }
                if (et != null) {
                    project.copy {
                        from et.getObjFolder()
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
                if (config.extraStaticLibDir != null) {
                    project.copy {
                        from config.extraStaticLibDir
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

    protected NativeBundleExportExtension getNativeBundle() {
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
}
