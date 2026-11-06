# 12 — Integrations (Scrobble, Discord, Recognition, Widgets)

## 1. Scrobble (`scrobble/`, ~510 LOC)

Scrobbles plays to **Last.fm** and **Libre.fm**.

- `Scrobbler.kt` — the core: decides *when* to scrobble (min song duration, delay %/seconds)
  and sends to the right site.
- `ScrobbleSite.kt` — site abstraction (Last.fm / Libre.fm API).
- `ScrobbleQueue.kt` + `ScrobbleSendWorker.kt` — a WorkManager queue so scrobbles survive
  even if the app is killed; retry with backoff.
- `ScrobbleTimer.kt` — the timer that fires after N% of the song / N seconds.
- Settings: `ScrobbleMinSongDurationKey`, `ScrobbleDelayPercentKey`,
  `ScrobbleDelaySecondsKey`, `PauseListenHistoryKey`.
- `ScrobblerEntryPoint` (Hilt) — the service reaches the scrobbler without a circular dep.

## 2. Discord RPC (`discord/`, ~1.9k LOC)

Full Discord **rich presence** (now-playing).

- `DiscordGateway.kt` — the WebSocket gateway (heartbeat, presence updates).
- `DiscordHttp.kt` — HTTP to Discord (tokens, commands).
- `DiscordAuth.kt` + `DiscordLoginActivity.kt` — OAuth2 sign-in (client id from
  `local.properties`/CI secrets; redirect scheme `discord-<id>`).
- `DiscordPresence.kt` — builds the presence payload (activity type, details, state,
  large image, buttons).
- `NowPlaying.kt` + `PresenceText.kt` + `PresenceImages.kt` — the now-playing payload
  (song title/artist, album art, timestamps).
- `SpinningArtwork.kt` + `GifEncoder.kt` — a spinning-artwork GIF for the large image.
- `PresenceActivity.kt` — the redirect target.
- `PresenceSettings.kt` — enable/disable, which fields to show.

## 3. Music recognition (`recognition/`, ~604 LOC)

Shazam-style **music recognition** (optional, needs microphone).

- `MusicRecognitionService.kt` — the foreground service that captures audio and matches.
- `ShazamSignatureGenerator.kt` — builds Shazam-compatible signatures.
- `VibraSignature.kt` — Vibra-style signatures.
- `AudioResampler.kt` — resamples capture for matching.
- Uses `providers/shazamkit` signatures; writes `RecognitionHistory` to the DB.

## 4. Widgets (`widget/`, ~577 LOC)

4 home-screen widgets (`res/layout/widget_*.xml`):
- `widget_compact_square`, `widget_compact_wide`, `widget_music_player`, `widget_turntable`
- `widget_colors.xml` for widget tinting.
- Updated from the media session (`MediaLibrarySessionCallback`).

## 5. Other integrations

- **Last.fm / Libre.fm** — metadata + scrobble (`providers/lastfm`).
- **Apple Music** — canvases (`providers/applecanvas`), module (`modules/applemusic`).
- **Spotify / Tidal / TuneMyMusic** — import only (via in-app sign-in).
- **Crowdin** — translation management (`crowdin.yml`).
- **Renovate** — dependency updates (`renovate.json`).
- **Trendshift** — repo badge.

## 6. Privacy posture (from README FAQ)

- No account needed to listen.
- Sign-in opens the **real** service page in-app; only the session token/cookie is stored.
- Tokens are used only to talk to that service; no Mochy server collects logins.
- GPL-3.0 → read exactly what it does with tokens.
