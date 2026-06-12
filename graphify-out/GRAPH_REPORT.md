# Graph Report - .  (2026-06-12)

## Corpus Check
- Corpus is ~8,766 words - fits in a single context window. You may not need a graph.

## Summary
- 259 nodes · 433 edges · 23 communities (22 shown, 1 thin omitted)
- Extraction: 90% EXTRACTED · 10% INFERRED · 0% AMBIGUOUS · INFERRED: 44 edges (avg confidence: 0.81)
- Token cost: 123,880 input · 0 output

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
- [[_COMMUNITY_Live Preview Player|Live Preview Player]]
- [[_COMMUNITY_Ambient Mode Screen|Ambient Mode Screen]]
- [[_COMMUNITY_Media Screen UI|Media Screen UI]]
- [[_COMMUNITY_Widget Card Renderer|Widget Card Renderer]]
- [[_COMMUNITY_Home Screen|Home Screen]]
- [[_COMMUNITY_Focusable Glass Card|Focusable Glass Card]]
- [[_COMMUNITY_Tools Screen|Tools Screen]]
- [[_COMMUNITY_Spatial Navigation Concept|Spatial Navigation Concept]]

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 18 edges
2. `HqApplication` - 17 edges
3. `widget()` - 14 edges
4. `GlassPanel()` - 14 edges
5. `StatBar()` - 14 edges
6. `SpatialNavController` - 13 edges
7. `DemoChannelRepository` - 13 edges
8. `SystemMonitor` - 13 edges
9. `HomeScreen()` - 12 edges
10. `ChannelRepository` - 12 edges

## Surprising Connections (you probably didn't know these)
- `Demo Repository Pattern` --rationale_for--> `DemoChannelRepository`  [INFERRED]
  README.md → core/data/src/main/kotlin/com/livingroomhq/core/data/repo/ChannelRepository.kt
- `Demo Repository Pattern` --rationale_for--> `DemoMediaRepository`  [INFERRED]
  README.md → core/data/src/main/kotlin/com/livingroomhq/core/data/repo/MediaRepository.kt
- `OLED-Dark Glassmorphism Design System` --conceptually_related_to--> `FocusableGlassCard()`  [INFERRED]
  README.md → core/ui/src/main/kotlin/com/livingroomhq/core/ui/components/FocusableGlassCard.kt
- `Demo Repository Pattern` --rationale_for--> `DemoAmbientInfoRepository`  [INFERRED]
  README.md → core/data/src/main/kotlin/com/livingroomhq/core/data/repo/AmbientInfoRepository.kt
- `Command Center Dashboard` --references--> `SystemMonitor`  [EXTRACTED]
  README.md → core/data/src/main/kotlin/com/livingroomhq/core/data/repo/SystemMonitor.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Spatial zone navigation system** — livingroomhq_mainactivity_mainactivity, navigation_spatialnavcontroller_spatialnavcontroller, navigation_spatialnavhost_spatialnavhost, navigation_zone_zone, navigation_zone_zoneindirection [EXTRACTED 1.00]
- **Widget card pipeline (repos -> WidgetState -> WidgetCard in zones)** — widgets_builtinwidgets_registerbuiltinwidgets, components_widgetcard_widgetcard, screens_homescreen_homescreen, screens_toolsscreen_toolsscreen, screens_commandcenterscreen_commandcenterscreen [EXTRACTED 1.00]
- **Always-on live preview flow** — player_livepreview_livepreview, screens_homescreen_homescreen, screens_livescreen_livescreen [EXTRACTED 1.00]
- **Demo Repository Implementations Behind Stable Interfaces** — repo_ambientinforepository_demoambientinforepository, repo_channelrepository_demochannelrepository, repo_mediarepository_demomediarepository, readme_demo_repository_pattern [INFERRED 0.95]
- **OLED-Dark Glassmorphism Design System Components** — components_glasspanel_glasspanel, components_focusableglasscard_focusableglasscard, components_statbar_statbar, theme_theme_hqcolors, theme_theme_hqtype [INFERRED 0.95]
- **Widget Plugin Contract and Registry** — widget_widgetplugin_widgetplugin, widget_widgetplugin_widgetstate, widget_widgetplugin_widgetstat, widget_widgetregistry_widgetregistry [EXTRACTED 1.00]

## Communities (23 total, 1 thin omitted)

### Community 0 - "Command Center Dashboard"
Cohesion: 0.10
Nodes (25): HqApplication, List, SpatialNavController, SystemStats, Brush, Color, com, GlassPanel() (+17 more)

### Community 1 - "Spatial Zone Navigation"
Cohesion: 0.13
Nodes (13): Boolean, Direction, Long, Zone, Modifier, Zone, Direction, SpatialNavController (+5 more)

