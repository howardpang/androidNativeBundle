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

class NativeBundleExportExtension {
    NativeBundleExportExtension(Project project) {

    }
    String headerDir
    boolean bundleStatic
    String extraStaticLibDir
    Set<String> excludeHeaderFilter = []
    Set<String> includeHeaderFilter = []
    Set<String> excludeStaticLibs = []
    String linkOrder

    void excludeHeaderFilter(@NonNull String ...excludeHeaderFilter) {
        Collections.addAll(this.excludeHeaderFilter, excludeHeaderFilter);
    }

    void includeHeaderFilter(@NonNull String ...includeHeaderFilter) {
        Collections.addAll(this.includeHeaderFilter, includeHeaderFilter);
    }

    void excludeStaticLibs(@NonNull String ...excludeStaticLibs) {
        Collections.addAll(this.excludeStaticLibs, excludeStaticLibs);
    }
}
