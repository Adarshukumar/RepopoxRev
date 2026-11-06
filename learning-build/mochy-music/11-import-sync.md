# 11 — Import & Sync

The importer subsystem (`importer/`, ~5.8k LOC) brings your music library *into* Mochy
from other services and local files.

## 1. What can be imported

- **Spotify** playlists (via the Spotify web session cookie)
- **Apple Music** playlists (via the Apple Music module / token)
- **Tidal** playlists
- **TuneMyMusic** transfers
- **YouTube Music** library + playlists (sync)
- **CSV** files (`MochyCsvExporter` / `CsvImport`)
- **Local files** (scan the device)

## 2. The flow (`ImportSteps.kt`, `ImportMatches.kt`, `ImportModels.kt`)

1. **Connect** — sign into the source (in-app browser, real page, session cookie stored).
2. **Pick** — choose playlists/artists to import.
3. **Match** — `TrackMatcher` matches each source track to a Mochy-playable track
   (via the provider order / metadata). `ImportMatches` holds the results.
4. **Import** — write matched songs + create playlists (`PlaylistSourceRecord` tracks
   where each playlist came from).
5. **Resync** — `PlaylistResync` re-checks imported playlists when the source changes.

## 3. `TrackMatcher` (`importer/TrackMatcher.kt`)

Matches a `TrackQuery` (title/artists/album/duration/isrc) to a playable track by asking
the provider order. `TrackMatcherTest.kt` tests it. This is the same engine modules use.

## 4. Account handling (`importer/account/`)

Per-service account state: sign-in via the real page, session token storage, expiry
detection (`AccountState { SignedOut, SignedIn, Expired }`). `AccountViewModel`,
`AccountSettingsViewModel`, `AccountImportViewModel` drive the UI.

## 5. Transfer / restore (`importer/transfer/`)

`TransferRestore.applyPending(this)` (called in `App.onCreate` *before* `super`) restores
a transfer that was interrupted (e.g. app killed mid-import). `SyncUndo` lets users undo.

## 6. YouTube sync (`YouTubeSyncManager.kt`, `YouTubePlaylistMirror.kt`)

Mirrors your YouTube Music library/playlists into Mochy's DB. `PendingYouTubeLikes` queues
likes to push. `youtube_sync` route in settings.

## 7. Local import (`LocalAudioImport`, `DeviceMusicScanner`, `LocalFolders`, `LocalArtwork`)

Scans the device for audio files, matches them, and (optionally) repairs covers from the
song's own file (`LocalArtwork.repair`, run after the intro at boot).

## 8. CSV export/import

`MochyCsvExporter` writes the library as CSV; `CsvImport` reads it back. `CsvImportTest`
tests the parsing.

## 9. Settings screens

- `importsync/` — Import & Sync settings (per-service connect, progress).
- `storage/` — storage settings (clear caches, see DB size).
- `TransferViewModel` — the transfer flow UI.
