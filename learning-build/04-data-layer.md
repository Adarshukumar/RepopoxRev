# 04 — Data layer

## Room database

```kotlin
const val MusicDatabaseVersion = 42

@Database(
    entities = [ …23 entities… ],
    views    = [ SortedSongArtistMap, SortedSongAlbumMap, PlaylistSongMapPreview ],
    version  = MusicDatabaseVersion,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class InternalDatabase : RoomDatabase() {
    abstract val dao: DatabaseDao
    abstract val speedDialDao: SpeedDialDao
    companion object { const val DB_NAME = "mochy.db" }
}
```

**23 entities, 3 SQL views, 350 DAO annotations** in a 2,030-line `DatabaseDao.kt`.

The entities: `SongEntity`, `ArtistEntity`, `AlbumEntity`, `PlaylistEntity`, the three
join tables (`SongArtistMap`, `SongAlbumMap`, `AlbumArtistMap`), `PlaylistSongMap`,
`SearchHistory`, `FormatEntity`, `LyricsEntity`, `Event` (play history), `RelatedSongMap`,
`SetVideoIdEntity`, `PlayCountEntity`, `RecognitionHistory`, `SpeedDialItem`,
`PlaylistEvent`, and five import tables (`ImportedSong`, `ImportSession`, `ImportSessionSong`,
`ImportedPlaylist`, `ImportedPlaylistTrack`).

Several are declared *inside* a shared file — `ImportSessionSong`, `ImportedPlaylist` and
`ImportedPlaylistTrack` all live in `ImportSession.kt`. A naive checker that assumes
one-class-per-file reports them as missing; the symbol index in `tools/kotlin_index.py` reads
the actual declarations and resolves all 23.

### Tuning

The database is opened with deliberate performance settings:

```kotlin
.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
.setTransactionExecutor(Executors.newFixedThreadPool(4))
.setQueryExecutor(Executors.newFixedThreadPool(4))
.addCallback(object : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        db.query("PRAGMA busy_timeout = 60000").close()
        db.query("PRAGMA cache_size = -16000").close()      // 16 MB page cache
        db.query("PRAGMA wal_autocheckpoint = 1000").close()
        db.query("PRAGMA synchronous = NORMAL").close()
    }
})
```

Separate 4-thread executors for transactions and queries mean a long write cannot starve the
UI's reads. `busy_timeout = 60000` makes SQLite wait a full minute rather than throwing
`SQLITE_BUSY` — the right trade for a media app doing background sync while the user scrolls.
`synchronous = NORMAL` with WAL is the standard durability/speed compromise.

## The migration graph — and its gap

This is the most serious defect found in the codebase.

`AllMigrations` contains exactly 11 migrations:

```
MIGRATION_1_2, MIGRATION_21_24, MIGRATION_22_24, MIGRATION_24_25,
MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38, MIGRATION_38_39,
MIGRATION_39_40, MIGRATION_40_41, MIGRATION_41_42
```

The chain is **disconnected**. Walking it from each starting version:

| start version | path to 42 | outcome |
|---|---|---|
| 1 | 1→2, then stuck (nothing starts at 2) | ✗ |
| 2–20 | nothing starts here | ✗ |
| 21, 22 | →24→25, then stuck (nothing starts at 25) | ✗ |
| 23, 24 | →25, then stuck | ✗ |
| 25 | nothing starts at 25 | ✗ |
| 26–34 | nothing starts here | ✗ |
| 35–41 | 35→36→…→42 | ✓ |
| 42 | already there | ✓ |

**34 of 42 versions have no upgrade path.** Because the builder also specifies:

```kotlin
.fallbackToDestructiveMigration(dropAllTables = true)
```

any user whose on-device database is at v1–v34 and who upgrades to a build at v42 has their
**entire library silently deleted** — every song, playlist, artist, album, play-count and
lyrics row. No prompt, no backup, no error.

The code is *aware* of this class of bug. The comment above `AllMigrations` reads:

> Every hand-written migration, for every place the database is built (here and the Hilt
> module). The app opens it through the Hilt module; when that copy of the list fell behind
> this one, an upgrade found no path to the new version and `fallbackToDestructiveMigration`
> emptied the whole library. Add new migrations here and nowhere else.

So the team has already been bitten by exactly this failure mode once, and fixed the *symptom*
(two divergent lists) rather than the *cause* (no test asserts the graph is connected).

### Why v34 matters specifically

This is not a theoretical concern about ancient installs. The predecessor package
`com.music.vivi` shipped schema **v34** — `app/schemas/com.music.vivi.db.InternalDatabase/`
contains `1.json … 34.json`, and the renamed `com.mochyy.mochymusic.db.InternalDatabase/`
directory contains only `34.json`. Migrations then resume at `35_36`.

