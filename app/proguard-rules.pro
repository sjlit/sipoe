# === Linphone SDK ===
# JNI / 反射密集,保留所有类与成员(SDK 也自带 consumer 规则,这里双保险)
-keep class org.linphone.** { *; }
-keep class org.linphone.mediastream.** { *; }
-dontwarn org.linphone.**

# === kotlinx.serialization (kotlinx-serialization-json 1.11.0) ===
# 需要 @Serializable 类的 Companion.serializer() 与生成的 *$$serializer
# 才能在 R8 / ProGuard 后仍被反射访问
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# 保留我们包内所有带 @Serializable 注解的类的 Companion 与字段
-keepclassmembers @kotlinx.serialization.Serializable class com.sipoe.softphone.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
    <fields>;
}
# 保留由序列化插件生成的 $serializer 类
-if @kotlinx.serialization.Serializable class com.sipoe.softphone.**
-keep class <1>$$serializer { *; }
# 保留 Companion(serializer() 是它的方法)
-if @kotlinx.serialization.Serializable class com.sipoe.softphone.**
-keep class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# === Kotlinx 协程 / 标准库 ===
-dontwarn kotlinx.coroutines.**
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# === Compose ===
# Compose 编译器会在编译期处理 @Composable;运行时无需额外 keep
-dontwarn androidx.compose.**

# === AndroidX DataStore ===
# PreferencesDataStore 是纯 Kotlin/Java,无需 keep

# === AndroidX / Lifecycle ===
-dontwarn androidx.lifecycle.**