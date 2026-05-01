# Keep generated kotlinx.serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.minenergo.monitor.**$$serializer { *; }
-keepclassmembers class com.minenergo.monitor.** {
    *** Companion;
}
-keepclasseswithmembers class com.minenergo.monitor.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# junrar
-dontwarn com.github.junrar.**
-keep class com.github.junrar.** { *; }

# Apache Commons Compress
-dontwarn org.apache.commons.compress.**
-keep class org.tukaani.xz.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