### Community 2 - "IPTV Channels & EPG"
Cohesion: 0.19
Nodes (9): Channel, List, Pair, StateFlow, String, Map, Program, ChannelRepository (+1 more)

### Community 3 - "Widget Plugin Contract"
Cohesion: 0.12
Nodes (16): Composable, Flow, Set, String, Unit, List, StateFlow, String (+8 more)

### Community 4 - "Data Models & Installed Apps"
Cohesion: 0.10
Nodes (16): Float, Long, List, String, LaunchableApp, Channel, LaunchableApp, MediaItem (+8 more)

### Community 5 - "System Monitoring"
Cohesion: 0.17
Nodes (9): ActivityManager, Boolean, Float, Flow, Long, Pair, SystemStats, Command Center Dashboard (+1 more)

### Community 6 - "Built-in Widgets"
Cohesion: 0.27
Nodes (18): Flow, HqApplication, Set, String, WidgetPlugin, WidgetRegistry, WidgetState, WidgetZone (+10 more)

### Community 7 - "Media Library"
Cohesion: 0.32
Nodes (7): Int, List, MediaItem, StateFlow, MediaType, DemoMediaRepository, MediaRepository

### Community 8 - "Main Activity Key Handling"
Cohesion: 0.18
Nodes (8): Boolean, Int, SpatialNavController, Bundle, ComponentActivity, KeyEvent, Edge D-pad navigation pattern, MainActivity

### Community 9 - "Ambient Info Services"
Cohesion: 0.30
Nodes (9): Flow, List, StateFlow, DownloadJob, Demo Repository Pattern, AmbientInfoRepository, DemoAmbientInfoRepository, ServiceStatus (+1 more)

### Community 10 - "App Composition Root"
Cohesion: 0.20
Nodes (9): AmbientInfoRepository, WidgetRegistry, Application, ChannelRepository, InstalledAppsRepository, HqApplication, Lazy composition root (no DI framework), MediaRepository (+1 more)

### Community 11 - "Live Preview Player"
Cohesion: 0.22
Nodes (7): Boolean, Channel, Modifier, HqApplication, SpatialNavController, LivePreview(), LiveScreen()

### Community 12 - "Ambient Mode Screen"
Cohesion: 0.36
Nodes (7): HqApplication, SpatialNavController, String, Ambient Mode idle behavior, ambientDate(), AmbientScreen(), ambientTime()

### Community 13 - "Media Screen UI"
Cohesion: 0.29
Nodes (7): HqApplication, List, MediaItem, SpatialNavController, String, MediaScreen(), PosterRail()

### Community 14 - "Widget Card Renderer"
Cohesion: 0.38
Nodes (6): Modifier, WidgetPlugin, WidgetState, DefaultWidgetBody(), WidgetCard(), Widget card system (live info before launch)

### Community 15 - "Home Screen"
Cohesion: 0.40
Nodes (5): HqApplication, SpatialNavController, String, HomeScreen(), timeNow()

### Community 16 - "Focusable Glass Card"
Cohesion: 0.33
Nodes (5): FocusableGlassCard(), Dp, Modifier, PaddingValues, Unit

### Community 17 - "Tools Screen"
Cohesion: 0.50
Nodes (3): HqApplication, SpatialNavController, ToolsScreen()

## Knowledge Gaps
- **82 isolated node(s):** `ChannelRepository`, `MediaRepository`, `AmbientInfoRepository`, `SystemMonitor`, `InstalledAppsRepository` (+77 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **1 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `StatBar()` connect `Command Center Dashboard` to `Live Preview Player`, `Media Screen UI`, `Built-in Widgets`, `Widget Card Renderer`?**
  _High betweenness centrality (0.433) - this node is a cross-community bridge._
- **Why does `WidgetStat` connect `Built-in Widgets` to `Command Center Dashboard`, `Widget Plugin Contract`, `Data Models & Installed Apps`?**
  _High betweenness centrality (0.299) - this node is a cross-community bridge._
- **Why does `DownloadJob` connect `Command Center Dashboard` to `Ambient Info Services`, `Data Models & Installed Apps`?**
  _High betweenness centrality (0.252) - this node is a cross-community bridge._
- **Are the 6 inferred relationships involving `GlassPanel()` (e.g. with `Color` and `NetworkPanel()`) actually correct?**
  _`GlassPanel()` has 6 INFERRED edges - model-reasoned connections that need verification._
- **Are the 6 inferred relationships involving `StatBar()` (e.g. with `DownloadJob` and `WidgetStat`) actually correct?**
  _`StatBar()` has 6 INFERRED edges - model-reasoned connections that need verification._
- **What connects `ChannelRepository`, `MediaRepository`, `AmbientInfoRepository` to the rest of the system?**
  _84 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Command Center Dashboard` be split into smaller, more focused modules?**
  _Cohesion score 0.10114942528735632 - nodes in this community are weakly interconnected._