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

# ── QuickJS wrapper（源码内置 com.whl.quickjs.**）────────────────
# libquickjs.so 通过 JNI 按类名查找 JSArray / JSObject / QuickJSContext 等，
# R8 改名会导致 ClassNotFoundException → SIGABRT（quickjs 线程直接 abort）
-keep class com.whl.quickjs.** { *; }
-dontwarn com.whl.quickjs.**
