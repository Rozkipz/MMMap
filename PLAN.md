# Michelin Guide Android Map App — Plan

## Context
Android app. Shows
Michelin Guide restaurants (3⭐ / 2⭐ / 1⭐ / Bib Gourmand / Selected) on a map,
filters by proximity, opens a quick info card on tap.

No official Michelin restaurant API exists; their site is an Algolia front-end
whose ToS forbids direct scraping. We sidestep this by consuming the MIT-licensed
community dataset `github.com/ngshiheng/michelin-my-maps` (refreshed via GitHub
releases) and enriching on-tap from Foursquare. Canonical descriptions stay on
guide.michelin.com via deep-links — we never republish Michelin prose.

**Phased distribution:**
- **Phase 1 (now):** self-hosted signed APK via GitHub Releases. Get a working
  app shipping fast.
- **Phase 2 (later):** F-Droid main repo submission + IzzyOnDroid auto-pickup.

The stack is **already constrained now** to OSS / F-Droid-compatible libraries
(no Mapbox SDK 10+, no Play Services, etc.) so Phase 2 needs no rewrite — only
metadata, signing-policy, and a fdroiddata PR. All release/build/test
orchestration via a `Justfile`.

---

## Stack (decided)
| Concern | Pick | F-Droid note |
|---|---|---|
| Language/UI | Kotlin + Jetpack Compose | ✅ |
| Map | **MapLibre Native** + `AndroidView` wrapper | ✅ OSS fork of pre-v10 Mapbox |
| Enrichment | **Foursquare Places API v3** | ⚠ `NonFreeNet` anti-feature tag (accepted) |
| Dataset | `ngshiheng/michelin-my-maps` SQLite bundled in APK | ✅ MIT |
| Location | **Android `LocationManager`** (NOT FusedLocation) | ✅ no Play Services |
| Sync | WorkManager | ✅ |
| DI | Hilt | ✅ |
| DB | Room (`createFromAsset`) | ✅ |
| HTTP | Retrofit + OkHttp + kotlinx.serialization | ✅ |
| Images | Coil | ✅ |
| Prefs | DataStore | ✅ |
| Command runner | **`just`** | — |

No Google Play Services, no Firebase, no proprietary SDKs.

---

## Package structure (`app/src/main/kotlin/app/mmmap/`)
```
data/
  db/
    entities/RestaurantEntity.kt
    dao/RestaurantDao.kt
    AppDatabase.kt
  remote/
    FoursquareApi.kt
    GitHubReleasesApi.kt
    models/  (response DTOs)
  sync/
    DatasetSyncWorker.kt
  repository/
    RestaurantRepository.kt
    EnrichmentRepository.kt
    SyncRepository.kt
domain/
  model/Restaurant.kt
  model/Distinction.kt
  model/FoursquareDetail.kt
ui/
  map/MapScreen.kt
  map/MapViewModel.kt
  detail/RestaurantSheet.kt
  detail/DetailViewModel.kt
  list/NearbyScreen.kt
  list/NearbyViewModel.kt
  theme/Theme.kt
  theme/Type.kt
  theme/Color.kt
MmmapApplication.kt
MainActivity.kt
Navigation.kt
di/DatabaseModule.kt
di/NetworkModule.kt
di/RepositoryModule.kt
```

---

## Data model

### `restaurant` table (mirrors michelin-my-maps SQLite schema)
```kotlin
@Entity(tableName = "restaurant")
data class RestaurantEntity(
  @PrimaryKey val id: String,     // SHA-256 of url slug, stable
  val name: String,
  val address: String,
  val location: String?,          // neighbourhood/area
  val latitude: Double,
  val longitude: Double,
  val award: String,              // "3 MICHELIN Stars", "1 MICHELIN Star", "Bib Gourmand", etc.
  val greenStar: Boolean,
  val cuisine: String?,
  val price: String?,             // "€", "€€€", etc.
  val phoneNumber: String?,
  val url: String,                // guide.michelin.com deep link
  val websiteUrl: String?,
  val description: String?,
  val facilitiesAndServices: String?
)
```

### `foursquare_cache` table
```kotlin
@Entity(tableName = "foursquare_cache")
data class FoursquareCacheEntity(
  @PrimaryKey val restaurantId: String,
  val fsqId: String?,
  val photoUrl: String?,
  val openingHoursJson: String?,
  val phone: String?,
  val rating: Double?,
  val fetchedAt: Long
)
```

### DataStore preference keys
- `last_release_tag` (String)
- `last_sync_at` (Long epoch ms)

---

## Key flows

### 1. First launch
1. Room opens DB via `createFromAsset("michelin.db")`. Instant.
2. Location permission flow.
3. Map opens centred on device location (fallback: London / last viewport).
4. WorkManager `DatasetSyncWorker` queued as one-shot.

