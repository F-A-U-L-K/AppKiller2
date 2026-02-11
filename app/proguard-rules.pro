# Default proguard rules for Android
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Material Design components
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Keep AppInfo data class
-keep class com.faulk.appkiller.AppInfo { *; }
