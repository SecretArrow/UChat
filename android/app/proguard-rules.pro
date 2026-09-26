# UChat ProGuard/R8 rules

# JNI: keep native method names used by libuchat_pty.so
-keepclassmembers class com.uchat.android.linux.Pty {
    native <methods>;
}

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.uchat.android.**$$serializer { *; }
-keepclassmembers class com.uchat.android.** { *** Companion; }
-keepclasseswithmembers class com.uchat.android.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp platform warnings
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# WebView JavascriptInterface
-keepclassmembers class com.uchat.android.terminal.TerminalBridge {
    @android.webkit.JavascriptInterface <methods>;
}