### 2. Showing pins
- `MapScreen` wraps MapLibre `MapView` in `AndroidView`.
- Restaurants loaded as a GeoJSON FeatureCollection from Room.
- MapLibre `SymbolLayer` with per-distinction icons; clustering enabled via
  `GeoJsonOptions`.
- Filter state (distinction, price, cuisine) held in `MapViewModel`, triggers
  DAO query refresh.

### 3. Nearby
- `LocationManager` with `GPS_PROVIDER` + `NETWORK_PROVIDER`, coarse fallback.
- Bounding-box SQL prefilter → in-memory Haversine sort → top-50 list.

### 4. Pin tap → info sheet
1. `ModalBottomSheet` opens with bundled Room data (name, distinction, cuisine,
   price, address, description).
2. `DetailViewModel` calls `EnrichmentRepository.get(restaurantId)`:
   - Cache fresh (<30d photo, <7d hours) → return immediately.
   - Stale → FSQ `/v3/places/search?ll=lat,lon&query=name&radius=200`, pick best
     match by Jaro-Winkler + distance, fetch `/v3/places/{fsq_id}` for
     photos/hours/rating, write to `foursquare_cache`.
3. Sheet: photo (Coil), hours, phone (tel: intent), website, directions
   (geo: URI → any map app), **"Open in MICHELIN Guide"** (custom-tab).

### 5. Dataset sync
1. GET `api.github.com/repos/ngshiheng/michelin-my-maps/releases/latest`.
2. Compare `tag_name` vs DataStore `last_release_tag`.
3. Newer → download `michelin.db` release asset to temp file, schema sanity
   check (required columns present, row count > prior × 0.9), atomically
   swap Room DB file, reopen, invalidate in-memory caches.
4. `foursquare_cache` rows are preserved (separate table).

---

## F-Droid release path (Phase 2 — deferred)
> Not in scope for the first cut. All stack choices already comply.

### Distribution channels (when ready)
1. **GitHub Releases:** signed APK. Immediate.
2. **IzzyOnDroid:** auto-scrapes GitHub releases after one-time registration.
3. **F-Droid main:** MR to `fdroiddata` with `metadata/app.mmmap.yml`.

### Metadata (future file)
```yaml
# metadata/app.mmmap.yml
Categories: [Navigation]
License: GPL-3.0-or-later
AntiFeatures: [NonFreeNet]
AutoUpdateMode: Version
UpdateCheckMode: Tags
```

---

## Justfile commands
```
build               # assembleDebug
install             # installDebug
run                 # install + adb launch
test                # unit tests
lint                # lintDebug + ktlintCheck + detekt
format              # ktlintFormat
check               # lint + test
clean               # clean
fetch-michelin-data # download latest michelin-my-maps SQLite into assets
assemble-release    # assembleRelease (signed)
bump [patch|minor|major]
tag <version>
gh-release <version>
release <version>   # clean + check + bump + assemble + tag + gh-release
```

---

## Build order (Phase 1)
0. ✅ Copy this plan into repo as `PLAN.md`
1. Project scaffold (Gradle, Compose, Hilt, Room, MapLibre, Retrofit, Justfile)
2. Drop michelin-my-maps SQLite into assets, wire Room `createFromAsset`, render list
3. MapLibre MapScreen — all pins
4. Clustering + per-distinction marker icons
5. LocationManager permission flow + "Near me" + nearby list
6. Filter chips (distinction, price, cuisine)
7. Bottom-sheet detail with bundled fields + Michelin deep link
8. Foursquare enrichment + caching
9. WorkManager dataset sync
10. Release: keystore, signing config, GitHub Actions workflow, README

---

## Setup prerequisites
- Foursquare API key → `local.properties`: `fsq.api.key=YOUR_KEY`
- Signing keystore → `keystore.properties` (gitignored)
- JDK 17 (available at `~/.local/jdk/jdk-17.0.19+10`)
- `just` (already installed at `~/.cargo/bin/just`)
- Android SDK (install via `sdkmanager` or Android Studio)

---

## Verification
- **Offline smoke:** no network → pins render, tap opens sheet with bundled data.
- **Live:** tap a 3⭐ in London → Foursquare photo + hours load; "Open in
  MICHELIN Guide" deep-link works.
- **Nearby:** stub location to Paris → top-10 list correct + sorted by distance.
- **Sync:** force `DatasetSyncWorker` via debug menu → new `last_release_tag`
  written, row count plausible, FSQ cache preserved.
- **Dep audit:** `./gradlew :app:dependencies | grep -Ei 'gms|firebase|mapbox'`
  returns nothing.

---

## Risks & mitigations
- **michelin-my-maps schema drift** → sync worker validates schema + row count.
- **MapLibre tile styling** → start with OpenFreeMap or Protomaps free tiles.
- **Foursquare match accuracy** → Jaro-Winkler + distance scoring; "Wrong info?" link.
- **ToS on bundled description** → drop if Michelin objects; deep-link covers it.
