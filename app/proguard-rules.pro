# Centinel Security App - R8 / Proguard Keep Rules

# Keep kotlinx.serialization data models
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    @kotlinx.serialization.Serializable <methods>;
}
-keepclassmembers class *$$serializer {
    *** INSTANCE;
}
-keepclassmembers class * {
    *** Companion;
}

# Keep Centinel Models
-keep class com.centinel.app.data.model.** { *; }

# Retrofit & OkHttp
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
