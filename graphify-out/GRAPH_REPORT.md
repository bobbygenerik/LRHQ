# Graph Report - LRHQ  (2026-06-12)

## Corpus Check
- 51 files · ~16,171 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 408 nodes · 611 edges · 28 communities (25 shown, 3 thin omitted)
- Extraction: 89% EXTRACTED · 11% INFERRED · 0% AMBIGUOUS · INFERRED: 68 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `cf9054be`
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
- [[_COMMUNITY_Media Library|Media Library]]
- [[_COMMUNITY_Main Activity Key Handling|Main Activity Key Handling]]
- [[_COMMUNITY_Ambient Info Services|Ambient Info Services]]
- [[_COMMUNITY_App Composition Root|App Composition Root]]
- [[_COMMUNITY_Ambient Mode Screen|Ambient Mode Screen]]
- [[_COMMUNITY_Media Screen UI|Media Screen UI]]
- [[_COMMUNITY_Tools Screen|Tools Screen]]
- [[_COMMUNITY_Gradle Build Config|Gradle Build Config]]
- [[_COMMUNITY_Spatial Navigation Concept|Spatial Navigation Concept]]
- [[_COMMUNITY_Gradle Settings|Gradle Settings]]
- [[_COMMUNITY_UI Module Build|UI Module Build]]
- [[_COMMUNITY_Widget Module Build|Widget Module Build]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 19 edges
2. `PersistentChannelRepository` - 17 edges
3. `HqApplication` - 16 edges
4. `GlassPanel()` - 16 edges
5. `widget()` - 14 edges
6. `InMemoryPrefsStore` - 14 edges
7. `StatBar()` - 14 edges
8. `SystemMonitor` - 13 edges
9. `FocusableGlassCard()` - 13 edges
10. `SpatialNavController` - 12 edges

## Surprising Connections (you probably didn't know these)
- `Demo Repository Pattern` --rationale_for--> `DemoAmbientInfoRepository`  [INFERRED]
  README.md → core/data/src/main/kotlin/com/livingroomhq/core/data/repo/AmbientInfoRepository.kt
- `Demo Repository Pattern` --rationale_for--> `DemoMediaRepository`  [INFERRED]
  README.md → core/data/src/main/kotlin/com/livingroomhq/core/data/repo/MediaRepository.kt
- `OLED-Dark Glassmorphism Design System` --conceptually_related_to--> `FocusableGlassCard()`  [INFERRED]
  README.md → core/ui/src/main/kotlin/com/livingroomhq/core/ui/components/FocusableGlassCard.kt
- `Demo Repository Pattern` --rationale_for--> `DemoChannelRepository`  [INFERRED]
  README.md → core/data/src/main/kotlin/com/livingroomhq/core/data/repo/ChannelRepository.kt
- `Command Center Dashboard` --references--> `SystemMonitor`  [EXTRACTED]
  README.md → core/data/src/main/kotlin/com/livingroomhq/core/data/repo/SystemMonitor.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Spatial zone navigation system** — livingroomhq_mainactivity_mainactivity, navigation_spatialnavcontroller_spatialnavcontroller, navigation_spatialnavhost_spatialnavhost, navigation_zone_zone, navigation_zone_zoneindirection [EXTRACTED 1.00]
- **Always-on live preview flow** — player_livepreview_livepreview, screens_homescreen_homescreen, screens_livescreen_livescreen [EXTRACTED 1.00]
- **Widget card pipeline (repos -> WidgetState -> WidgetCard in zones)** — widgets_builtinwidgets_registerbuiltinwidgets, components_widgetcard_widgetcard, screens_homescreen_homescreen, screens_toolsscreen_toolsscreen, screens_commandcenterscreen_commandcenterscreen [EXTRACTED 1.00]
- **OLED-Dark Glassmorphism Design System Components** — components_glasspanel_glasspanel, components_focusableglasscard_focusableglasscard, components_statbar_statbar, theme_theme_hqcolors, theme_theme_hqtype [INFERRED 0.95]
- **Widget Plugin Contract and Registry** — widget_widgetplugin_widgetplugin, widget_widgetplugin_widgetstate, widget_widgetplugin_widgetstat, widget_widgetregistry_widgetregistry [EXTRACTED 1.00]
- **Demo Repository Implementations Behind Stable Interfaces** — repo_ambientinforepository_demoambientinforepository, repo_channelrepository_demochannelrepository, repo_mediarepository_demomediarepository, readme_demo_repository_pattern [INFERRED 0.95]

## Communities (28 total, 3 thin omitted)

### Community 0 - "Command Center Dashboard"
Cohesion: 0.07
Nodes (31): Boolean, Channel, Modifier, HqApplication, SpatialNavController, Boolean, HqApplication, List (+23 more)

### Community 1 - "Spatial Zone Navigation"
Cohesion: 0.08
Nodes (22): Boolean, Int, Intent, SpatialNavController, Boolean, Direction, Long, Zone (+14 more)

### Community 2 - "IPTV Channels & EPG"
Cohesion: 0.10
Nodes (21): Modifier, WidgetPlugin, WidgetState, LauncherPrefsStore, Modifier, HqApplication, SpatialNavController, String (+13 more)

### Community 3 - "Widget Plugin Contract"
Cohesion: 0.13
Nodes (18): Float, Long, Flow, List, StateFlow, DownloadJob, MediaItem, MediaType (+10 more)

### Community 4 - "Data Models & Installed Apps"
Cohesion: 0.18
Nodes (10): Channel, List, Pair, Program, StateFlow, String, Channel, Demo Repository Pattern (+2 more)

### Community 5 - "System Monitoring"
Cohesion: 0.12
Nodes (16): Composable, Flow, Set, String, Unit, List, StateFlow, String (+8 more)

### Community 6 - "Built-in Widgets"
Cohesion: 0.17
Nodes (9): ActivityManager, Boolean, Float, Flow, Long, Pair, SystemStats, Command Center Dashboard (+1 more)

### Community 7 - "Media Library"
Cohesion: 0.25
Nodes (19): Flow, HqApplication, Set, String, WidgetPlugin, WidgetRegistry, WidgetState, WidgetZone (+11 more)

### Community 8 - "Main Activity Key Handling"
Cohesion: 0.10
Nodes (19): AmbientInfoRepository, ChannelRepository, LauncherPrefsStore, WidgetRegistry, HqApplication, SpatialNavController, String, Application (+11 more)

### Community 9 - "Ambient Info Services"
Cohesion: 0.32
Nodes (7): Int, List, MediaItem, MediaType, StateFlow, DemoMediaRepository, MediaRepository

### Community 10 - "App Composition Root"
Cohesion: 0.22
Nodes (6): Boolean, List, String, LaunchableApp, LaunchableApp, InstalledAppsRepository

### Community 13 - "Media Screen UI"
Cohesion: 0.29
Nodes (6): Architecture, Building, Design language, Knowledge graph, LivingRoom HQ, Spatial navigation

### Community 17 - "Tools Screen"
Cohesion: 0.08
Nodes (17): Boolean, Flow, List, Set, String, Channel, List, Pair (+9 more)

### Community 18 - "Gradle Build Config"
Cohesion: 0.16
Nodes (12): HqApplication, List, SpatialNavController, SystemStats, Float, MediaType, com, CommandCenterScreen() (+4 more)

### Community 19 - "Spatial Navigation Concept"
Cohesion: 0.12
Nodes (15): File Structure, Launcher Completion Implementation Plan, Manual verification on device (post-implementation), Out of scope (deliberately), Task 10: Watch Next publisher + sync, Task 11: Final verification + knowledge graph refresh, Task 1: JVM test infrastructure, Task 2: M3U playlist parser (+7 more)

### Community 20 - "Gradle Settings"
Cohesion: 0.23
Nodes (8): Boolean, Flow, List, Set, String, LauncherPrefsStore, DataStorePrefsStore, Keys

### Community 21 - "UI Module Build"
Cohesion: 0.24
Nodes (6): Boolean, Intent, Context, DefaultHomeHelper, shouldPromptForDefault(), DefaultHomeHelperTest

### Community 22 - "Widget Module Build"
Cohesion: 0.28
Nodes (6): Channel, List, Program, String, Map, DemoLineup

### Community 23 - "Community 23"
Cohesion: 0.32
Nodes (5): Modifier, StateFlow, String, MessageOverlay(), UiMessages

### Community 25 - "Community 25"
Cohesion: 0.47
Nodes (4): List, toWatchNextEntry(), WatchNextEntry, WatchNextPublisher

### Community 26 - "Community 26"
Cohesion: 0.33
Nodes (4): Channel, List, String, M3uParser

## Knowledge Gaps
- **125 isolated node(s):** `LauncherPrefsStore`, `ChannelRepository`, `MediaRepository`, `AmbientInfoRepository`, `SystemMonitor` (+120 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **3 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `StatBar()` connect `Command Center Dashboard` to `IPTV Channels & EPG`, `Gradle Build Config`, `Media Library`?**
  _High betweenness centrality (0.233) - this node is a cross-community bridge._
- **Why does `HqApplication` connect `Main Activity Key Handling` to `Spatial Zone Navigation`, `Media Library`?**
  _High betweenness centrality (0.202) - this node is a cross-community bridge._
- **Why does `WidgetStat` connect `Media Library` to `Command Center Dashboard`, `Widget Plugin Contract`, `System Monitoring`?**
  _High betweenness centrality (0.187) - this node is a cross-community bridge._
- **Are the 6 inferred relationships involving `PersistentChannelRepository` (e.g. with `.`empty playlist keeps current lineup`()` and `.`loadM3u replaces lineup and persists url`()`) actually correct?**
  _`PersistentChannelRepository` has 6 INFERRED edges - model-reasoned connections that need verification._
- **Are the 8 inferred relationships involving `GlassPanel()` (e.g. with `Color` and `DefaultHomeBanner()`) actually correct?**
  _`GlassPanel()` has 8 INFERRED edges - model-reasoned connections that need verification._
- **What connects `LauncherPrefsStore`, `ChannelRepository`, `MediaRepository` to the rest of the system?**
  _128 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Command Center Dashboard` be split into smaller, more focused modules?**
  _Cohesion score 0.07112375533428165 - nodes in this community are weakly interconnected._