# Changelog

## [Unreleased]

## [1.1]

- Move `versionName` / `versionCode` into `gradle.properties` so source-only
  builds (F-Droid) produce identically-versioned APKs without `-P` flags
- Release pipeline auto-bumps `gradle.properties`; GH Actions workflow
  fails fast if it drifts from the input version
- Fix F-Droid metadata output path: `app-release-unsigned.apk`

## [1.0]

Initial release.

- Browse all MICHELIN Guide restaurants on an offline-first map (OpenFreeMap tiles)
- Filter by distinction: ★★★ / ★★ / ★ / Bib Gourmand / Selected Restaurants
- Filter by cuisine and price range
- Tap any pin for details: phone, website, cuisine, facilities from the MICHELIN dataset
- Near Me — restaurants sorted by distance
- Direct deep-link to each restaurant's official MICHELIN Guide page
- Mark restaurants as visited; export / import visited list
- No tracking, no analytics, no account required
