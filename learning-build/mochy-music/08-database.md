# 08 — Database

Room (`db/`, ~4k LOC). The DB is `MusicDatabase` (single instance, Hilt-provided), versioned
with migrations exported to `app/schemas/com.mochyy.mochymusic.db.InternalDatabase/`
(latest = `34.json`).

## 1. `MusicDatabase`

- `@Database(entities = [...], version = 34, exportSchema = true)`
- KSP Room compiler; `room.schemaLocation = app/schemas`
- `@TypeConverters` for enums/dates/lists
- In-memory `query {}` helper for bulk transactions (used e.g. to purge timestamp artists
  at boot, and to clear fetched lyrics once on the 7.1.0 migration).

## 2. Entities (`db/entities/`)

The schema mirrors a real music library:

| Entity | Purpose |
|---|---|
| `SongEntity` | the song (title, artists, album, duration, streamUrl, …) |
| `AlbumEntity`, `ArtistEntity` | albums & artists |
| `SongArtistMap`, `AlbumArtistMap`, `SongAlbumMap` | many-to-many join tables |
| `AlbumWithSongs`, `SongWithStats`, `SortedSongAlbumMap`, … | query result POJOs |
| `PlaylistEntity`, `PlaylistSongMap`, `PlaylistSongMapPreview` | playlists + membership |
| `PlaylistEvent` | playlist change history |
| `LyricsEntity` | saved lyrics (per provider, user-edited preserved) |
| `Event`, `EventWithSong`, `PlayCountEntity` | play history / counts (for recap & stats) |
| `SearchHistory` | search history |
| `FormatEntity` | per-song stream format info (Audio Quality screen) |
| `ImportedSong`, `ImportSession` | import tracking |
| `LocalItem` | local-file songs |
| `RecognitionHistory` | Shazam-style recognition history |
| `SetVideoIdEntity` | YouTube setVideoId mapping |
| `RelatedSongMap` | "related songs" |
| `SpeedDialItem` | quick-dial items |

## 3. DAOs (`db/daos/`)

Mostly **inline DAOs** (Room supports interface-less DAOs via `@Dao` on abstract classes
or using the `MusicDatabase` companion). `SpeedDialDao.kt` is a standalone DAO.

## 4. Where data comes from

- **YouTube Music** browse/library → parsed by InnerTube → written to the DB
  (`YouTubePlaylistMirror`, `YouTubeSyncManager`).
- **Local files** → `LocalAudioImport`, `DeviceMusicScanner`, `LocalFolders`.
- **Imports** (Spotify/Apple/Tidal/TuneMyMusic/CSV) → `importer/` (see `11-import-sync.md`).
- **Scrobble** history → `scrobble/` writes play counts.
- **Recognition** → `recognition/` writes `RecognitionHistory`.

## 5. Migrations & one-shot wipes

- Schema export to `app/schemas/.../34.json` for Room migrations.
- `App.onCreate` runs a **one-shot lyrics wipe** (`LyricsCacheWipeGeneration`): the first
  time 7.1.0 runs it clears fetched lyrics (keeping user-edited + "Manual"), so every song
  refetches fresh. Only the lyrics table is touched — songs, playlists, library, profile stay.
- `purgeTimestampArtists()` cleans stale artist entries at boot.

## 6. Backup / restore

- `android:fullBackupContent="@xml/backup_rules"` + `dataExtractionRules` — the DB and
  DataStore are backed up (with rules).
- `BackupRestoreViewModel` + `MochyCsvExporter` — export/import the library as CSV.

## 7. The `LocalDatabase` composition local

`MainActivity` provides `LocalDatabase` (a `staticCompositionLocalOf<MusicDatabase>`) so
any composable can read the DB without injection.
