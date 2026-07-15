# CLens Android

Kotlin MongoDB management client.

Open the `android/` directory in Android Studio.

## Features

* Encrypted multi-profile connections
* Database / collection / document administration
* Find, aggregate, explain
* Index and server admin panels
* Material 3 UI language matched to Synapse Mobile
* Crash report capture and share

## Verification

Repository policy prohibits local Gradle build/test commands. Use GitHub Actions workflow `.github/workflows/clens-android.yml`.

## CI problems report

If GitHub Actions uploads a problems-report.html with multi-string dependency deprecations for apt2 / lint-gradle, those come from AGP internals under Gradle 9.5 and are not app dependency declarations. See docs/android-mongodb-client.md.

