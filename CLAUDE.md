# Mmmap — Claude Code notes

## What this app is

Android app that plots every MICHELIN Guide restaurant on a MapLibre map with offline-capable tile caching, Foursquare enrichment (photos / hours / ratings), and multi-select filters (award, cuisine, price tier). No Google Play Services dependency.

## Common commands

```
just install       # build debug APK and push to connected device
just run           # install + launch via adb
just test          # ./gradlew testDebugUnitTest
just coverage      # tests + JaCoCo HTML report
just check         # lint + tests (run before committing)
just lint
just format        # ktlintFormat
just release 1.2.3 # clean → check → sign → tag → GitHub Release
```

`JAVA_HOME` is set automatically by the Justfile to `~/.local/jdk/jdk-17.0.19+10`.

## Architecture

```
ui/map/MapViewModel      ← filters, bounds, WorkManager, TileCacheManager
ui/detail/DetailViewModel ← EnrichmentRepository
data/repository/
  RestaurantRepository   ← Room DAO, cuisine post-filter (client-side)
  EnrichmentRepository   ← FoursquareCacheDao + FoursquareApi + ApiKeyPreferences
data/sync/
  DatasetSyncWorker      ← WorkManager worker, downloads michelin CSV → Room
  SyncPreferences        ← DataStore: last SHA + timestamp
data/prefs/
  ApiKeyPreferences      ← DataStore: user-supplied FSQ key
  MapCachePreferences    ← DataStore: tile cache size (50/100/500/1024 MB)
map/
  TileCacheManager       ← wraps MapCachePreferences + AmbientCacheSource
  AmbientCacheSource     ← interface (real impl: MapLibreAmbientCacheSource)
  MapLibreAmbientCacheSource ← wraps OfflineManager callbacks as coroutines
di/
  DatabaseModule         ← Room + DAOs + WorkManager provider
  DataStoreModule        ← single shared DataStore<Preferences> ("mmmap_prefs")
  NetworkModule          ← OkHttp + Retrofit (Foursquare + GitHub)
  TileCacheModule        ← @Binds AmbientCacheSource → MapLibreAmbientCacheSource
```

DI: Hilt throughout. `@HiltViewModel` on all ViewModels.

## Data

**Restaurant DB** — bundled SQLite asset (`app/src/main/assets/michelin.db`), refreshed periodically by `DatasetSyncWorker` pulling from `ngshiheng/michelin-my-maps` on GitHub. ~19 000 rows.

**Award column values** (exact strings, case-sensitive):
- `1 Star`, `2 Stars`, `3 Stars`, `Bib Gourmand`, `Selected Restaurants`

**Price tier** — derived at query time via `LENGTH(price)` in SQL (1 = `£`, 2 = `££`, etc.). No stored column.

**Cuisine filter** — Room does not filter cuisines; `RestaurantRepository` post-filters in Kotlin because compound values like `"French, Contemporary"` need splitting first.

**Room gotcha** — do NOT pre-create `room_master_table` in the bundled SQLite. Room creates it on first open; a pre-existing table (even empty) triggers destructive migration and wipes data.

## Foursquare API key

`BuildConfig.FSQ_API_KEY` is only set in **debug** builds, read from `local.properties`:

```
fsq.api.key=your_key_here
```

Release APKs ship with an empty key — enrichment is opt-in for users via the in-app Settings → Foursquare Key dialog. `EnrichmentRepository` falls back gracefully when both the stored key and the BuildConfig key are blank.

## Testing patterns

### What to mock vs what to instantiate

| Class | How to test |
|---|---|
| `RestaurantRepository` | Instantiate with `mockk<RestaurantDao>` |
| `EnrichmentRepository` | Instantiate with `mockk<FoursquareApi>`, `mockk<FoursquareCacheDao>`, `mockk<ApiKeyPreferences>` |
| `TileCacheManager` | Instantiate with real `MapCachePreferences` (DataStore) + `mockk<AmbientCacheSource>` |
| `MapViewModel` | Instantiate directly; inject `mockk<WorkManager>` |
| `ApiKeyPreferences` / `SyncPreferences` / `MapCachePreferences` | Instantiate with `PreferenceDataStoreFactory.create(scope = testScope) { tmpFolder.newFile(...) }` |

### Do NOT mock OfflineManager

`OfflineManager` has a JNI native static initializer that calls `android.util.Log.e()` → `ExceptionInInitializerError` in JVM tests. The `AmbientCacheSource` interface exists specifically to keep `OfflineManager` out of unit tests. Mock the interface instead.

### WorkManager injection

`WorkManager.getInstance(context)` cannot be intercepted by `mockkStatic` during `every { }` recording — the companion's real implementation runs and calls `context.getApplicationContext()` on the matcher object, which throws `AbstractMethodError`. Always inject `WorkManager` as a constructor parameter and provide it via `DatabaseModule.provideWorkManager`.

### Room withTransaction in tests (Room 2.8.x)

```kotlin
// Room 2.8 moved withTransaction to this class:
mockkStatic("androidx.room.RoomDatabaseKt__RoomDatabase_androidKt")
coEvery { db.withTransaction<Unit>(any()) } coAnswers {
    // args[0] = extension receiver (db), args[1] = the suspend lambda
    @Suppress("UNCHECKED_CAST")
    (invocation.args[1] as suspend () -> Unit).invoke()
}
```

### Avoid withContext(Dispatchers.IO) in ViewModels under test

`withContext(IO)` dispatches to a real thread pool. If the test's `@After` calls `Dispatchers.resetMain()` before the IO thread finishes, you get `UncaughtExceptionsBeforeTest` on the next test. Use `viewModelScope.launch` on the main dispatcher instead, or restructure to avoid blocking calls inside ViewModels.

### BuildConfig.FSQ_API_KEY in tests

The key is present in debug builds **only when `local.properties` exists** (i.e. on your machine). In CI it is blank. Any test that requires the key to be non-blank must guard with:

```kotlin
org.junit.Assume.assumeTrue(BuildConfig.FSQ_API_KEY.isNotBlank())
```

### Standard test setup for coroutine-based classes

```kotlin
private val testDispatcher = UnconfinedTestDispatcher()

@Before fun setUp() {
    Dispatchers.setMain(testDispatcher)
}

@After fun tearDown() {
    Dispatchers.resetMain()
}
```

Use `TestScope(testDispatcher)` + `testScope.runTest { }` for classes that need a stable `CoroutineScope` (e.g. DataStore-backed prefs). Use `runTest { advanceUntilIdle() }` for ViewModels.

## Security

- `local.properties` and `keystore.properties` are gitignored — never commit them.
- The FSQ API key must only live in `local.properties` or the in-app DataStore, never in version-controlled files.
- Release signing credentials live in `keystore.properties` (gitignored). Back up the `.jks` separately.

## Distribution plan

Phase 1 (current): self-hosted GitHub Releases APK (`just release <version>`).
Phase 2 (deferred): F-Droid + IzzyOnDroid — stack is already compatible (no GMS/Firebase/Mapbox).

`just deps-audit` checks for proprietary lib leakage.
