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

# MMKV loads native code and stores preference values by stable keys.
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# AndroidX Security Crypto is used for encrypted connection secrets.
-dontwarn androidx.security.crypto.**

# MongoDB Java driver uses reflection/service loading heavily.
-keep class com.mongodb.** { *; }
-keep class org.bson.** { *; }
-dontwarn com.mongodb.**
-dontwarn org.bson.**
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

