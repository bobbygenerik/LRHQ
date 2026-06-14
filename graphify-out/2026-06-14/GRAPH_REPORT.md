# Graph Report - LRHQ  (2026-06-14)

## Corpus Check
- 86 files · ~154,759 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 880 nodes · 1574 edges · 60 communities (47 shown, 13 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 104 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `57f2b66e`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Command Center Dashboard|Command Center Dashboard]]
- [[_COMMUNITY_Spatial Zone Navigation|Spatial Zone Navigation]]
- [[_COMMUNITY_IPTV Channels & EPG|IPTV Channels & EPG]]
- [[_COMMUNITY_Widget Plugin Contract|Widget Plugin Contract]]
- [[_COMMUNITY_Data Models & Installed Apps|Data Models & Installed Apps]]
- [[_COMMUNITY_System Monitoring|System Monitoring]]
- [[_COMMUNITY_Built-in Widgets|Built-in Widgets]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Main Activity Key Handling|Main Activity Key Handling]]
- [[_COMMUNITY_Ambient Info Services|Ambient Info Services]]
- [[_COMMUNITY_App Composition Root|App Composition Root]]
- [[_COMMUNITY_Live Preview Player|Live Preview Player]]
- [[_COMMUNITY_Ambient Mode Screen|Ambient Mode Screen]]
- [[_COMMUNITY_Media Screen UI|Media Screen UI]]
- [[_COMMUNITY_Widget Card Renderer|Widget Card Renderer]]
- [[_COMMUNITY_Home Screen|Home Screen]]
- [[_COMMUNITY_Focusable Glass Card|Focusable Glass Card]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Gradle Build Config|Gradle Build Config]]
- [[_COMMUNITY_Spatial Navigation Concept|Spatial Navigation Concept]]
- [[_COMMUNITY_Gradle Settings|Gradle Settings]]
- [[_COMMUNITY_UI Module Build|UI Module Build]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]
- [[_COMMUNITY_Community 48|Community 48]]
- [[_COMMUNITY_Community 49|Community 49]]
- [[_COMMUNITY_Community 50|Community 50]]
- [[_COMMUNITY_Community 51|Community 51]]
- [[_COMMUNITY_Community 52|Community 52]]
- [[_COMMUNITY_Community 53|Community 53]]
- [[_COMMUNITY_Community 54|Community 54]]
- [[_COMMUNITY_Community 55|Community 55]]
- [[_COMMUNITY_Community 56|Community 56]]
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 58|Community 58]]
- [[_COMMUNITY_Community 62|Community 62]]

## God Nodes (most connected - your core abstractions)
1. `PersistentChannelRepository` - 25 edges
2. `FakeIptvDao` - 23 edges
3. `AmbientPhotoCacheRepository` - 22 edges
4. `GooglePhotosPickerClient` - 22 edges
5. `GlassPanel()` - 21 edges
6. `IptvDao` - 20 edges
7. `InMemoryPrefsStore` - 20 edges
8. `String` - 19 edges
9. `HqApplication` - 18 edges
10. `MainActivity` - 18 edges

## Surprising Connections (you probably didn't know these)
- `OLED-Dark Glassmorphism Design System` --conceptually_related_to--> `FocusableGlassCard()`  [INFERRED]
  README.md → core/ui/src/main/kotlin/com/livingroomhq/core/ui/components/FocusableGlassCard.kt
- `Command Center Dashboard` --references--> `SystemMonitor`  [EXTRACTED]
  README.md → core/data/src/main/kotlin/com/livingroomhq/core/data/repo/SystemMonitor.kt
- `OLED-Dark Glassmorphism Design System` --references--> `GlassPanel()`  [EXTRACTED]
  README.md → core/ui/src/main/kotlin/com/livingroomhq/core/ui/components/GlassPanel.kt
- `Apps Are Cards, Never Icons` --references--> `WidgetPlugin`  [EXTRACTED]
  README.md → core/widget/src/main/kotlin/com/livingroomhq/core/widget/WidgetPlugin.kt
- `Apps Are Cards, Never Icons` --references--> `WidgetRegistry`  [EXTRACTED]
  README.md → core/widget/src/main/kotlin/com/livingroomhq/core/widget/WidgetRegistry.kt

## Import Cycles
- None detected.

## Communities (60 total, 13 thin omitted)

### Community 0 - "Command Center Dashboard"
Cohesion: 0.09
Nodes (35): Iterable, List, Long, Map, Program, String, Boolean, Channel (+27 more)

