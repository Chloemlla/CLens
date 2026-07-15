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
    core/crash/                  # crash capture + sanitizer
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
| compile/targetSdk | 37 |
| JDK | 21 |
| CI Gradle | 9.5.1 |
| Mongo driver | mongodb-driver-kotlin-coroutine 5.2.1 |

## Verification policy

Local machine build/test is prohibited by repository guidelines. Verification is defined only in:

* `.github/workflows/clens-android.yml`

Jobs:

1. `verify` — policy script, unit tests, lint, release assemble
2. `release` (main) — signed universal + ABI APKs, checksums, release manifest, GitHub Release

## Secrets

Release signing expects GitHub repository secrets:

* `KEYSTORE_BASE64`
* `KEYSTORE_PASSWORD`
* `KEY_ALIAS`
* `KEY_PASSWORD`

Use `setup-android-signing.ps1` to generate and push these values.
