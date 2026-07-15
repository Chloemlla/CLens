from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
ERRORS: list[str] = []


def read_text(relative_path: str) -> str:
    path = ROOT / relative_path
    try:
        return path.read_text(encoding="utf-8")
    except FileNotFoundError:
        ERRORS.append(f"Missing required file: {relative_path}")
        return ""


def require_file(relative_path: str) -> None:
    if not (ROOT / relative_path).is_file():
        ERRORS.append(f"Missing required file: {relative_path}")


def require_contains(relative_path: str, expected: str) -> None:
    if expected not in read_text(relative_path):
        ERRORS.append(f"{relative_path} must contain: {expected}")


for required in (
    "android/gradlew",
    "android/gradle/wrapper/gradle-wrapper.properties",
    "android/settings.gradle.kts",
    "android/build.gradle.kts",
    "android/app/build.gradle.kts",
    "android/app/src/main/AndroidManifest.xml",
    "android/app/src/main/java/com/chloemlla/clens/MainActivity.kt",
    "android/app/proguard-rules.pro",
    ".github/workflows/clens-android.yml",
):
    require_file(required)

main_source_root = ROOT / "android/app/src/main"
java_files = sorted(path.relative_to(ROOT).as_posix() for path in main_source_root.rglob("*.java"))
if java_files:
    ERRORS.append("Android main source must stay Kotlin-only; Java files found: " + ", ".join(java_files))

require_contains("android/app/src/main/AndroidManifest.xml", 'android:allowBackup="false"')
require_contains("android/app/build.gradle.kts", "isMinifyEnabled = true")
require_contains("android/app/build.gradle.kts", "isShrinkResources = true")
require_contains("android/app/build.gradle.kts", 'getDefaultProguardFile("proguard-android-optimize.txt")')
require_contains("android/app/build.gradle.kts", 'applicationId = "com.chloemlla.clens"')
require_contains("android/app/build.gradle.kts", 'create("production")')
require_contains("android/app/build.gradle.kts", "mongodb-driver-kotlin-coroutine")
require_contains("android/app/build.gradle.kts", "compileSdk = 37")
require_contains("android/app/build.gradle.kts", "targetSdk = 37")
require_contains(".github/workflows/clens-android.yml", "./gradlew testProductionDebugUnitTest")
require_contains(".github/workflows/clens-android.yml", "./gradlew lintProductionDebug")
require_contains(".github/workflows/clens-android.yml", "./gradlew assembleProductionRelease")

if ERRORS:
    for error in ERRORS:
        print(f"::error::{error}")
    sys.exit(1)

print("CLens Android policy checks passed.")
