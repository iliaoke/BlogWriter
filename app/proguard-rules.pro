# ===== kotlinx.serialization =====
# 序列化用到的 @Serializable data class 及其伴生 serializer() 需要保留，
# 否则混淆后反射找不到字段/serializer 会在运行时崩溃
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.blog.writer.**$$serializer { *; }
-keepclassmembers class com.blog.writer.** {
    *** Companion;
}
-keepclasseswithmembers class com.blog.writer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.blog.writer.** { *; }

# ===== Retrofit / OkHttp =====
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature, Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep class retrofit2.Response
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ===== compose-markdown（内部基于 Markwon，用了少量反射/服务发现） =====
-keep class dev.jeziellago.compose.markdowntext.** { *; }
-dontwarn dev.jeziellago.compose.markdowntext.**
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**

# ===== Compose 编译期已经会自动生成必要的 keep 规则，这里不需要重复声明 =====
