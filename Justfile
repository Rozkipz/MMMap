package := "app.mmmap"
gradle  := "./gradlew"
java17  := env_var_or_default("JAVA_HOME", env_var("HOME") + "/.local/jdk/jdk-17.0.19+10")

export JAVA_HOME := java17

# List all available commands
default:
    @just --list

# ─── Dev ──────────────────────────────────────────────────────────────────────

# Build a debug APK  →  app/build/outputs/apk/debug/
build:
    {{gradle}} assembleDebug

# Build and install the debug APK on a connected device or emulator
install:
    {{gradle}} installDebug

# Install and launch the app (requires adb + connected device)
run: install
    adb shell am start -n {{package}}/.MainActivity

# Delete all build outputs
clean:
    {{gradle}} clean

# ─── Quality ──────────────────────────────────────────────────────────────────

# Run unit tests
test:
    {{gradle}} testDebugUnitTest

# Run instrumented tests on a connected device or emulator
test-instrumented:
    {{gradle}} connectedDebugAndroidTest

# Run Android lint checks
lint:
    {{gradle}} lintDebug

# Auto-format Kotlin source with ktlint
format:
    {{gradle}} ktlintFormat

# Run lint + unit tests — do this before committing
check: lint test

# ─── Data ─────────────────────────────────────────────────────────────────────

# Download the latest michelin-my-maps CSV and rebuild app/src/main/assets/michelin.db (no-op if already current)
fetch-michelin-data:
    ./scripts/fetch-michelin-db.sh

# ─── Release ──────────────────────────────────────────────────────────────────

# Build a signed release APK (run `just setup-keystore` first if needed)
assemble-release:
    {{gradle}} assembleRelease

# Bump versionName/versionCode — part is patch (default), minor, or major
bump part="patch":
    ./scripts/bump-version.sh {{part}}

# Create a signed git tag and push it  →  e.g. `just tag 1.2.0`
tag version:
    git tag -s v{{version}} -m "Release v{{version}}"
    git push origin v{{version}}

# Create a GitHub Release and attach the signed APK (reads notes from fastlane/metadata/…/changelogs/)
gh-release version:
    #!/usr/bin/env bash
    set -euo pipefail
    NOTES="fastlane/metadata/android/en-US/changelogs/$(cat .version-code 2>/dev/null || echo 1).txt"
    APK="app/build/outputs/apk/release/app-release.apk"
    gh release create "v{{version}}" \
      --title "v{{version}}" \
      --notes-file "$NOTES" \
      "$APK"

# Full release pipeline: clean → fetch data → check → sign APK → tag → GitHub Release
release version:
    just clean
    just fetch-michelin-data
    just check
    just assemble-release
    just tag {{version}}
    just gh-release {{version}}

# ─── Phase 2 (F-Droid + IzzyOnDroid — deferred) ──────────────────────────────
# fdroid-lint:        # Validate F-Droid metadata YAML against fdroiddata rules
#     fdroid lint metadata/{{package}}.yml
# fdroid-build-local: # Simulate an F-Droid reproducible build locally
#     fdroid build --on-server --no-tarball {{package}}
# fdroid-pr version:  # Open a PR against the fdroiddata repo
#     ./scripts/open-fdroiddata-pr.sh {{version}}

# ─── Utilities ────────────────────────────────────────────────────────────────

# Scan dependencies for proprietary libs (GMS/Firebase/Mapbox) — should print OK
deps-audit:
    {{gradle}} :app:dependencies | grep -Ei 'gms|firebase|mapbox' && echo "WARNING: proprietary deps found" || echo "OK: no proprietary deps"

# Generate a release signing keystore and write keystore.properties (run once, back up the .jks)
setup-keystore:
    ./scripts/create-keystore.sh
