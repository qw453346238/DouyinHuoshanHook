# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Xposed Hook classes
-keep class com.douyin.huoshan.hook.** { *; }
-keep class de.robv.android.xposed.** { *; }

# Keep classes that might be accessed via reflection
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep Gson classes
-keep class com.google.gson.** { *; }
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}