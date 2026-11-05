# Keep Kotlin metadata for reflection-based serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.shorttv.app.data.model.** {
    *** Companion;
    <fields>;
}
-keepclasseswithmembers class com.shorttv.app.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# kotlinx.serialization 兜底：所有 @Serializable 类（不止 data.model 包）
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    <fields>;
}
-keep,includedescriptorclasses class com.shorttv.app.**$$serializer { *; }
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
