# Release obfuscation is intentionally boundary-focused.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-allowaccessmodification
-repackageclasses c

# Android framework entry points declared in AndroidManifest.xml.
-keep,allowoptimization class com.chloemlla.clens.MainActivity {
    public <init>();
    public <methods>;
    protected <methods>;
}
-keep,allowoptimization class com.chloemlla.clens.ClensApplication {
    public <init>();
    public <methods>;
    protected <methods>;
}

# FileProvider is referenced directly from the manifest and its XML metadata.
-keep,allowoptimization class androidx.core.content.FileProvider {
    public <init>();
}

# Enum helpers may be used by framework/runtime code and string comparisons.
-keepclassmembers enum com.chloemlla.clens.** {
    public static final <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# AndroidX Security Crypto is used for encrypted connection secrets.
-dontwarn androidx.security.crypto.**

# MongoDB Java driver uses reflection/service loading heavily.
# Keep the driver surface, but exclude the optional Netty transport.
# CLens always builds MongoClientSettings without StreamFactoryFactory, so the
# default NIO socket factory is used. A broad com.mongodb.** keep forces R8 to
# retain NettyStreamFactoryFactory even though Netty is not on the Android
# classpath, which produces:
#   "NettyStreamFactoryFactory.<init>(Builder) does not type check..."
-keep class !com.mongodb.internal.connection.netty.**, com.mongodb.** { *; }
-keep class org.bson.** { *; }
-dontwarn com.mongodb.**
-dontwarn org.bson.**
-dontwarn com.mongodb.internal.connection.netty.**
-dontwarn io.netty.**
-dontwarn javax.naming.**
-dontwarn javax.net.ssl.**
-dontwarn org.slf4j.**
-dontwarn org.ietf.jgss.**

# Compose and lifecycle warnings are dependency-internal.
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**

# AndroidX Security Crypto depends on Tink; annotation-only and optional API refs are not on Android.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**

# MongoDB reactive stack may pull optional Reactor / Micrometer / BlockHound service metadata.
# These classes are not required for the coroutine driver path used by CLens.
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
