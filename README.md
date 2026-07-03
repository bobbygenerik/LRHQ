# LivingRoom HQ

A living-room command center for Android TV — not a grid of icons, not an OS.
LivingRoom HQ combines IPTV, media discovery, utilities, system monitoring and
ambient information into one unified experience that makes the television feel
alive before any app is launched.

## Navigation

The launcher uses a collapsible sidebar rail on the left. The rail stays
icon-only while you browse content and expands when focus moves into it
(D-pad LEFT). Selecting a tab collapses the rail and moves focus back into
the main content.

Tabs are ordered top-to-bottom:

```
Home
Live TV
Apps
Command Center
Settings
```

- **Home** → IPTV-first landing zone with a live hero and recent-channel rail
- **Live TV** → Channel grid with always-on preview and now/next EPG
- **Apps** → Utility dashboard of intelligent app cards
- **Command Center** → System monitor (CPU, RAM, storage, network, VPN, services)
- **Settings** → Playlist/EPG sources, weather, and appearance controls
- **Menu** → Jump directly to Command Center
- **Double Back** → Enter Ambient Mode

Tab switches animate vertically based on the sidebar order
(`navigation/LauncherNavHost.kt`). After the configured idle timeout the
launcher fades into Ambient Mode on its own
(`navigation/LauncherNavController.kt`).

## Architecture

| Module | Role |
| --- | --- |
| `:app` | Launcher activity, sidebar tab navigation, zone screens, built-in widgets |
| `:core:ui` | OLED-dark glassmorphism design system (`GlassPanel`, `StatBar`, `HqColors`, `HqType`) |
| `:core:data` | Models + repositories: IPTV channels/EPG, media library, system monitor, ambient info, installed apps |
| `:core:widget` | Widget plugin contract (`WidgetPlugin`, `WidgetRegistry`) — apps are cards, never icons |

Repositories are interfaces backed by real device or configured sources. Live
TV stays empty until an M3U playlist is saved, the media library reads Android
MediaStore, and ambient providers report unavailable until weather, smart-home,
or service APIs are configured. `SystemMonitor` reads real device metrics
(`/proc/stat`, ActivityManager, StatFs, TrafficStats).

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
