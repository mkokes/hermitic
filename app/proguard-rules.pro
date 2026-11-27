# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.martronics.hermitic.core.network.dto.**$$serializer { *; }
-keepclassmembers class com.martronics.hermitic.core.network.dto.** {
    *** Companion;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
