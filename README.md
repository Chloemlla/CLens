# CLens

Android Kotlin client for full MongoDB database administration.

## Product

CLens is a mobile MongoDB management console:

* manage encrypted connection profiles
* browse and mutate databases, collections, and documents
* run find / aggregate / explain
* manage indexes and inspect server status

The Android app intentionally mirrors the UI/UX language, Gradle toolchain, dependency pins, crash-reporting posture, and GitHub auto-release pipeline of [Synapse-Client](https://github.com/Chloemlla/Synapse-Client).

## Repository layout

```text
android/                         # Kotlin app (Gradle Kotlin DSL, Jetpack Compose)
docs/android-mongodb-client.md   # product/engineering notes
.github/workflows/clens-android.yml
```

## Requirements

| Item | Value |
|------|--------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Min SDK | 26 |
| Compile / Target SDK | 37 (Android 17) |
| JDK (CI) | 21 |
| Gradle (CI) | 9.5.1 |
| MongoDB driver | kotlin-coroutine 5.2.1 |

## Continuous integration

Workflow: [`.github/workflows/clens-android.yml`](.github/workflows/clens-android.yml)

* unit tests
* Android lint
* release APK assemble
* signed GitHub Release on `main` (requires keystore secrets)

Local build/test is intentionally not part of the development workflow for this repository.
