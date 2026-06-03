# F-Droid submission guide

## One-time setup

### 1. Fill in AllowedAPKSigningKeys

Get the SHA-256 fingerprint of the release signing cert, strip the colons, and paste it into `fdroid/app.mmmap.yml`:

```sh
keytool -list -v -keystore mmmap-release.jks -alias mmmap \
    | grep "SHA-256"
# example output: SHA-256: AA:BB:CC:DD:EE:...
# paste as:       aabbccddee...  (lowercase, no colons)
```

Edit the placeholder in `fdroid/app.mmmap.yml`:
```yaml
AllowedAPKSigningKeys: aabbccddee...
```

---

### 2. Add screenshots, icon, and feature graphic

Install the app on a device or emulator (`just run`), then capture the following screenshots and export them as PNG:

| Shot | What to show |
|---|---|
| 1 | Map with restaurant pins visible |
| 2 | Filter sheet open (award/cuisine/price chips) |
| 3 | Restaurant detail sheet (name, award, cuisine, facilities) |
| 4 | Near Me list |
| 5 | Visited list or Settings screen |

Save them here:
```
fastlane/metadata/android/en-US/images/
  phoneScreenshots/
    1.png
    2.png
    3.png
    4.png
    5.png
  icon.png           (512×512 — export from mipmap-xxxhdpi or the app icon SVG)
  featureGraphic.png (1024×500 — a banner; docs/banner.svg is a starting point)
```

Capture via adb if you prefer:
```sh
adb shell screencap -p /sdcard/shot.png && adb pull /sdcard/shot.png 1.png
```

---

## Submitting to fdroiddata

### 3. Fork fdroiddata

```sh
# on GitLab: fork https://gitlab.com/fdroid/fdroiddata
# then clone your fork locally
git clone git@gitlab.com:YOUR_GITLAB_USERNAME/fdroiddata.git
cd fdroiddata
git remote add upstream https://gitlab.com/fdroid/fdroiddata.git
git fetch upstream
git checkout -b add-app.mmmap upstream/master
```

### 4. Install fdroidserver (for local validation)

```sh
pip install fdroidserver
# or on Debian/Ubuntu:
sudo apt install fdroidserver
```

### 5. Copy and validate the metadata

```sh
cp /path/to/Mmmap/fdroid/app.mmmap.yml metadata/app.mmmap.yml

# auto-format to fdroiddata style (fixes whitespace, field order, etc.)
fdroid rewritemeta app.mmmap

# lint for errors
fdroid lint app.mmmap
# expected: no errors
```

### 6. (Optional) Test the build locally with Docker

Requires Docker and fdroidserver with the build VM image:

```sh
fdroid build --on-server --no-tarball --verbose app.mmmap
```

This simulates exactly what F-Droid's build server will do. Fix any build failures before submitting.

### 7. Open the Merge Request

```sh
git add metadata/app.mmmap.yml
git commit -m "Add app.mmmap (Mmmap — MICHELIN Guide map)"
git push origin add-app.mmmap
```

Then open a MR on GitLab against `fdroid/fdroiddata:master`.

Suggested MR description:

```
## Mmmap — MICHELIN Guide restaurant map

**App ID**: app.mmmap
**License**: MIT
**Source**: https://github.com/Rozkipz/Mmmap

Plots every MICHELIN Guide restaurant on an offline-first MapLibre map.
No GMS, no Firebase, no Mapbox. Fully self-hostable data via GitHub CSV sync.

### Anti-features
- `NonFreeNet`: dataset refresh from raw.githubusercontent.com and map tiles from tiles.openfreemap.org.

### Reproducibility
Build instructions are in BUILDING.md. The release APK is signed by upstream;
AllowedAPKSigningKeys is set so F-Droid can verify the byte-match and serve
the developer-signed APK.

### Build verification
Tested with: JDK 17, AGP 9.2.1, Android SDK platform-36.
./gradlew assembleRelease -PversionName=1.0 -PversionCode=10000
```

---

## Per-release checklist (after initial acceptance)

Each time you cut a new release, do these steps **before** tagging:

```sh
# 1. Refresh the bundled DB
just seed-db

# 2. Add a fastlane changelog (versionCode = MAJOR*10000 + MINOR*100 + PATCH)
echo "What changed in this release." \
  > fastlane/metadata/android/en-US/changelogs/<versionCode>.txt

# 3. Update CHANGELOG.md

# 4. Add a new Builds: entry to fdroid/app.mmmap.yml:
#    - versionName: 'X.Y.Z'
#      versionCode: NNNNN
#      commit: vX.Y.Z
#      gradle:
#        - yes
#      output: app/build/outputs/apk/release/app-release.apk

# 5. Run the standard release pipeline
just release X.Y.Z
```

After the GitHub Release is published, F-Droid detects the new tag via `UpdateCheckMode: Tags`, builds from source, verifies the byte-match against your signed APK, and publishes automatically — no MR needed for subsequent releases.

---

## After acceptance

Add the F-Droid badge to `README.md`:

```markdown
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="75">](https://f-droid.org/packages/app.mmmap)
```
