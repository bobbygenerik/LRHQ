# Graph Report - LRHQ  (2026-06-13)

## Corpus Check
- 84 files · ~53,542 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 824 nodes · 1469 edges · 53 communities (41 shown, 12 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 104 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `23c813d1`
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
- [[_COMMUNITY_Tools Screen|Tools Screen]]
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
- [[_COMMUNITY_Community 51|Community 51]]
- [[_COMMUNITY_Community 52|Community 52]]
- [[_COMMUNITY_Community 55|Community 55]]

## God Nodes (most connected - your core abstractions)
1. `PersistentChannelRepository` - 25 edges
2. `FakeIptvDao` - 23 edges
3. `AmbientPhotoCacheRepository` - 22 edges
4. `IptvDao` - 20 edges
5. `InMemoryPrefsStore` - 20 edges
6. `GlassPanel()` - 20 edges
7. `String` - 19 edges
8. `HqApplication` - 18 edges
9. `MainActivity` - 18 edges
10. `GooglePhotosPickerClient` - 18 edges

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

## Communities (53 total, 12 thin omitted)

### Community 0 - "Command Center Dashboard"
Cohesion: 0.09
Nodes (35): Iterable, List, Long, Map, Program, String, Boolean, Channel (+27 more)

### Community 1 - "Spatial Zone Navigation"
Cohesion: 0.05
Nodes (56): AmbientPhotoCacheStats, LauncherPrefsStore, Modifier, HqApplication, LauncherNavController, String, Channel, List (+48 more)

### Community 2 - "IPTV Channels & EPG"
Cohesion: 0.06
Nodes (37): androidx, Boolean, Bundle, Int, Intent, LauncherNavController, Modifier, Zone (+29 more)

### Community 3 - "Widget Plugin Contract"
Cohesion: 0.09
Nodes (35): Flow, HqApplication, Set, String, WidgetPlugin, WidgetRegistry, WidgetState, WidgetZone (+27 more)

### Community 4 - "Data Models & Installed Apps"
Cohesion: 0.16
Nodes (9): Boolean, ChannelEntity, Flow, GuideChannelEntity, List, Long, ProgramEntity, String (+1 more)

### Community 5 - "System Monitoring"
Cohesion: 0.07
Nodes (34): Modifier, WidgetPlugin, WidgetState, HqApplication, ImageVector, String, HqApplication, Intent (+26 more)

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
Nodes (9): Boolean, Int, List, MediaType, StateFlow, String, MediaItem, LocalMediaRepository (+1 more)

### Community 10 - "App Composition Root"
Cohesion: 0.06
Nodes (25): Float, Long, Channel, List, Long, Pair, Program, StateFlow (+17 more)

### Community 11 - "Live Preview Player"
Cohesion: 0.12
Nodes (15): File Structure, Launcher Completion Implementation Plan, Manual verification on device (post-implementation), Out of scope (deliberately), Task 10: Watch Next publisher + sync, Task 11: Final verification + knowledge graph refresh, Task 1: JVM test infrastructure, Task 2: M3U playlist parser (+7 more)

### Community 12 - "Ambient Mode Screen"
Cohesion: 0.35
Nodes (12): Boolean, Channel, List, String, AmbientBackdrops, AmbientPhoto, Artwork, BackdropProvider (+4 more)

### Community 13 - "Media Screen UI"
Cohesion: 0.22
Nodes (8): Boolean, Flow, List, Set, String, LauncherPrefsStore, DataStorePrefsStore, Keys

### Community 14 - "Widget Card Renderer"
Cohesion: 0.09
Nodes (23): Boolean, Channel, Int, Long, Modifier, String, Channel, Int (+15 more)

### Community 15 - "Home Screen"
Cohesion: 0.24
Nodes (5): List, Map, Program, String, XmltvParserTest

### Community 16 - "Focusable Glass Card"
Cohesion: 0.24
Nodes (6): Boolean, Context, Intent, DefaultHomeHelper, shouldPromptForDefault(), DefaultHomeHelperTest

### Community 17 - "Tools Screen"
Cohesion: 0.18
Nodes (8): Boolean, Flow, List, Set, String, InMemoryPrefsStoreTest, InMemoryPrefsStore, LauncherPrefsStore

### Community 18 - "Gradle Build Config"
Cohesion: 0.24
Nodes (4): Channel, List, String, M3uParserTest

### Community 19 - "Spatial Navigation Concept"
Cohesion: 0.10
Nodes (19): LrhqDreamService, Bitmap, String, Boolean, Int, List, Long, Modifier (+11 more)

### Community 20 - "Gradle Settings"
Cohesion: 0.29
Nodes (5): AmbientPhoto, Int, List, String, UnsplashClient

### Community 21 - "UI Module Build"
Cohesion: 0.32
Nodes (5): Channel, InputStream, List, String, M3uParser

### Community 22 - "Community 22"
Cohesion: 0.30
Nodes (13): Boolean, Channel, Float, Int, Modifier, String, ClockWeather(), heroTextShadow() (+5 more)

### Community 23 - "Community 23"
Cohesion: 0.29
Nodes (6): Architecture, Building, Design language, Knowledge graph, LivingRoom HQ, Spatial navigation

### Community 24 - "Community 24"
Cohesion: 0.47
Nodes (4): List, toWatchNextEntry(), WatchNextEntry, WatchNextPublisher

### Community 32 - "Community 32"
Cohesion: 0.18
Nodes (15): AmbientPhotoCacheSource, Boolean, List, Long, Map, StateFlow, String, DeviceCodeResponse (+7 more)

### Community 33 - "Community 33"
Cohesion: 0.17
Nodes (9): ActivityManager, Boolean, Float, Flow, Long, Pair, Command Center Dashboard, SystemMonitor (+1 more)

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
Cohesion: 0.11
Nodes (13): Boolean, ChannelEntity, Flow, GuideChannelEntity, IptvDao, List, Long, ProgramEntity (+5 more)

### Community 48 - "Community 48"
Cohesion: 0.32
Nodes (5): Modifier, StateFlow, String, MessageOverlay(), UiMessages

### Community 49 - "Community 49"
Cohesion: 0.40
Nodes (3): Channel, Context, ChannelPlayer

### Community 51 - "Community 51"
Cohesion: 0.31
Nodes (8): Boolean, ImageVector, Modifier, String, Zone, NavigationItem, Sidebar(), SidebarItem()

### Community 52 - "Community 52"
Cohesion: 0.38
Nodes (4): InputStream, Long, String, XmltvParser

### Community 55 - "Community 55"
Cohesion: 0.38
Nodes (5): Collection, List, String, fromAliases(), GuideChannelEntity

## Knowledge Gaps
- **205 isolated node(s):** `PreToolUse`, `PreToolUse`, `plugin`, `LrhqDatabase`, `WidgetRegistry` (+200 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **12 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `StatBar()` connect `System Monitoring` to `Widget Plugin Contract`, `Widget Card Renderer`?**
  _High betweenness centrality (0.217) - this node is a cross-community bridge._
- **Why does `PersistentChannelRepository` connect `Command Center Dashboard` to `Community 46`, `Community 7`?**
  _High betweenness centrality (0.189) - this node is a cross-community bridge._
- **Why does `DownloadJob` connect `System Monitoring` to `App Composition Root`, `Built-in Widgets`?**
  _High betweenness centrality (0.181) - this node is a cross-community bridge._
- **Are the 6 inferred relationships involving `PersistentChannelRepository` (e.g. with `.`empty playlist leaves channels empty`()` and `.`loadM3u replaces lineup and persists url`()`) actually correct?**
  _`PersistentChannelRepository` has 6 INFERRED edges - model-reasoned connections that need verification._
- **Are the 8 inferred relationships involving `InMemoryPrefsStore` (e.g. with `.`defaults are empty`()` and `.`writes are observable`()`) actually correct?**
  _`InMemoryPrefsStore` has 8 INFERRED edges - model-reasoned connections that need verification._
- **What connects `PreToolUse`, `PreToolUse`, `plugin` to the rest of the system?**
  _210 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Command Center Dashboard` be split into smaller, more focused modules?**
  _Cohesion score 0.08961038961038961 - nodes in this community are weakly interconnected._