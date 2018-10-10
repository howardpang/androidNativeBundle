# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in E:\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}
#-applymapping ../mapping.txt

-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers
-dontpreverify
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-dontwarn android.support.v4.**
-keep, allowobfuscation class android.support.v4.** {*;}
#-keep,allowobfuscation class android.support.annotation.** {*;}
-keep, allowobfuscation class com.yy.mobile.qupaishenqu.ui.shenqu.ShenquCommunityFragment {
    void refreshData();
}

-keep class howard.myapplication.MainActivity {*;}
#-keep class howard.myapplication.Hao {*;}
-keep public @interface howard.mylibrarya.Export
-keep @howard.mylibrarya.Export class * { *; }

