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

# Install APK on a connected device or emulator (release by default; pass 'debug' for a debug build)
install variant='release':
    {{gradle}} install{{capitalize(variant)}}

# Shorthand for `just install debug`
installdebug: (install "debug")

# Install and launch the app (requires adb + connected device)
run variant='release': (install variant)
    adb shell am start -n {{package}}/.MainActivity

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
# e.g. `just assemble-release 1.2 10200`  or plain `just assemble-release`
# When version is given without code, derives code = MAJOR*10000 + MINOR*100.
assemble-release version='' code='':
    #!/usr/bin/env bash
    set -euo pipefail
    VERSION="{{version}}"
    CODE="{{code}}"
    ARGS=()
    if [[ -n "$VERSION" ]]; then
        if ! [[ "$VERSION" =~ ^([0-9]+)\.([0-9]+)$ ]]; then
            echo "error: version must be MAJOR.MINOR (got '$VERSION')" >&2
            exit 1
        fi
        ARGS+=("-PversionName=$VERSION")
        if [[ -z "$CODE" ]]; then
            CODE=$(( BASH_REMATCH[1] * 10000 + BASH_REMATCH[2] * 100 ))
        fi
    fi
    if [[ -n "$CODE" ]]; then
        ARGS+=("-PversionCode=$CODE")
    fi
    {{gradle}} assembleRelease "${ARGS[@]}"

# Create a signed git tag and push it  →  e.g. `just tag 1.2`
tag version:
    #!/usr/bin/env bash
    set -euo pipefail
    if ! [[ "{{version}}" =~ ^[0-9]+\.[0-9]+$ ]]; then
        echo "error: version must be MAJOR.MINOR (got '{{version}}')" >&2
        exit 1
    fi
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

# Bump gradle.properties to match the given MAJOR.MINOR version (no commit)
# F-Droid builds without -P flags, so the source-of-truth versionName/versionCode
# must live in gradle.properties — git tags pin commits, not gradle args.
bump-version version:
    #!/usr/bin/env bash
    set -euo pipefail
    if ! [[ "{{version}}" =~ ^([0-9]+)\.([0-9]+)$ ]]; then
        echo "error: version must be MAJOR.MINOR (got '{{version}}')" >&2
        exit 1
    fi
    CODE=$(( BASH_REMATCH[1] * 10000 + BASH_REMATCH[2] * 100 ))
    sed -i.bak "s/^versionName=.*/versionName={{version}}/" gradle.properties
    sed -i.bak "s/^versionCode=.*/versionCode=$CODE/" gradle.properties
    rm -f gradle.properties.bak
    echo "bumped: versionName={{version}} versionCode=$CODE"

# Full release pipeline: bump → clean → check → sign APK → commit bump → tag → GitHub Release
release version:
    #!/usr/bin/env bash
    set -euo pipefail
    just bump-version {{version}}
    just clean
    just check
    just assemble-release {{version}}
    git add gradle.properties
    if ! git diff --cached --quiet; then
        git commit -m "chore(release): v{{version}}"
        git push origin HEAD
    fi
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