And the app actively migrates users across that boundary. `SetupWizardScreen.kt` offers, in
its own words, *"previous data: a whole export from the old app or another phone"*, routing
into `TransferViewModel.importFrom(uri)` → `TransferImporter`, which guards with:

```kotlin
if (!hasMigrationPath(manifest.databaseVersion, MusicDatabaseVersion)) {
    throw TransferException("This export is too old for Mochy to read. " +
                            "Update your old app, export again and import that.")
}
```

`hasMigrationPath` is a greedy walk over the same `AllMigrations`:

```kotlin
fun hasMigrationPath(from: Int, to: Int): Boolean {
    if (from == to) return true
    if (from <= 0 || from > to) return false
    var at = from
    while (at < to) {
        at = AllMigrations.filter { it.startVersion == at && it.endVersion <= to }
                 .maxOfOrNull { it.endVersion } ?: return false
    }
    return true
}
```

`hasMigrationPath(34, 42)` returns **false**, because no migration starts at 34. So the
onboarding flow that exists precisely to bring users over from the old app rejects the old
app's own last schema version. The user is told to "update your old app" — but v34 *is* the
version the fork inherited.

Two distinct user-visible failures, one root cause:

1. **In-place upgrade** from a v≤34 database → silent total library loss.
2. **Transfer import** from a v≤34 export → refused with a message that advises an impossible
   action.

### The missing test

`exportSchema = false` means KSP writes no schema JSON, so `app/schemas` froze at v34 while
the live version is 42 — even though `ksp { arg("room.schemaLocation", …) }` is still
configured. Without exported schemas there is no way to write Room's standard
`MigrationTestHelper` test, which is the usual mechanism that catches a disconnected graph.
The `room.schemaLocation` argument is now dead configuration.

A cheap guard that would have caught this:

```kotlin
@Test fun migrationGraphIsConnected() {
    for (from in 1 until MusicDatabaseVersion) {
        assertTrue("no upgrade path from v$from", hasMigrationPath(from, MusicDatabaseVersion))
    }
}
```

Or, better: drop `fallbackToDestructiveMigration` in release builds so a missing migration
crashes loudly in testing instead of eating a library in production.

## Preferences

Two stores, used for different things:

- **DataStore** (`androidx.datastore:datastore-preferences`) via `utils/dataStore`, with
  ~1,287 lines of keys in `constants/PreferenceKeys.kt`. Observed in `App.kt`'s
  `observeSettingsChanges()`.
- **SharedPreferences** for things read on hot paths or from the native layer, wrapped by
  `utils/PreferencesCache` (started in `App.onCreate`) so a DataStore round-trip never blocks
  the UI thread.

`PreferenceKeys.kt` also carries the app's enums — `PlayerStyle`, `AudioPassMode`,
`PreferredLyricsProvider`, `DarkMode`, `LyricsPosition`, `CrossmixMode` and many more — which
is why several "missing type" reports from a filename-based checker were false positives:
`TransportEffect` and `PlayerBackgroundStyle` are declared there, not in their own files.

## The transfer/backup archive

`importer/transfer/` implements a zip-based whole-app archive: `TransferArchive` (format
constants + manifest), `TransferExport`, `TransferImport`, `TransferRestore`.

Details worth learning from:

- **Zip-slip defence.** Every extracted entry is checked with
  `if (!target.canonicalPath.startsWith(root)) continue` so a crafted `../` path cannot escape
  the staging directory.
- **Staging + marker.** Files are unpacked to a staging directory and a `ReadyMarker` is
  written *last*: *"without it the next start throws the half-made folder away instead of
  using it."* An interrupted import therefore cannot leave a half-applied library.
- **Crash-safe apply.** `TransferRestore.applyPending(this)` is called in `App.onCreate()`
  **before** `super.onCreate()`, because that is where Hilt builds the database — the restore
  must land first.
- **Forward-compatibility guard.** `manifest.formatVersion > TransferArchive.FormatVersion ||
  manifest.databaseVersion > MusicDatabaseVersion` → "This export comes from a newer Mochy."
- **Selective preferences.** Only `TransferArchive.PrefsToCarry` entries are copied; everything
  else in the zip is deleted, so a device-specific preference cannot be smuggled across.

## Cached media

`playback/CachedSongs.kt` and `ExoDownloadService`/`DownloadUtil` manage Media3's
`SimpleCache`. `StorageRoute` in settings exposes usage, and the roadmap lists "Storage usage"
as an in-progress item.
