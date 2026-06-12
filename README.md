# LivingRoom HQ

A living-room command center for Android TV — not a grid of icons, not an OS.
LivingRoom HQ combines IPTV, media discovery, utilities, system monitoring and
ambient information into one spatial experience that makes the television feel
alive before any app is launched.

## Spatial navigation

The launcher is one continuous space made of four directional zones around Home:

```
          LIVE
TOOLS     HOME     MEDIA
        AMBIENT
```

- **Up** → Live (IPTV channel surfing: list + always-on preview, now/next EPG)
- **Right** → Media (cinematic poster rails with an expanding info panel)
- **Left** → Tools (utility dashboard of intelligent app cards)
- **Down** → Ambient (idle face: oversized clock, weather, current channel)
- **Menu** → Command Center (mission-control dashboard: CPU, RAM, storage, network, VPN, services)
- **Home / Back** → return to center

Zone changes slide the world along the axis of travel
(`navigation/SpatialNavHost.kt`), so moving right genuinely feels like
travelling right. After three minutes of inactivity the launcher drifts into
Ambient Mode on its own (`navigation/SpatialNavController.kt`).

## Architecture

| Module | Role |
| --- | --- |
| `:app` | Launcher activity, spatial navigation, zone screens, built-in widgets |
| `:core:ui` | OLED-dark glassmorphism design system (`GlassPanel`, `StatBar`, `HqColors`, `HqType`) |
| `:core:data` | Models + repositories: IPTV channels/EPG, media library, system monitor, ambient info, installed apps |
| `:core:widget` | Widget plugin contract (`WidgetPlugin`, `WidgetRegistry`) — apps are cards, never icons |

Repositories are interfaces with demo implementations so the launcher is fully
navigable out of the box; M3U/Xtream playlists, Jellyfin/Plex and real service
monitors plug in behind the same surfaces. `SystemMonitor` reads real device
metrics (`/proc/stat`, ActivityManager, StatFs, TrafficStats).

## Design language

True-black base for OLED, frosted glass panels (layered gradients instead of
GPU blur so cards composite at 60 fps on Shield-class hardware), a single cool
accent, 10-foot typography, spring-scaled focus. No Material chrome, no ad
banners, no dense content rows.

## Building

```
./gradlew :app:assembleDebug
```

Requires JDK 17 and the Android SDK (compileSdk 35). The app registers both
`LEANBACK_LAUNCHER` and `HOME` intents, so it can be selected as the default
launcher on Android TV / Google TV / NVIDIA Shield.

## Knowledge graph

The repository ships with a graphify knowledge graph in `graphify-out/`.
Ask structural questions with:

```
graphify query "How does zone navigation reach the Command Center?"
```
