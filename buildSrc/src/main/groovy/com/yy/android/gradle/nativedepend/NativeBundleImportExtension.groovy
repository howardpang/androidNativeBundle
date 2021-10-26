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

import com.android.annotations.NonNull
import org.gradle.api.Project

class NativeBundleImportExtension {
    NativeBundleImportExtension(Project project) {

    }

    File ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK
    String wholeStaticLibs
    Set<String> excludeDependencies = []
    Set<String> excludeLibs = []

    void excludeLibs(@NonNull String ...excludelibs) {
        Collections.addAll(this.excludeLibs, excludelibs);
    }

    void excludeDependencies(@NonNull String ...excludeDependencies) {
        Collections.addAll(this.excludeDependencies, excludeDependencies);
    }
}
