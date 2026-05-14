# MMMap — MICHELIN Guide Map

[![CI](https://github.com/Rozkipz/MMMap/actions/workflows/ci.yml/badge.svg)](https://github.com/Rozkipz/MMMap/actions/workflows/ci.yml)
[![Coverage](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/Rozkipz/MMMap/badges/coverage.json)](https://github.com/Rozkipz/MMMap/actions/workflows/ci.yml)

An Android app that shows every restaurant in the MICHELIN Guide on an interactive map.

## Features
- Offline-first — full dataset bundled in the APK
- Filter by distinction (★★★ / ★★ / ★ / Bib Gourmand / Selected), cuisine, price
- Near Me — restaurants sorted by your distance
- Tap any pin for photos, opening hours, phone, website (via Foursquare)
- Deep-links directly to each restaurant's MICHELIN Guide page

## Tech stack
Kotlin + Jetpack Compose · MapLibre Native · Room · Retrofit · Hilt · WorkManager

## Setup

### Requirements
- Android Studio Ladybug or newer
- JDK 17 (bundled at `~/.local/jdk/jdk-17.0.19+10` on the dev machine, or set `JAVA_HOME`)
- `just` command runner: `cargo install just` or `brew install just`

### API keys
Create `local.properties` (gitignored):
```
fsq.api.key=YOUR_FOURSQUARE_API_KEY
```
Get a free key at https://developer.foursquare.com/

### Michelin dataset
```bash
just fetch-michelin-data
```
This downloads the latest SQLite release from
[ngshiheng/michelin-my-maps](https://github.com/ngshiheng/michelin-my-maps) (MIT licence)
into `app/src/main/assets/michelin.db`.

### Build & run
```bash
just build          # debug APK
just install        # install to connected device/emulator
just run            # install + launch
just check          # lint + tests
```

## Release

### First time: create signing keystore
```bash
just setup-keystore
```
**Back up `mmmap-release.jks` and `keystore.properties` securely. Losing the keystore
means users can't receive future updates from GitHub Releases / IzzyOnDroid.**

### Publish a release
```bash
just release 0.2.0
```
This cleans, checks, bumps version, signs the APK, creates a git tag,
and publishes a GitHub Release with the signed APK attached.

### Install from GitHub Releases (sideload)
1. Download `app-release.apk` from the latest GitHub Release
2. On your phone: Settings → Security → Install unknown apps → allow your browser
3. Open the downloaded APK and install

## F-Droid (Phase 2 — planned)
All stack choices are already F-Droid-compatible. When ready:
- Run `just fdroid-lint` to validate metadata
- Open a PR against [fdroiddata](https://gitlab.com/fdroid/fdroiddata)
- Register at [IzzyOnDroid](https://apt.izzysoft.de/fdroid/) for faster initial availability

> Note: the APK signed by F-Droid uses a different key from GitHub Releases.
> Users switching repos will see an "app conflict" prompt and must uninstall/reinstall.

## Map tiles
Default: [MapLibre demo tiles](https://demotiles.maplibre.org/) — fine for development,
rate-limited in production. Replace `TILE_STYLE_URL` in `MapScreen.kt` with:
- [Protomaps](https://protomaps.com/) — pay-once serverless tiles
- [OpenFreeMap](https://openfreemap.org/) — free self-hosted tiles
- Your own MapLibre tile server

## Data attribution
Restaurant data: © MICHELIN Guide, sourced via
[ngshiheng/michelin-my-maps](https://github.com/ngshiheng/michelin-my-maps) (MIT)  
Place enrichment: Foursquare Places API  
Map tiles: OpenStreetMap contributors
