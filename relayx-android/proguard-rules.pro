# RelayX Android Proguard / R8 Configuration

# Preserve line numbers and source file names for crash reporting
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlin Serialization
-keepattributes *Annotation*,Signature
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Koin Dependency Injection
-keep class * extends org.koin.core.module.Module
-keep class org.koin.** { *; }

# Ktor Client & OkHttp
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**

# WorkManager
-keep class * extends androidx.work.Worker {
    <init>(...);
}
-keep class * extends androidx.work.CoroutineWorker {
    <init>(...);
}

# Domain & Data models
-keep class com.parsomash.relayx.domain.model.** { *; }
-keep class com.parsomash.relayx.data.local.** { *; }
