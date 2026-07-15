# CLens Android MongoDB Client

Kotlin + Jetpack Compose mobile client for full-lifecycle MongoDB administration.

## Scope

* Connection profiles (URI or host form) with encrypted on-device secrets
* Database / collection CRUD and stats
* Document browse, insert, replace, update, delete
* Find / aggregate / explain console
* Index create/list/drop
* Server overview (`serverStatus` / `buildInfo` / `hostInfo`), users, current ops (best-effort)
* Synapse-matched Material 3 shell, crash report recovery, GitHub Actions signed release

## Layout

```text
android/                         # Gradle root
  app/src/main/java/com/chloemlla/clens/
    core/mongo/                  # URI builder, session, admin repository
    core/storage/                # encrypted connection store
    core/util/                   # host secret sanitizer for UI errors
    # lumen-crash SDK via GitHub Packages (capture + adaptive report UI)
    ui/                          # Compose tabs and panels
.github/workflows/clens-android.yml
.github/scripts/validate-clens-android-policy.py
```

## Toolchain parity with Synapse-Client

| Item | Value |
|------|-------|
| AGP | 8.13.2 |
| Kotlin | 2.1.20 |
| Compose BOM | 2024.12.01 |
| minSdk | 26 |
| compile/targetSdk | 37 (Android 17) |
| JDK | 21 |
| CI Gradle | 9.5.1 |
| Mongo driver | mongodb-driver-kotlin-coroutine 5.2.1 |

## Verification policy

Local machine build/test is prohibited by repository guidelines. Verification is defined only in:

* `.github/workflows/clens-android.yml`

Jobs:

1. `verify` — policy script, unit tests, lint, release assemble
2. `release` (main) — signed universal + ABI APKs, checksums, release manifest, GitHub Release

## Crash reporting

CLens consumes the published `com.chloemlla.lumen:lumen-crash` SDK from Project Lumen.

* Version policy: `android/lumen-crash.version`
  * `latest` (default) tracks the newest Project-Lumen **main auto-release** (`lumen-crash-vX.Y.Z-<shortSha>`)
  * pin an exact version with `0.1.0-<sha>` or a future stable `X.Y.Z`
  * runtime override: env `LUMEN_CRASH_SDK_VERSION`
* Resolved version file: `android/lumen-crash.resolved.version` (generated, gitignored)
* Coordinates: `com.chloemlla.lumen:lumen-crash:<resolved-version>`
* Preferred resolve path: GitHub Release assets materialized into `android/local-maven/` by `.github/scripts/fetch-lumen-crash-sdk.py`
* Fallback resolve path: GitHub Packages `https://maven.pkg.github.com/Chloemlla/Project-Lumen`
* Install early in `ClensApplication.attachBaseContext`
* Gate startup UI with `LumenCrashReportScreen`
* Host product copy overrides live in `res/values*/strings.xml`
* Host-side `SecretSanitizer` remains for Mongo/token redaction in action errors

CI always runs the fetch script before Gradle so `latest` is resolved at build time.

## Lumen Crash ProGuard exemption

Host release minify keeps the official third-party exemption block from `lumen-crash/README.md` in `android/app/proguard-rules.pro`:

* keep `CrashAuthorAttribution` constants and `AuthorIntegrity` entry points
* keep public SDK API used by host integration
* package-level keep for `com.chloemlla.lumen.crash.**`

This is required because author integrity is fail-closed under R8.

## Secrets

Release signing expects GitHub repository secrets:

* `KEYSTORE_BASE64`
* `KEYSTORE_PASSWORD`
* `KEY_ALIAS`
* `KEY_PASSWORD`

Use `setup-android-signing.ps1` to generate and push these values.

GitHub Packages resolution for `lumen-crash` expects either:

* same-account package read via `GITHUB_TOKEN` (when permitted), or
* repository secret `LUMEN_CRASH_READ_PACKAGES_TOKEN` with `read:packages` for the Project Lumen package

## Release minify notes

* Mongo driver jars include an optional Netty stream factory. CLens uses the default NIO `MongoClientSettings` path only.
* ProGuard keeps `com.mongodb.**` while excluding `com.mongodb.internal.connection.netty.**` so R8 does not retain the untyped Netty constructor warning during `minifyProductionReleaseWithR8`.
* Optional Reactor / Micrometer / BlockHound service metadata is excluded from packaging and silenced with `-dontwarn`.

## Cleartext MongoDB

By default the app denies cleartext traffic. Non-TLS Mongo profiles surface an in-app warning and should only be used on trusted networks. Prefer TLS for any shared or production environment.

## Known CI problems-report noise

Gradle 9.5.1 with AGP 8.13.2 may write problems-report.html entries such as:

- Declaring dependencies using multi-string notation has been deprecated
  - com.android.tools.lint:lint-gradle:31.13.2
  - com.android.tools.build:aapt2:8.13.2-...

These warnings are emitted by AGP plugin code (com.android.internal.application), not by CLens uild.gradle.kts dependencies (which already use single-string notation). They are safe to ignore for now and become hard errors only on Gradle 10 unless AGP ships a fix first. Keep AGP/Gradle pins aligned with Synapse-Client unless deliberately upgrading the toolchain.

## Advanced tab

The **高级** tab adds:

* GridFS list/upload(text)/download(text)/delete for bucket `fs` (customizable)
* Change Stream watch for the currently selected collection (requires replica set / sharded cluster)
* User/role create & drop (best-effort; privilege errors are shown)
* Import JSON array into the selected collection and export collection JSON with limit

## Next enhancements

* Query history (local recent find/aggregate restore)
* killOp for structured current operations
* Collection validator view/update via collMod
* Readonly connection profiles that block writes
* Local destructive-operation audit log in Advanced tab


