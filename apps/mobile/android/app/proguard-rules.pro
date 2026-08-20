# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ========================================
# CAPACITOR PROGUARD RULES
# ========================================

# Keep all Capacitor classes and methods
-keep class com.getcapacitor.** { *; }
-keep class com.capacitorjs.** { *; }

# Keep all plugin classes
-keep class * extends com.getcapacitor.Plugin { *; }
-keep class * implements com.getcapacitor.PluginMethod { *; }

# Keep WebView JavaScript interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep plugin method annotations
-keepattributes *Annotation*
-keep class com.getcapacitor.annotation.** { *; }

# Keep plugin registration
-keep class com.getcapacitor.PluginManager { *; }
-keep class com.getcapacitor.Bridge { *; }

# Keep specific plugins we use
-keep class com.capacitorjs.plugins.geolocation.** { *; }
-keep class com.capacitorjs.plugins.device.** { *; }
-keep class com.capacitorjs.plugins.app.** { *; }

# Keep Cordova plugins
-keep class org.apache.cordova.** { *; }

# Keep reflection-based access
-keepclassmembers class * {
    @com.getcapacitor.annotation.CapacitorPlugin <fields>;
    @com.getcapacitor.PluginMethod <methods>;
}

# Keep native method names
-keepclasseswithmembernames class * {
    native <methods>;
}

# WebView debugging
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}

-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
}
