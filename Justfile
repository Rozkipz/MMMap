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

# Install release APK on a connected device or emulator
install variant='release':
    {{gradle}} install{{capitalize(variant)}}

# Install debug APK on a connected device or emulator
installdebug: (install "debug")

# Install debug build and launch (use `just run release` to install the release build)
run variant='debug': (install variant)
    #!/usr/bin/env bash
    PKG="{{package}}$([ "{{variant}}" = "debug" ] && echo ".debug" || true)"
    adb -d shell am start -n "$PKG/{{package}}.MainActivity"

# Delete all build outputs
clean:
    {{gradle}} clean

# ─── Quality ──────────────────────────────────────────────────────────────────

# Run unit tests
test:
    {{gradle}} testDebugUnitTest

# Run unit tests with JaCoCo coverage  →  app/build/reports/coverage/test/debug/index.html
coverage:
    {{gradle}} testDebugUnitTest createDebugUnitTestCoverageReport

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

# ─── Release ──────────────────────────────────────────────────────────────────

# Build a signed release APK; optionally override version name and code
# e.g. `just assemble-release 1.2.3 10203`  or plain `just assemble-release`
assemble-release version='' code='':
    {{gradle}} assembleRelease \
        {{ if version != '' { "-PversionName=" + version } else { "" } }} \
        {{ if code != '' { "-PversionCode=" + code } else { "" } }}

# Create a signed git tag and push it  →  e.g. `just tag 1.2.0`
tag version:
    git tag -s v{{version}} -m "Release v{{version}}"
    git push origin v{{version}}

# Create a GitHub Release and attach the signed APK
# Pass target SHA to pin the tag to a specific commit (e.g. $GITHUB_SHA in CI)
# Uses fastlane changelog if present, otherwise builds bullet-point notes from commits since last tag
gh-release version target='':
    #!/usr/bin/env bash
    set -euo pipefail
    APK="app/build/outputs/apk/release/app-release.apk"
    NOTES_FILE="fastlane/metadata/android/en-US/changelogs/$(cat .version-code 2>/dev/null || true).txt"
    TARGET_FLAG={{ if target != '' { '"--target ' + target + '"' } else { '""' } }}
    if [[ -f "$NOTES_FILE" ]]; then
      gh release create "v{{version}}" $TARGET_FLAG --title "v{{version}}" --notes-file "$NOTES_FILE" "$APK#Mmmap_v{{version}}.apk"
    else
      PREV=$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || git rev-list --max-parents=0 HEAD)
      CHANGELOG=$(git log "${PREV}..HEAD" --pretty=format:"- %s")
      gh release create "v{{version}}" $TARGET_FLAG --title "v{{version}}" --notes "$CHANGELOG" "$APK#Mmmap_v{{version}}.apk"
    fi

# Full release pipeline: clean → check → sign APK → tag → GitHub Release
# versionCode must be a strictly-increasing integer (e.g. 10000 for v1.0.0, 10001 for v1.0.1)
# e.g. `just release 1.2.3 10203`
release version code:
    just clean
    just check
    just assemble-release {{version}} {{code}}
    just tag {{version}}
    just gh-release {{version}}

# ─── Phase 2 (F-Droid) ────────────────────────────────────────────────────────

# Download the upstream MICHELIN CSV and regenerate app/src/main/assets/michelin.db
seed-db:
    python3 scripts/seed_db.py

# Validate F-Droid metadata YAML (run from inside a fdroiddata clone)
fdroid-lint path='../fdroiddata':
    fdroid lint --allow-disabled-algorithms {{path}}/metadata/{{package}}.yml

# Simulate a reproducible F-Droid build locally (requires fdroidserver + Docker)
fdroid-build-local path='../fdroiddata':
    fdroid build --on-server --no-tarball --verbose {{package}}

# ─── Utilities ────────────────────────────────────────────────────────────────

# Scan dependencies for proprietary libs (GMS/Firebase/Mapbox) — should print OK
deps-audit:
    {{gradle}} :app:dependencies | grep -Ei 'gms|firebase|mapbox' && echo "WARNING: proprietary deps found" || echo "OK: no proprietary deps"

# Generate a release signing keystore and write keystore.properties (run once, back up the .jks)
setup-keystore:
    #!/usr/bin/env bash
    set -euo pipefail
    KEYSTORE_FILE="mmmap-release.jks"
    PROPS_FILE="keystore.properties"

    if [[ -f "$KEYSTORE_FILE" ]]; then
        echo "$KEYSTORE_FILE already exists — delete it manually to regenerate."
        exit 1
    fi

    echo "Creating release keystore at $KEYSTORE_FILE"
    echo "You will be prompted for a keystore password."

    keytool -genkeypair \
        -v \
        -keystore "$KEYSTORE_FILE" \
        -keyalg RSA \
        -keysize 4096 \
        -validity 10000 \
        -alias mmmap \
        -dname "CN=Mmmap, O=Mmmap, C=GB"

    echo
    echo "Enter keystore password (same as above):"
    read -rs STORE_PASS
    echo "Enter key password (leave blank to reuse keystore password):"
    read -rs KEY_PASS
    KEY_PASS="${KEY_PASS:-$STORE_PASS}"

    printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=mmmap\nkeyPassword=%s\n' \
        "$KEYSTORE_FILE" "$STORE_PASS" "$KEY_PASS" > "$PROPS_FILE"

    echo
    echo "Written to $PROPS_FILE"
    echo "IMPORTANT: back up both $KEYSTORE_FILE and $PROPS_FILE — they are gitignored."
