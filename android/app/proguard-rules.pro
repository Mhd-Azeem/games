# Keep WebView JS interface
-keepclassmembers class com.neonracers.MainActivity$AndroidBridge {
    public *;
}
-keepattributes JavascriptInterface

# Keep Activities
-keep class com.neonracers.** { *; }

# WebView
-keep public class android.webkit.** { *; }
