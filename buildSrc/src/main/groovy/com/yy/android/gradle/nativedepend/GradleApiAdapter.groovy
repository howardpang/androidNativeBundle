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

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.api.LibraryVariantImpl
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType
import com.android.build.gradle.tasks.ExternalNativeBuildJsonTask
import com.android.build.gradle.tasks.NativeBuildSystem
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.tasks.bundling.Jar
import org.gradle.util.VersionNumber
import com.android.build.gradle.internal.dsl.CoreExternalNativeBuildOptions;
import org.gradle.api.Task
import java.lang.reflect.Field

class GradleApiAdapter {
    private static String androidPluginVersion
    static List<File> getJniFolders(Project project, ApplicationVariantImpl variant) {
        List<File> jniFolders = []
        if (isAndroidGradleVersionGreaterOrEqualTo("3.5.0")) {
            String taskName = variant.variantData.scope.getTaskName("merge", "JniLibFolders")
            Task mergeJniLibsTask = project.tasks.findByName(taskName)
            if (mergeJniLibsTask != null) {
                jniFolders.add(mergeJniLibsTask.outputDir.getAsFile().get())
            }
            Task mergeNativeLibsTask = project.tasks.withType(Class.forName("com.android.build.gradle.internal.tasks.MergeNativeLibsTask")).find {
                it.variantName == variant.name
            }
            if (mergeNativeLibsTask != null) {
                jniFolders.add(mergeNativeLibsTask.outputDir.getAsFile().get())
            }
        }else {
            Task mergeJniLibsTask = project.tasks.withType(TransformTask.class).find {
                it.transform.name == 'mergeJniLibs' && it.variantName == variant.name
            }
            if (mergeJniLibsTask != null) {
                jniFolders.add(mergeJniLibsTask.streamOutputFolder)
            }
        }
        return jniFolders
    }

    static Task getPackageLibraryTask(LibraryVariantImpl variant) {
        Task task
        if (isAndroidGradleVersionGreaterOrEqualTo("3.3.0")) {
            task = variant.packageLibraryProvider.get()
        }else {
            task = variant.packageLibrary
        }
        return task
    }

    static def getNativeBuildConfigurationsJson(def externalNativeBuildTask, LibraryVariantImpl variant) {
        def nativeBuildConfigurationsJson
        if (isAndroidGradleVersionGreaterOrEqualTo("7.1.0")) {
            nativeBuildConfigurationsJson = [ ]
            variant.variantData.getTaskContainer().cxxConfigurationModel.activeAbis.each {
                File json = new File(it.cxxBuildFolder, "/android_gralde_build.json")
                nativeBuildConfigurationsJson.add(json)
            }
        } else if (isAndroidGradleVersionGreaterOrEqualTo("7.0.0")) {
            nativeBuildConfigurationsJson = [ ]
            variant.variantData.getTaskContainer().cxxConfigurationModel.variant.validAbiList.each {
                File json = new File(variant.variantData.getTaskContainer().cxxConfigurationModel.variant.cxxBuildFolder, "${it.getTag()}/android_gralde_build.json")
                nativeBuildConfigurationsJson.add(json)
            }
        } else if (isAndroidGradleVersionGreaterOrEqualTo("3.5.0")) {
            nativeBuildConfigurationsJson = variant.variantData.getTaskContainer().externalNativeJsonGenerator.get().nativeBuildConfigurationsJsons
        }else {
            nativeBuildConfigurationsJson = externalNativeBuildTask.nativeBuildConfigurationsJsons
        }
        return nativeBuildConfigurationsJson
    }

