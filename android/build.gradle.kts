// AGP 8.13.2 bundles the R8 8.13 line, which cannot read Kotlin 2.4 metadata and floods every
// release build with "An error occurred when parsing kotlin metadata" (once per affected class).
// Kotlin 2.4 requires R8 9.1.29 or newer with AGP 8.5.2+, so raise the bundled compiler here.
// https://developer.android.com/studio/build/kotlin-d8-r8-versions
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools:r8:9.1.43")
    }
}

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
}
