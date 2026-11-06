# 10 — Lyrics

The lyrics subsystem (`lyrics/`, ~5.9k LOC) is a **multi-provider, auto-syncing, translating**
engine. It's one of the most polished parts of the app.

## 1. Provider registry (`LyricsProviderRegistry.kt`)

A single `object` mapping provider names → `LyricsProvider` implementations:

```
YouLyPlus, Paxsenix, BetterLyrics, SimpMusic, LrcLib, Kugou,
NetEase, QQMusic, Deezer, YouTubeSubtitle, YouTubeMusic, YouTubeCaptions
```

- **Default order** (most reliable first): BetterLyrics → YouLyPlus → Paxsenix →
  SimpMusic → LrcLib → Deezer → YouTubeSubtitle → YouTubeMusic → NetEase → QQMusic →
  Kugou → YouTubeCaptions (last).
- **Module lyrics** (from modules that are on) are asked *first*.
- `deserializeProviderOrder` keeps a saved order, puts newly-added modules first, and
  appends any defaults not yet saved.
- `getOrderedProviders` returns the live `LyricsProvider` list.
- `PreferredLyricsProvider` enum → name mapping for settings.

**Comment in code:** NetEase/QQ/Kugou are near the end because they're Chinese services and
`LyricsHelper` only believes them first-hand for Chinese songs; YouTube auto-captions last
because they're speech recognition.

## 2. The providers

Each provider implements `LyricsProvider` (`suspend fun getLyrics(song): String?` returning
LRC or plain lines). Files:

| File | Source |
|---|---|
| `BetterLyricsProvider` + `TTMLParser` | Apple-Music-style synced lyrics (TTML) |
| `LrcLibLyricsProvider` | LRCLIB |
| `KuGouLyricsProvider` | KuGou |
| `NetEaseLyricsProvider`, `QQMusicLyricsProvider`, `DeezerLyricsProvider` | Chinese/Western |
| `PaxSenixLyricsProvider` | Paxsenix |
| `SimpMusicLyricsProvider` | SimpMusic |
| `YouLyPlusLyricsProvider` | YouLyPlus |
| `YouTubeLyricsProvider`, `YouTubeSubtitleLyricsProvider`, `YouTubeCaptionsLyricsProvider` | YouTube |
| `ModuleLyricsProvider` | wraps a module's `LyricsModule` |

## 3. Auto-sync (`LyricsAutoSync.kt`)

When lyrics aren't timed, Mochy **auto-syncs** them to the audio:
- `AudioFingerprint.kt` + `Fft.kt` — an audio fingerprint + FFT for matching.
- `AudioMatcher` — matches the playing audio to the lyrics timeline.
- `VocalActivity.kt` — detects vocal activity (to find lyric boundaries).
- `VideoAudioSync.kt`, `VideoLyricsSync.kt`, `SongVideoMap.kt` — sync lyrics to a song's
  video/audio.

## 4. Caption alignment (`CaptionAligner.kt`)

For YouTube auto-captions (which are speech-recognition, no real timing), Mochy aligns the
caption cues to the audio using `VideoCaptionSource` + `CaptionAligner`. Tested by
`CaptionAlignerTest.kt`.

## 5. Alignment & offsets (`LyricsAligner.kt`)

`LyricsAligner` aligns LRC lines to the playback position; user offsets are applied and
persisted (`LyricsOffset`). `LyricsUtils` does the heavy lifting (parse LRC, compute timings).

## 6. Translation (`LyricsTranslationHelper.kt`, `GoogleTranslateEngine.kt`)

Lyrics can be translated (Google Translate). `YouTubeCaptionTranslation` translates
YouTube captions. `YouTubeTranslateEngine` for caption translation.

## 7. Embedded lyrics (`EmbeddedLyrics.kt`, `EmbeddedLyricsFile.kt`)

Reads lyrics embedded in audio files (ID3/FLAC tags) — a local source that's always available.

## 8. Lyrics UI

- `LyricsScreen.kt` — the full-screen lyrics view with multiple motion styles
  (`MOTION`, `LEGACY`, `BORING`, `PLAIN`).
- `MiniLyricsTicker.kt`, `LyricsGlowLine.kt`, `LyricsStagger.kt`, `LyricsSteadyWrap.kt`,
  `LyricsReadings.kt`, `LyricsInterludeDots.kt`, `LyricsSourceFooter.kt` — the lyric
  components.
- `MetroLyrics`, `MochyLyrics`, `LyricsV2` — different lyric renderers.
- `LyricsMenuViewModel` — the lyrics bottom-sheet menu (source picker, offset, translate).

## 9. Testing

`LyricsUtilsTest`, `LyricsAutoSyncTest`, `CaptionAlignerTest`, `AudioMatcherTest` — all
pure-JVM tests of the sync/alignment math.
