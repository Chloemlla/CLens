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
# Keep the full driver surface, including nested SASL authenticator classes.
# CLens never configures StreamFactoryFactory, so runtime uses default NIO.
# Do NOT use a negation keep filter here: excluding
# com.mongodb.internal.connection.netty.** has been observed to leave SCRAM
# handshake classes (SaslAuthenticator$SaslClientImpl) missing at runtime
# under R8 full mode, causing:
#   NoClassDefFoundError: com.mongodb.internal.connection.SaslAuthenticator$SaslClientImpl
# A retained optional Netty constructor may only produce a benign R8 type-check
# warning during minify; that is preferable to an auth-path crash.
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
-dontwarn javax.security.sasl.**

# Android lacks the JDK SASL module. CLens ships minimal stubs under
# android/app/src/main/java/javax/security/sasl so Mongo SCRAM classes can load.
-keep class javax.security.sasl.** { *; }
-keep interface javax.security.sasl.** { *; }

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