### Community 1 - "Spatial Zone Navigation"
Cohesion: 0.07
Nodes (43): AmbientPhotoCacheStats, LauncherPrefsStore, Modifier, List, Modifier, String, Boolean, Color (+35 more)

### Community 2 - "IPTV Channels & EPG"
Cohesion: 0.28
Nodes (15): HqApplication, Modifier, String, WidgetPlugin, WidgetState, AmbientClock(), ambientDate(), ambientMeridiem() (+7 more)

### Community 3 - "Widget Plugin Contract"
Cohesion: 0.09
Nodes (35): Flow, HqApplication, Set, String, WidgetPlugin, WidgetRegistry, WidgetState, WidgetZone (+27 more)

### Community 4 - "Data Models & Installed Apps"
Cohesion: 0.16
Nodes (9): Boolean, ChannelEntity, Flow, GuideChannelEntity, List, Long, ProgramEntity, String (+1 more)

### Community 5 - "System Monitoring"
Cohesion: 0.13
Nodes (25): Boolean, Channel, Int, Long, Modifier, String, Boolean, Channel (+17 more)

### Community 6 - "Built-in Widgets"
Cohesion: 0.19
Nodes (17): Flow, List, StateFlow, String, DownloadJob, AmbientInfoRepository, httpGet(), parseOpenMeteoWeather() (+9 more)

### Community 7 - "Community 7"
Cohesion: 0.15
Nodes (19): AmbientInfoRepository, AmbientPhotoCacheRepository, AmbientPhoto, ChannelRepository, LauncherPrefsStore, List, StateFlow, WidgetRegistry (+11 more)

### Community 8 - "Main Activity Key Handling"
Cohesion: 0.16
Nodes (18): Any, AmbientPhoto, Bitmap, Boolean, Int, List, Long, StateFlow (+10 more)

### Community 9 - "Ambient Info Services"
Cohesion: 0.23
Nodes (9): Boolean, Int, List, MediaItem, MediaType, StateFlow, String, LocalMediaRepository (+1 more)

### Community 10 - "App Composition Root"
Cohesion: 0.17
Nodes (8): Channel, List, Long, Pair, Program, StateFlow, String, ChannelRepository

### Community 11 - "Live Preview Player"
Cohesion: 0.12
Nodes (15): File Structure, Launcher Completion Implementation Plan, Manual verification on device (post-implementation), Out of scope (deliberately), Task 10: Watch Next publisher + sync, Task 11: Final verification + knowledge graph refresh, Task 1: JVM test infrastructure, Task 2: M3U playlist parser (+7 more)

### Community 12 - "Ambient Mode Screen"
Cohesion: 0.32
Nodes (13): Boolean, Channel, Int, List, String, AmbientBackdrops, AmbientPhoto, Artwork (+5 more)

### Community 13 - "Media Screen UI"
Cohesion: 0.22
Nodes (8): Boolean, Flow, List, Set, String, LauncherPrefsStore, DataStorePrefsStore, Keys

### Community 14 - "Widget Card Renderer"
Cohesion: 0.12
Nodes (14): Bundle, Channel, String, Channel, ExoPlayer, Int, String, Tracks (+6 more)

### Community 15 - "Home Screen"
Cohesion: 0.22
Nodes (5): List, Map, Program, String, XmltvParserTest

### Community 16 - "Focusable Glass Card"
Cohesion: 0.24
Nodes (6): Boolean, Context, Intent, DefaultHomeHelper, shouldPromptForDefault(), DefaultHomeHelperTest

### Community 17 - "Community 17"
Cohesion: 0.11
Nodes (26): Boolean, Channel, Float, Int, Modifier, String, Brush, StatBar() (+18 more)

### Community 18 - "Gradle Build Config"
Cohesion: 0.24
Nodes (4): Channel, List, String, M3uParserTest

### Community 19 - "Spatial Navigation Concept"
Cohesion: 0.16
Nodes (17): Bitmap, String, Boolean, Color, Int, List, Long, Modifier (+9 more)

### Community 20 - "Gradle Settings"
Cohesion: 0.29
Nodes (5): AmbientPhoto, Int, List, String, UnsplashClient

### Community 21 - "UI Module Build"
Cohesion: 0.32
Nodes (5): Channel, InputStream, List, String, M3uParser

### Community 22 - "Community 22"
Cohesion: 0.32
Nodes (5): Modifier, StateFlow, String, MessageOverlay(), UiMessages

### Community 23 - "Community 23"
Cohesion: 0.29
Nodes (6): Architecture, Building, Design language, Knowledge graph, LivingRoom HQ, Spatial navigation

