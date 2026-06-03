# Building Mmmap

## Prerequisites

| Tool | Version |
|---|---|
| JDK | 17 (OpenJDK, e.g. Eclipse Temurin 17.0.x) |
| Android SDK | platform-36 + build-tools-36.x |
| Android Gradle Plugin | 9.2.1 (declared in `build.gradle.kts`) |
| Gradle wrapper | ships in repo (`./gradlew`) |

`JAVA_HOME` can be set manually; the `Justfile` defaults it to `~/.local/jdk/jdk-17.0.19+10`.

No Google Play Services, Firebase, or proprietary SDK dependencies.

## Standard debug build

```sh
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Release build (unsigned)

```sh
./gradlew assembleRelease \
    -PversionName=<version>   \
    -PversionCode=<code>
```

Output: `app/build/outputs/apk/release/app-release.apk`

When `keystore.properties` is absent (e.g. on a build server), the release APK is produced unsigned. F-Droid's build server intentionally omits this file and signs with its own key, or verifies a byte-match against a developer-signed APK for the `AllowedAPKSigningKeys` path.

## versionCode convention

`versionCode = MAJOR * 10000 + MINOR * 100`

For example, `1.0` → `10000`, `1.2` → `10200`, `2.3` → `20300`.

## Bundled restaurant data

The asset at `app/src/main/assets/michelin.db` is a SQLite snapshot of the upstream
[ngshiheng/michelin-my-maps](https://github.com/ngshiheng/michelin-my-maps) dataset.
Regenerate it with:

```sh
python3 scripts/seed_db.py
```

This requires Python 3 (stdlib only — no pip dependencies). The script downloads the
current CSV, parses it with the same logic as `DatasetSyncWorker`, and writes a Room v2
SQLite file to `app/src/main/assets/michelin.db`.

The app's `DatasetSyncWorker` updates the database at runtime when the upstream SHA changes,
so the bundled file only needs to be refreshed periodically (e.g. before each release).

## Reproducible builds

The release APK is reproducible when `versionName` and `versionCode` are pinned:

```sh
./gradlew clean assembleRelease \
    -PversionName=1.0 \
    -PversionCode=10000
```

Build twice on a clean checkout and compare:

```sh
# unpack both APKs and diff content (ignoring META-INF/ signatures)
unzip -d /tmp/build1 app/build/outputs/apk/release/app-release.apk
./gradlew clean assembleRelease -PversionName=1.0 -PversionCode=10000
unzip -d /tmp/build2 app/build/outputs/apk/release/app-release.apk
diff -rq --exclude='*.RSA' --exclude='*.SF' --exclude='*.MF' /tmp/build1 /tmp/build2
```

A clean diff confirms bit-identical output.

## F-Droid build recipe

F-Droid's build server invocation (declared in `metadata/app.mmmap.yml` in the
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) repository):

```yaml
Builds:
  - versionName: 1.0
    versionCode: 10000
    commit: v1.0
    gradle:
      - yes
    output: app/build/outputs/apk/release/app-release-unsigned.apk
```

The `-unsigned` suffix is because fdroidserver strips the keystore
config from `build.gradle.kts` before building, so the gradle build
emits an unsigned APK. F-Droid then byte-matches it against your
developer-signed APK from the GitHub Release.

Gradle passes `-PversionName` and `-PversionCode` automatically from the metadata.
No `local.properties` or `keystore.properties` are present; the build produces an unsigned APK.
F-Droid verifies the unsigned APK byte-matches the developer-signed GitHub Release APK
(excluding signatures), then republishes the developer-signed version.
