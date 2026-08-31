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

Output: `app/build/outputs/apk/release/app-release.apk` — an unsplit APK carrying all four ABIs.

When `keystore.properties` is absent (e.g. on a build server), the release APK is produced unsigned. F-Droid's build server intentionally omits this file and signs with its own key, or verifies a byte-match against a developer-signed APK for the `AllowedAPKSigningKeys` path.

## ABI split

Carrying four ABIs makes the APK ~67 MB, of which ~45 MB is native code the device cannot
use. `-PabiFilter=<abi>` restricts the build to one architecture:

```sh
./gradlew assembleRelease -PabiFilter=arm64-v8a
# → app/build/outputs/apk/release/app-arm64-v8a-release.apk   (~36 MB)
```

Valid values: `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`. Without the property the build is
unsplit, so debug builds, `just install` and `just run` are unaffected.

F-Droid has no native support for split APKs — it needs one build block per ABI, each
producing an APK with its own versionCode. `just assemble-release` runs exactly those four
invocations (plus one unsplit build) and stages the results in `dist/`.

## versionCode convention

The base lives as a literal in `app/build.gradle.kts`:

`base = MAJOR * 10000 + MINOR * 100` — e.g. `1.0` → `10000`, `1.2` → `10200`, `2.3` → `20300`.

Each APK is then stamped `10 * base + <ABI offset>`, ordered as F-Droid requires so clients
resolve the correct variant:

| ABI | offset | versionCode at base 10500 |
|---|---|---|
| *(unsplit / universal)* | 0 | 105000 |
| `armeabi-v7a` | 1 | 105001 |
| `arm64-v8a` | 2 | 105002 |
| `x86` | 3 | 105003 |
| `x86_64` | 4 | 105004 |

Bump with `just bump-version 1.5`, which rewrites both literals **and** keeps
`fdroid/app.mmmap.yml` in lockstep. Never replace the literals with a computed expression:
F-Droid's `checkupdates` regex-scans the `versionCode = <digits>` and
`versionName = "<string>"` lines, and anything else silently breaks auto-updates.

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

`versionName` and `versionCode` are literals in `app/build.gradle.kts`, so a bare
`assembleRelease` is already pinned — no `-P` flags required. This matters because F-Droid
invokes gradle with no version properties; anything derived from git or a gradle property
would differ from the published APK and fail verification.

Build the same ABI twice on a clean checkout and compare:

```sh
./gradlew clean assembleRelease -PabiFilter=arm64-v8a
unzip -d /tmp/build1 app/build/outputs/apk/release/app-arm64-v8a-release.apk
./gradlew clean assembleRelease -PabiFilter=arm64-v8a
unzip -d /tmp/build2 app/build/outputs/apk/release/app-arm64-v8a-release.apk
diff -rq --exclude='*.RSA' --exclude='*.SF' --exclude='*.MF' /tmp/build1 /tmp/build2
```

A clean diff confirms bit-identical output.

`Binaries:` verification is per-APK, so `just assemble-release` builds each ABI in its own
gradle invocation — the same way F-Droid's four build blocks do — rather than emitting all
four from a single build. That keeps each published artifact byte-identical to what the
F-Droid builder produces.

## F-Droid build recipe

One build block per ABI — see `fdroid/app.mmmap.yml` for the full recipe:

```yaml
Builds:
  - versionName: '1.5'
    versionCode: 105002
    commit: <full sha>
    subdir: app
    gradle:
      - yes
    gradleprops:
      - abiFilter=arm64-v8a
```

F-Droid passes **no** version properties to gradle — only what `gradleprops` lists — which
is why the version has to be a literal in `app/build.gradle.kts`. It then checks that the
built APK's own versionCode equals the block's `versionCode`, so the `10 * base + offset`
arithmetic in the build script and the `VercodeOperation` entries in the recipe must agree
exactly.

There is no `ndk:` field: the project has no native source, and the `.so` files arrive
prebuilt inside the MapLibre AAR. Pinning an NDK revision would only risk the build server
not having it.

fdroidserver strips the keystore config before building, so the gradle build emits an
unsigned APK (`app-arm64-v8a-release-unsigned.apk`). F-Droid byte-matches that against the
developer-signed APK from the GitHub Release, then republishes the signed one.

Gradle passes `-PversionName` and `-PversionCode` automatically from the metadata.
No `local.properties` or `keystore.properties` are present; the build produces an unsigned APK.
F-Droid verifies the unsigned APK byte-matches the developer-signed GitHub Release APK
(excluding signatures), then republishes the developer-signed version.
