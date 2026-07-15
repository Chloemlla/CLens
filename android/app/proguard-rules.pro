# Release minify rules for CLens.
# Prefer keep-by-package for app/Compose/third-party reflection surfaces.
# Do NOT use -repackageclasses or allowoptimization on Application/Activity:
# those settings have caused blank-screen startup failures under R8 full mode.

-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, AnnotationDefault
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# -----------------------------------------------------------------------------
# App entry points + full app package
# -----------------------------------------------------------------------------

# Manifest components and everything they reach into the app package.
-keep class com.chloemlla.clens.MainActivity { <init>(); }
-keep class com.chloemlla.clens.ClensApplication { <init>(); }
-keep class com.chloemlla.clens.** { *; }
-keepclassmembers class com.chloemlla.clens.** {
    <init>(...);
    public <methods>;
    public <fields>;
}
-keepclassmembers enum com.chloemlla.clens.** {
    public static final <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlin metadata / coroutines used across ViewModels and Mongo session code.
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# FileProvider is referenced from the manifest and share path XML metadata.
-keep class androidx.core.content.FileProvider {
    <init>();
    public <methods>;
}

# -----------------------------------------------------------------------------
# Compose / lifecycle / ViewModel
# -----------------------------------------------------------------------------

# Keep Compose runtime and compiler-generated classes for setContent startup.
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.ComponentActivity { *; }
-keep class androidx.activity.compose.** { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * implements androidx.lifecycle.ViewModelProvider$Factory { *; }
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**

# -----------------------------------------------------------------------------
# Encrypted connection storage (Security Crypto + Tink)
# -----------------------------------------------------------------------------

-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn androidx.security.crypto.**
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**

# -----------------------------------------------------------------------------
# MongoDB driver + Android SASL stubs
# -----------------------------------------------------------------------------

# MongoDB Java driver uses reflection/service loading heavily.
# Keep the full driver surface, including nested SASL authenticator classes.
# CLens never configures StreamFactoryFactory, so runtime uses default NIO.
# Do NOT use a negation keep filter here: excluding
# com.mongodb.internal.connection.netty.** previously correlated with release
# NoClassDefFoundError for SaslAuthenticator$SaslClientImpl during SCRAM.
-keep class com.mongodb.** { *; }
-keep class org.bson.** { *; }
-keep class com.mongodb.internal.connection.SaslAuthenticator { *; }
-keep class com.mongodb.internal.connection.SaslAuthenticator$* { *; }
-keep class com.mongodb.internal.connection.**Authenticator* { *; }
-keep class com.mongodb.internal.connection.**Authenticator$* { *; }
-keep class com.mongodb.internal.authentication.** { *; }
-keepnames class com.mongodb.internal.connection.SaslAuthenticator
-keepnames class com.mongodb.internal.connection.SaslAuthenticator$*
-keepnames class com.mongodb.internal.connection.DefaultAuthenticator
-dontwarn com.mongodb.**
-dontwarn org.bson.**
-dontwarn com.mongodb.internal.connection.netty.**
-dontwarn io.netty.**
-dontwarn javax.naming.**
-dontwarn javax.net.ssl.**
-dontwarn org.slf4j.**
-dontwarn org.ietf.jgss.**

# Android lacks the JDK SASL module. CLens ships minimal Kotlin stubs under
# android/app/src/main/java/javax/security/sasl so Mongo SCRAM classes can load.
# Keep them; do not dontwarn them away.
-keep class javax.security.sasl.** { *; }
-keep interface javax.security.sasl.** { *; }
-keepclassmembers class javax.security.sasl.** { *; }

# MongoDB reactive stack may pull optional Reactor / Micrometer / BlockHound
# service metadata. Not required for the coroutine driver path used by CLens.
-dontwarn reactor.**
-dontwarn io.micrometer.**
-dontwarn reactor.blockhound.**
-dontwarn reactor.blockhound.integration.**

############################################################
# Lumen Crash SDK minify exemption
# Artifact: com.chloemlla.lumen:lumen-crash
# Source: lumen-crash/README.md "Required third-party minify exemption"
# Put this in the host app proguard-rules.pro
############################################################

# Required: author attribution + integrity checks
-keep class com.chloemlla.lumen.crash.CrashAuthorAttribution {
    public static final java.lang.String *;
}
-keep class com.chloemlla.lumen.crash.AuthorIntegrity {
    public static *** verifyOrThrow();
    public static *** fingerprintHex();
}

# Required backup: keep public SDK API used by host integration
-keep class com.chloemlla.lumen.crash.LumenCrash { *; }
-keep class com.chloemlla.lumen.crash.LumenCrashConfig { *; }
-keep class com.chloemlla.lumen.crash.CrashReport { *; }
-keep class com.chloemlla.lumen.crash.CrashAppInfo { *; }
-keep class com.chloemlla.lumen.crash.CrashReportStore { *; }
-keep class com.chloemlla.lumen.crash.CrashBreadcrumbs { *; }
-keep class com.chloemlla.lumen.crash.ui.LumenCrashReportScreenKt { *; }

# Package-level exemption (safe default for third-party hosts)
-keep class com.chloemlla.lumen.crash.** { *; }
-dontwarn com.chloemlla.lumen.crash.**
