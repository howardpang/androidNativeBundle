## NativeBundle
nativeBundle plugin is a gradle plugin that extend  *bundle* task provided by android gradle plugin,it can help you publish c/c++ headers and other module that contain native source can dependent those module directly
- android gradle plugin 3.0.0 - 8.0.2

## Build and Test
1.Android studio import this project  
2.Enter 'gradlew publishToMavenLocal' command in Terminal or click 'publishToMavenLocal' task in gradle task list  
3.Open *settings.gradle*, include 'app' project and build it  

## Usage
### 1.Edit your root *build.gradle* file, add  *classpath 'io.github.howardpang:androidNativeBundle:1.1.5'* to the file
    buildscript {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath 'com.android.tools.build:gradle:3.0.0'
            //Add androidNativeBundle dependency
            classpath "io.github.howardpang:androidNativeBundle:1.1.5"
        }
    }
### 2. Export header to aar
##### 1. Apply plugin to your lib, add following line to your lib *build.gradle*
    apply plugin: 'com.android.library'
    apply plugin: 'com.ydq.android.gradle.native-aar.export' // must below android gradle plugin
##### 2. Specify header path that you want to export, add following code segment to your lib *build.gradle*;
    nativeBundleExport {
        headerDir = "${project.projectDir}/src/main/jni/include"
        //excludeHeaderFilter.add("**/**.cpp")
        //includeHeaderFilter.add("**/**.h")
        //bundleStatic = true
        //extraStaticLibDir = "${project.projectDir}/xx"
        //excludeStaticLibs.add("**/libmylib.a")
        //excludeStaticLibs.add("**/libxx.a")
    }
    
##### 3.The plugin also support flavours feature, you can add this code to flavour configure, like this:
    productFlavors {
        flavorDimensions "default"
        export {
            dimension "default"
            nativeBundleExport {
                headerDir = "${project.projectDir}/src/main/jni/include"
                //excludeHeaderFilter.add("**/**.cpp")
                //includeHeaderFilter.add("**/**.h")
                //bundleStatic = true
                //extraStaticLibDir = "${project.projectDir}/xx"
                //excludeStaticLibs.add("**/libmylib.a")
                //excludeStaticLibs.add("**/libxx.a")
            }
        }
    }
    
##### 4.Because publish more than one static library will cause *link order* problem, so you can specify link order, for example libxx.a should link before libyy.a, like this:
    nativeBundleExport {
        headerDir = "${project.projectDir}/src/main/jni/include"
        //excludeHeaderFilter.add("**/**.cpp")
        //includeHeaderFilter.add("**/**.h")
        bundleStatic = true
        //extraStaticLibDir = "${project.projectDir}/xx"
        //excludeStaticLibs.add("**/libmylib.a")
        //excludeStaticLibs.add("**/libxx.a")
        linkOrder = "libxx.a:libyy.a"
    }

##### 5. Android lib only packet shared library(so) to aar, this plugin can generate another aar to packet static library ,if you set 'bundleStatic',the plugin will gather static lib from 'externalNativeBuild' output dir default, you can specify other dir by set 'extraStaticLibDir' in 'nativeBundleExport', you can also exclude some static lib; <br>Default, the plugin will create a "bundleStaticLibRelease" task to packet the static bundle, but if you use flavours feature, the plugin will create "bundleStaticLib${flavourName}Release" for every flavour;<br>After you configure, you can add static publication to your publish script, like this:
    publishing {
        publications {
            maven(MavenPublication) {
                groupId 'com.ydq.android.native-aar'
                artifactId "mylib"
                artifact bundleRelease
            }

            mavenStaticBundle(MavenPublication) {
                groupId 'com.ydq.android.native-aar'
                artifactId "mylib-static"
                artifact bundleStaticLibRelease
            }
        }
    }
    
### 3. Import aar
##### 1. Apply plugin to your lib/app, add following line to your lib/app *build.gradle*
    apply plugin: 'com.android.application'
    apply plugin: 'com.ydq.android.gradle.native-aar.import' // must below android gradle plugin
    
##### 2. If you use 'externalNativeBuild' to build, there are two ways to build
* ndkBuild: Add this line <br>*include ${ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK} #must be before "include $(BUILD_SHARED_LIBRARY)" or "include $(BUILD_STATIC_LIBRARY)"* <br>to every module that you want in *Android.mk*, like this

``` 
include $(CLEAR_VARS)
LOCAL_SRC_FILES := myapp.cpp \
LOCAL_MODULE := myapp
LOCAL_LDLIBS += -llog
include ${ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK} #must followed by "include $(BUILD_SHARED_LIBRARY)" or "include $(BUILD_STATIC_LIBRARY)"
include $(BUILD_SHARED_LIBRARY)
```` 

* cmake: Add this line <br> *include (${ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK})* <br> to *CMakeLists.txt*; Then link modules if the target needed <br> *target_link_libraries(\<target\> ${ANDROID_GRADLE_NATIVE_MODULES})* <br> like this

````
cmake_minimum_required(VERSION 3.4.1)
project(echo LANGUAGES C CXX)
add_library(myapp
  SHARED
    myapp.cpp)
target_link_libraries(myapp
    log
    )
include (${ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK})
target_link_libraries(myapp ${ANDROID_GRADLE_NATIVE_MODULES})
target_compile_options(myapp
  PRIVATE
    -Wall -Werror)
````

##### 3. If you use custom command ndk-build
* Add following line to every module you want like "externalNativeBuild:ndkBuild"
* Add macro <br> *"ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK=${nativeBundleImport.ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK}"* <br>to your ndk-build, like this

````
def execmd = ["$ndkbuildcmd", "-j${coreNum}", "V=1", "NDK_PORJECT_PATH=$buildDir",
                          "APP_BUILD_SCRIPT=$androidMKfile", "NDK_APPLICATION_MK=$applicationMKfile", "ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK=${nativeBundleImport.ANDROID_GRADLE_NATIVE_BUNDLE_PLUGIN_MK}"]
````

##### 4. If you want to link some static library with whole archive, you can set it in *build.gradle* like this
    nativeBundleImport {
        wholeStaticLibs = "libxx.a:libyy.a" // Library is seperated by colon
    }
    
##### 5. If your module have below dependency , the plugin will put the so in to 'aar' and add it to native compile 
    dependencies {
        implementation "com.my.group:module:1.0.0:armeabi-v7a@so"
        implementation "com.my.group:module:1.0.0:armeabi-v7a@har" // contain 'headers'
    }
    
##### 6. If you don't want to add some dependency to native compile, for example, you have 'implementation "com.my.group:moduleA:1.0.0"  ' dependency and it contain native interface, you can do like this
    nativeBundleImport {
        //wholeStaticLibs = "libxx.a:libyy.a" // Library is seperated by colon
        excludeDependencies.add("com.my.group:moduleA")
        excludeDependencies.add("com.my.group:moduleB")
    }
    
##### 7. The plugin will extract headers(include) and library to *"${project.projectDir}/build/nativeLib/"*, you can find what interfaces that aar contain 
##### 8. If android studio IDE can't parse those headers when you edit c/c++ source and press 'Sync Project With Gradle Files' button to re-sync project

