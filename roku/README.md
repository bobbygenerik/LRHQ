# LivingRoom HQ — Roku

A Roku (BrightScript / SceneGraph) port of the Android TV launcher. This is a
**rewrite**, not a shared codebase — Roku has no Kotlin/Compose. The Android app's
data model and design decisions are mirrored here.

## Important platform differences

- **Roku does not allow third-party launchers.** The Android app *replaces the home
  screen*; on Roku this is a normal channel the user opens. The "ambient when idle"
  behavior maps to a separate **screensaver channel** (not yet built — see roadmap).
- **Live hero preview is audible.** The Android home hero auto-plays a muted live
  preview. Roku can render a windowed `Video` node in the hero, but there is no
  reliable per-node mute API, so the preview can play channel audio.
- **Codec support is narrower** and device-dependent; not all IPTV streams will play.

## Project layout

```
roku/
  manifest                       app metadata + splash color
  source/
    main.brs                     entry point + screen event loop
    Theme.brs                    HqColors palette, mirrored from Theme.kt
    Registry.brs                 playlist/EPG URL persistence (roRegistrySection)
  components/
    HomeScene.xml/.brs           Android-style hero, rails, EPG overlay, playback
    RecentChannelChip.xml/.brs   compact recent-channel pill with logo/initial
    CategoryChip.xml/.brs        compact group/category filter pill
    ChannelCard.xml/.brs         text-forward programme card (name + now + progress)
    tasks/
      Net.brs                    shared HTTP GET
      PlaylistLoader.xml/.brs    M3U download + parse  (mirrors M3uParser.kt)
      EpgLoader.xml/.brs         XMLTV download + parse (mirrors XmltvParser.kt)
      UnsplashLoader.xml/.brs    optional random Unsplash ambient batch
```

## Configure the playlist + guide

Press `*` / Options in the channel to open Settings and enter the playlist and guide
URLs with the on-screen keyboard. For debugging, the same values live in the
persistent registry:

```brightscript
regSet("playlist_url", "https://example.com/playlist.m3u")
regSet("epg_url", "https://example.com/guide.xml")
```

Android reads `unsplash.accessKey` from `local.properties` through Gradle
`BuildConfig`. Roku packages are static zips, so the Roku port stores the
Unsplash access key in the device registry from Settings instead of hardcoding it
in source.

## Sideload (developer mode)

1. Enable developer mode on the Roku: Home ×3, Up ×2, Right, Left, Right, Left, Right.
2. Zip the **contents** of `roku/` (manifest at the zip root, not the folder):
   ```
   cd roku && zip -r ../lrhq-roku.zip . -x '*.DS_Store'
   ```
3. Open `http://<roku-ip>` in a browser, log in, upload the zip under "Upload".

## What's implemented

- M3U playlist load + parse (tvg-id, tvg-logo, group-title, display name)
- XMLTV guide load + parse → current + **next** programme, progress, time-left,
  with normalized channel-id matching (case/space/punctuation-insensitive)
- Hero billboard reflecting the focused channel (now/next + clock) with a windowed
  live preview when a channel is focused, Android-style clock/date/weather,
  programme progress, and an up-next panel; falls back to the bundled cinematic
  backdrop while loading or after preview errors
- Rails: Recent Channels chips, On Now programme cards, Category filter chips, and
  filtered All Channels cards
- Recents + favorites persisted in the registry; recents update on play
- Search overlay (Search key) with on-screen keyboard and playable results
- Favorites toggle from focused channels with Play/Pause
- Settings overlay (press `*`/options) to edit playlist + guide URLs, live preview,
  weather, idle delay, and Unsplash access key
- In-app ambient/idle overlay: after 2 min idle, cycles bundled backdrops with a clock;
  any keypress wakes it
- HLS playback on select; Back stops and returns home
- 6 bundled backdrops in `images/`

## Roadmap (remaining Android parity)

- Device-tested playback buffering/error polish
