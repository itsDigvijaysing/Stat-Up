# Stat Up v3.1 ProGuard Rules
# Kotlin + Compose + Room + Ktor + Koin

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.statup.app.**$$serializer { *; }
-keepclassmembers class dev.statup.app.** {
    *** Companion;
}
-keepclasseswithmembers class dev.statup.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Room entities and DAOs
-keep class dev.statup.app.data.local.db.entity.** { *; }
-keep class dev.statup.app.data.local.db.dao.** { *; }
-keep class dev.statup.app.data.local.db.AppDatabase { *; }

# Keep domain models
-keep class dev.statup.app.domain.model.** { *; }

# Ktor
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }

# WorkManager - workers are instantiated reflectively by the default WorkerFactory
-keep class dev.statup.app.sync.DecayWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep class dev.statup.app.sync.TodoistSyncWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# Security Crypto / Tink (EncryptedSharedPreferences)
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-keep class androidx.security.crypto.** { *; }

# Koin
# Koin 3.x resolves dependencies through the explicit `single { }` / `viewModel { }` lambdas in
# AppModule.kt - constructor reflection is never used, so no app-wide constructor keep is needed.
# ViewModels are kept because androidx.lifecycle instantiates them reflectively.
-keepnames class androidx.lifecycle.ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Compose
# Do NOT add `-keep class androidx.compose.** { *; }` here. Compose ships its own consumer
# ProGuard rules, and a blanket keep defeats R8 across the whole toolkit - most visibly it
# retains every icon in material-icons-extended (11,408 classes / ~15 MB of DEX) when the app
# references about 25 of them. Measured 2026-08-17: removing it cut the release AAB from
# 25.3 MB to the size recorded in CLAUDE.md.
-dontwarn androidx.compose.**

# DataStore
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# General Android
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
