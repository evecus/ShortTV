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
