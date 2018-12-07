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
package com.yy.android.gradle.ndkdebug

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.initialization.DefaultSettings
import com.android.build.gradle.tasks.ExternalNativeBuildTask
import org.gradle.invocation.DefaultGradle

class NdkDebugHelper implements Plugin<DefaultSettings> {

    protected DefaultSettings settings
    private File dummyHostPath

    void apply(DefaultSettings settings) {
        this.settings = settings
        if (settings.hasProperty("hostPackageName")) {
            println("host package name: " + settings.hostPackageName)
            createFakeHost(settings.hostPackageName)
            settings.include(":dummyHost")
            dummyHostPath = new File(settings.rootDir, ".idea/dummyHost")
            settings.project(":dummyHost").projectDir = dummyHostPath
            boolean hookBuild = true
            if (settings.hasProperty("hookExternalBuild")) {
                hookBuild = settings.hookExternalBuild.toBoolean()
            }
            println("hookExternalBuild: " + hookBuild)
            if (hookBuild) {
                settings.gradle.beforeProject { p ->
                    hookProjectExternalNativeBuild(p)
                }
            }
        }
    }

    void hookProjectExternalNativeBuild(Project project) {
        project.afterEvaluate {
            if (project.hasProperty("android") && project.android.class.name.find("com.android.build.gradle.LibraryExtension") != null) {
                project.android.packagingOptions {
                    doNotStrip "*/armeabi/*.so"
                    doNotStrip "*/armeabi-v7a/*.so"
                    doNotStrip "*/x86/*.so"
                }
                if (project.android.productFlavors.size() > 0) {
                    project.android.productFlavors.each { f->
                        Task debugTask
                        Task releaseTask
                        project.tasks.whenObjectAdded {
                            if (it.name == "externalNativeBuild${f.name.capitalize()}Debug") {
                                debugTask = it
                                if (releaseTask != null) {
                                    hookExternalNativeBuildTask(project, debugTask, releaseTask)
                                }
                            }
                            if (it.name == "externalNativeBuild${f.name.capitalize()}Release") {
                                releaseTask = it
                                if (debugTask != null) {
                                    hookExternalNativeBuildTask(project, debugTask, releaseTask)
                                }
                            }
                        }
                    }
                } else {
                    Task debugTask
                    Task releaseTask
                    project.tasks.whenObjectAdded {
                        if (it.name == "externalNativeBuildDebug") {
                            debugTask = it
                            if (releaseTask != null) {
                                hookExternalNativeBuildTask(project, debugTask, releaseTask)
                            }
                        }
                        if (it.name == "externalNativeBuildRelease") {
                            releaseTask = it
                            if (debugTask != null) {
                                hookExternalNativeBuildTask(project, debugTask, releaseTask)
                            }
                        }
                    }
                }
            }
        }
    }

    void hookExternalNativeBuildTask(Project project, Task debugTask, Task releaseTask) {
        if (debugTask != null && releaseTask != null) {
            println("hook tasks " + project.name + ":" + releaseTask.name + ", " + project.name + ":" + debugTask.name)
            releaseTask.dependsOn debugTask
            releaseTask.doLast {
                project.copy {
                    from debugTask.getSoFolder().parentFile
                    into releaseTask.getSoFolder().parentFile
                }
            }
        }
    }

    void createFakeHost(String hostPacakgeName) {
        File dummyHostDir = new File(settings.rootDir, ".idea/dummyHost")
        if (!dummyHostDir.exists()) dummyHostDir.mkdirs()
        File buildGradle = new File(dummyHostDir, "build.gradle")
        def pw = new PrintWriter(buildGradle.newWriter(false))
        pw.print("""apply plugin: 'com.android.application'
android { 
    compileSdkVersion 23 
    defaultConfig { 
        applicationId "${hostPacakgeName}" 
        minSdkVersion 23 
        targetSdkVersion 27 
        versionCode 1 
        versionName "1.0" 
        externalNativeBuild {
            ndkBuild {
                abiFilters "armeabi-v7a"
            }
        }
    } 
    sourceSets {
        main {
            manifest.srcFile "\${projectDir}/AndroidManifest.xml"
        }
    }
    
    /*
    externalNativeBuild {
        ndkBuild {
            path 'Android.mk'
        }
    }
    */
} 

""")
        pw.flush()
        pw.close()

        File manifest = new File(dummyHostDir, "AndroidManifest.xml")
        pw = new PrintWriter(manifest.newWriter(false))
        pw.print("""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${hostPacakgeName}">

</manifest>
      """)
        pw.flush()
        pw.close()

        File amk = new File(dummyHostDir, "Android.mk")
        pw = new PrintWriter(amk.newWriter(false))
        pw.print("""
LOCAL_PATH := \$(call my-dir)
include \$(CLEAR_VARS)
LOCAL_SRC_FILES := dummy.cpp 
LOCAL_MODULE := dummy
include \$(BUILD_SHARED_LIBRARY)

      """)
        pw.flush()
        pw.close()

        File dummy = new File(dummyHostDir, "dummy.cpp")
        dummy.createNewFile()

    }
}