### Community 24 - "Community 24"
Cohesion: 0.47
Nodes (4): List, toWatchNextEntry(), WatchNextEntry, WatchNextPublisher

### Community 32 - "Community 32"
Cohesion: 0.16
Nodes (16): AmbientPhotoCacheSource, Boolean, List, Long, Map, StateFlow, String, DeviceCodeResponse (+8 more)

### Community 33 - "Community 33"
Cohesion: 0.22
Nodes (6): ActivityManager, Boolean, Flow, Command Center Dashboard, SystemMonitor, SystemStats

### Community 34 - "Community 34"
Cohesion: 0.25
Nodes (7): Context, IptvDao, build(), LrhqDatabase, migrate(), RoomDatabase, SupportSQLiteDatabase

### Community 35 - "Community 35"
Cohesion: 0.60
Nodes (3): Channel, ChannelEntity, fromModel()

### Community 45 - "Community 45"
Cohesion: 0.60
Nodes (3): Program, fromModel(), ProgramEntity

### Community 46 - "Community 46"
Cohesion: 0.07
Nodes (21): Boolean, Flow, List, Set, String, Boolean, ChannelEntity, Flow (+13 more)

### Community 48 - "Community 48"
Cohesion: 0.11
Nodes (25): Boolean, HqApplication, LauncherNavController, String, Channel, List, Long, Pair (+17 more)

### Community 49 - "Community 49"
Cohesion: 0.40
Nodes (3): Channel, Context, ChannelPlayer

### Community 50 - "Community 50"
Cohesion: 0.16
Nodes (10): Boolean, Channel, Context, ExoPlayer, Int, MediaItem, String, Tracks (+2 more)

### Community 51 - "Community 51"
Cohesion: 0.31
Nodes (8): Boolean, ImageVector, Modifier, String, Zone, NavigationItem, Sidebar(), SidebarItem()

### Community 52 - "Community 52"
Cohesion: 0.39
Nodes (4): InputStream, Long, String, XmltvParser

### Community 53 - "Community 53"
Cohesion: 0.05
Nodes (34): Modifier, WidgetPlugin, WidgetState, Boolean, Bundle, Int, Intent, LauncherNavController (+26 more)

### Community 54 - "Community 54"
Cohesion: 0.17
Nodes (11): Float, Long, Channel, MediaItem, MediaType, Program, ServiceHealth, ServiceStatus (+3 more)

### Community 55 - "Community 55"
Cohesion: 0.38
Nodes (5): Collection, List, String, fromAliases(), GuideChannelEntity

### Community 56 - "Community 56"
Cohesion: 0.22
Nodes (6): Boolean, List, String, LaunchableApp, LaunchableApp, InstalledAppsRepository

### Community 57 - "Community 57"
Cohesion: 0.25
Nodes (7): Configure the playlist + guide, Important platform differences, LivingRoom HQ — Roku, Project layout, Roadmap (remaining Android parity), Sideload (developer mode), What's implemented

### Community 58 - "Community 58"
Cohesion: 0.50
Nodes (3): Float, Long, Pair

## Knowledge Gaps
- **224 isolated node(s):** `PreToolUse`, `PreToolUse`, `plugin`, `LrhqDatabase`, `WidgetRegistry` (+219 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **13 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `StatBar()` connect `Community 17` to `System Monitoring`, `Widget Plugin Contract`, `Community 53`?**
  _High betweenness centrality (0.206) - this node is a cross-community bridge._
- **Why does `PersistentChannelRepository` connect `Command Center Dashboard` to `Community 46`, `Community 7`?**
  _High betweenness centrality (0.172) - this node is a cross-community bridge._
- **Why does `DownloadJob` connect `Community 17` to `Community 54`, `Built-in Widgets`?**
  _High betweenness centrality (0.172) - this node is a cross-community bridge._
- **Are the 6 inferred relationships involving `PersistentChannelRepository` (e.g. with `.`empty playlist leaves channels empty`()` and `.`loadM3u replaces lineup and persists url`()`) actually correct?**
  _`PersistentChannelRepository` has 6 INFERRED edges - model-reasoned connections that need verification._
- **Are the 13 inferred relationships involving `GlassPanel()` (e.g. with `DefaultHomeBanner()` and `AmbientScreen()`) actually correct?**
  _`GlassPanel()` has 13 INFERRED edges - model-reasoned connections that need verification._
- **What connects `PreToolUse`, `PreToolUse`, `plugin` to the rest of the system?**
  _229 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Command Center Dashboard` be split into smaller, more focused modules?**
  _Cohesion score 0.08961038961038961 - nodes in this community are weakly interconnected._