    static void addArgumentToNativeBuildOption(Project project, def variant, String ndkArgument, String cmakeArgument) {
        if (isAndroidGradleVersionGreaterOrEqualTo("7.1.0")) {
            // For configure, see 'com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel' for detail
            variant.variantData.getTaskContainer().cxxConfigurationModel.activeAbis.each {
                if (it.variant.module.buildSystem == NativeBuildSystem.CMAKE) {
                    it.configurationArguments.add(cmakeArgument)
                }else {
                    it.configurationArguments.add(ndkArgument)
                }
            }
            // For build
            def result = project.tasks.withType(ExternalNativeBuildJsonTask.class).findAll {
                it.abi.variant.variantName == variant.name
            }
            if (result != null) {
                result.each { re ->
                    re.doFirst {
                        if (re.abi.variant.module.buildSystem == NativeBuildSystem.CMAKE) {
                            re.abi.configurationArguments.add(cmakeArgument)
                        } else {
                            re.abi.configurationArguments.add(ndkArgument)
                        }
                    }
                }
            }else {
                println(project.name + "NativeBundleImportPlugin:addArgumentToNativeBuildOption can't find ExternalNativeBuildJsonTask " + variant.name)
            }
        }else if (isAndroidGradleVersionGreaterOrEqualTo("7.0.0")) {
            // For configure, see 'com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel' for detail
            variant.variantData.getTaskContainer().cxxConfigurationModel.activeAbis.each {
                if (it.variant.module.buildSystem == NativeBuildSystem.CMAKE) {
                    it.configurationArguments.add(cmakeArgument)
                }else {
                    it.configurationArguments.add(ndkArgument)
                }
            }
            // For build
            def result = project.tasks.withType(ExternalNativeBuildJsonTask.class).find {
                it.configurationModel.variant.variantName == variant.name
            }
            if (result != null) {
                result.configurationModel.activeAbis.each {
                    if (it.variant.module.buildSystem == NativeBuildSystem.CMAKE) {
                        it.configurationArguments.add(cmakeArgument)
                    } else {
                        it.configurationArguments.add(ndkArgument)
                    }
                }
            }else {
                println(project.name + "NativeBundleImportPlugin:addArgumentToNativeBuildOption can't find ExternalNativeBuildJsonTask " + variant.name)
            }
        } else if (isAndroidGradleVersionGreaterOrEqualTo("4.0.0")) {
            CoreExternalNativeBuildOptions externalNativeBuildOptions = variant.variantData.variantDslInfo.externalNativeBuildOptions
            externalNativeBuildOptions.externalNativeNdkBuildOptions.arguments.add(ndkArgument)
            externalNativeBuildOptions.externalNativeCmakeOptions.arguments.add(cmakeArgument)
        }else {
            CoreExternalNativeBuildOptions externalNativeBuildOptions = variant.variantData.variantConfiguration.externalNativeBuildOptions
            externalNativeBuildOptions.externalNativeNdkBuildOptions.arguments.add(ndkArgument)
            externalNativeBuildOptions.externalNativeCmakeOptions.arguments.add(cmakeArgument)
        }
    }

    static  ArtifactCollection getArtifactCollection(def variant, ConsumedConfigType type, ArtifactScope scope, ArtifactType artifactType) {
        ArtifactCollection artifactCollection
        if (isAndroidGradleVersionGreaterOrEqualTo("4.1.0")) {
            artifactCollection = variant.variantData.variantDependencies.getArtifactCollection(type, scope, artifactType)
        }else {
            artifactCollection = variant.variantData.scope.getArtifactCollection(type, scope, artifactType)
        }
        return artifactCollection
    }

    static Task getPackageApplicationTask(ApplicationVariantImpl variant) {
        Task task
        if (isAndroidGradleVersionGreaterOrEqualTo("3.3.0")) {
            task = variant.packageApplicationProvider.get()
        }else {
            task = variant.packageApplication
        }
        return task
    }

    static Task getMergeResourcesTask(ApplicationVariantImpl variant) {
        Task task
        if (isAndroidGradleVersionGreaterOrEqualTo("3.3.0")) {
            task = variant.mergeResourcesProvider.get()
        }else {
            task = variant.mergeResources
        }
        return task
    }

    static boolean isAndroidGradleVersionGreaterOrEqualTo(String targetVersionString) {
        String curVersionString = androidGradleVersion()
        VersionNumber currentVersion = VersionNumber.parse(curVersionString)
        VersionNumber targetVersion = VersionNumber.parse(targetVersionString)
        return currentVersion >= targetVersion
    }

    static String androidGradleVersion() {
        if (androidPluginVersion == null) {
            try {
                Class<?> versionClass = Class.forName("com.android.builder.model.Version")
                Field versionField = versionClass.getField("ANDROID_GRADLE_PLUGIN_VERSION")
                androidPluginVersion = versionField.get(null)
            } catch(ClassNotFoundException e) {
                println(" unknown android plugin version ")
            }
            //androidPluginVersion = ProcessProfileWriter.getProject(project.getPath()).getAndroidPluginVersion()
            println(" android plugin version " + androidPluginVersion)
        }
        return androidPluginVersion
    }

    static Task createBundleStaticTask(Project project, File bundleStaticOutputDir, String variantName,String name) {
        project.gradle.gradleVersion
        VersionNumber currentVersion = VersionNumber.parse(project.gradle.gradleVersion)
        VersionNumber targetVersion = VersionNumber.parse("5.0")
        Task staticBundleTask
        if (currentVersion > targetVersion) {
            staticBundleTask = project.task(name, type: Jar) {
                from bundleStaticOutputDir
                archiveExtension = 'aar'
                archiveBaseName = "${project.name}-static-${variantName}"
                getDestinationDirectory().set(new File(project.buildDir, 'outputs/aar'))
                archiveVersion = ''
            }
        }else {
            staticBundleTask = project.task(name, type: Jar) {
                from bundleStaticOutputDir
                extension 'aar'
                baseName "${project.name}-static-${variantName}"
                destinationDir new File(project.buildDir, 'outputs/aar')
                version ''
            }
        }
        return staticBundleTask
    }
}
