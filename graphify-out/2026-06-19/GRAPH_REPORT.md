# Graph Report - LRHQ  (2026-06-19)

## Corpus Check
- 88 files · ~960,861 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 937 nodes · 1658 edges · 61 communities (48 shown, 13 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 119 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `4c666c7c`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Command Center Dashboard|Command Center Dashboard]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_IPTV Channels & EPG|IPTV Channels & EPG]]
- [[_COMMUNITY_Widget Plugin Contract|Widget Plugin Contract]]
- [[_COMMUNITY_Data Models & Installed Apps|Data Models & Installed Apps]]
- [[_COMMUNITY_System Monitoring|System Monitoring]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Main Activity Key Handling|Main Activity Key Handling]]
- [[_COMMUNITY_Ambient Info Services|Ambient Info Services]]
- [[_COMMUNITY_App Composition Root|App Composition Root]]
- [[_COMMUNITY_Ambient Mode Screen|Ambient Mode Screen]]
- [[_COMMUNITY_Media Screen UI|Media Screen UI]]
- [[_COMMUNITY_Tools Screen|Tools Screen]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Spatial Navigation Concept|Spatial Navigation Concept]]
- [[_COMMUNITY_Gradle Settings|Gradle Settings]]
- [[_COMMUNITY_UI Module Build|UI Module Build]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
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
- [[_COMMUNITY_Community 42|Community 42]]
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
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 58|Community 58]]
- [[_COMMUNITY_Community 59|Community 59]]
- [[_COMMUNITY_Community 60|Community 60]]

## God Nodes (most connected - your core abstractions)
1. `PersistentChannelRepository` - 25 edges
2. `HqApplication` - 24 edges
3. `FakeIptvDao` - 24 edges
4. `FocusableGlassCard()` - 23 edges
5. `AmbientPhotoCacheRepository` - 22 edges
6. `GooglePhotosPickerClient` - 22 edges
7. `HomeScreen()` - 22 edges
8. `GlassPanel()` - 21 edges
9. `MainActivity` - 20 edges
10. `IptvDao` - 20 edges

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

## Hyperedges (group relationships)
- **Spatial zone navigation system** — livingroomhq_mainactivity_mainactivity, navigation_spatialnavcontroller_spatialnavcontroller, navigation_spatialnavhost_spatialnavhost, navigation_zone_zone, navigation_zone_zoneindirection [EXTRACTED 1.00]
- **Always-on live preview flow** — player_livepreview_livepreview, screens_homescreen_homescreen, screens_livescreen_livescreen [EXTRACTED 1.00]
- **Widget card pipeline (repos -> WidgetState -> WidgetCard in zones)** — widgets_builtinwidgets_registerbuiltinwidgets, components_widgetcard_widgetcard, screens_homescreen_homescreen, screens_toolsscreen_toolsscreen, screens_commandcenterscreen_commandcenterscreen [EXTRACTED 1.00]
- **OLED-Dark Glassmorphism Design System Components** — components_glasspanel_glasspanel, components_focusableglasscard_focusableglasscard, components_statbar_statbar, theme_theme_hqcolors, theme_theme_hqtype [INFERRED 0.95]
- **Widget Plugin Contract and Registry** — widget_widgetplugin_widgetplugin, widget_widgetplugin_widgetstate, widget_widgetplugin_widgetstat, widget_widgetregistry_widgetregistry [EXTRACTED 1.00]
- **Demo Repository Implementations Behind Stable Interfaces** — repo_ambientinforepository_demoambientinforepository, repo_channelrepository_demochannelrepository, repo_mediarepository_demomediarepository, readme_demo_repository_pattern [INFERRED 0.95]

## Communities (61 total, 13 thin omitted)

### Community 0 - "Command Center Dashboard"
Cohesion: 0.12
Nodes (25): Boolean, Channel, Float, Int, Modifier, String, Brush, StatBar() (+17 more)

### Community 1 - "Community 1"
Cohesion: 0.21
Nodes (5): Bundle, Intent, LauncherNavController, Edge D-pad navigation pattern, MainActivity

### Community 2 - "IPTV Channels & EPG"
Cohesion: 0.08
Nodes (38): android, LauncherPrefsStore, Modifier, AmbientPhoto, Boolean, HqApplication, LauncherFocusTarget, LauncherNavController (+30 more)

### Community 3 - "Widget Plugin Contract"
Cohesion: 0.06
Nodes (37): Float, Long, Flow, List, StateFlow, String, Channel, List (+29 more)

### Community 4 - "Data Models & Installed Apps"
Cohesion: 0.09
Nodes (35): Iterable, List, Long, Map, Program, String, Boolean, Channel (+27 more)

### Community 5 - "System Monitoring"
Cohesion: 0.09
Nodes (35): Flow, HqApplication, Set, String, WidgetPlugin, WidgetRegistry, WidgetState, WidgetZone (+27 more)

### Community 6 - "Community 6"
Cohesion: 0.14
Nodes (11): Activity, Boolean, Context, Int, LaunchableApp, List, StateFlow, String (+3 more)

### Community 7 - "Community 7"
Cohesion: 0.15
Nodes (18): Any, AmbientPhoto, Bitmap, Boolean, Int, List, Long, MutableSet (+10 more)

### Community 8 - "Main Activity Key Handling"
Cohesion: 0.09
Nodes (31): AmbientInfoRepository, AmbientPhotoCacheRepository, Boolean, Channel, List, String, AmbientPhoto, ChannelRepository (+23 more)

### Community 9 - "Ambient Info Services"
Cohesion: 0.22
Nodes (9): Boolean, Int, List, MediaItem, MediaType, StateFlow, String, LocalMediaRepository (+1 more)

### Community 10 - "App Composition Root"
Cohesion: 0.08
Nodes (39): AmbientPhotoCacheStats, List, Modifier, String, Boolean, Color, Composable, CustomSettings (+31 more)

### Community 12 - "Ambient Mode Screen"
Cohesion: 0.31
Nodes (8): Boolean, ImageVector, Modifier, String, Zone, NavigationItem, Sidebar(), SidebarItem()

### Community 13 - "Media Screen UI"
Cohesion: 0.29
Nodes (6): Architecture, Building, Design language, Knowledge graph, LivingRoom HQ, Spatial navigation

### Community 17 - "Tools Screen"
Cohesion: 0.06
Nodes (21): Boolean, Flow, List, Set, String, Boolean, ChannelEntity, Flow (+13 more)

### Community 18 - "Community 18"
Cohesion: 0.43
Nodes (7): HqApplication, ImageVector, String, CommandCenterScreen(), getLocalIpAddress(), getTailscaleIpAddress(), MetricCard()

### Community 19 - "Spatial Navigation Concept"
Cohesion: 0.12
Nodes (15): File Structure, Launcher Completion Implementation Plan, Manual verification on device (post-implementation), Out of scope (deliberately), Task 10: Watch Next publisher + sync, Task 11: Final verification + knowledge graph refresh, Task 1: JVM test infrastructure, Task 2: M3U playlist parser (+7 more)

### Community 20 - "Gradle Settings"
Cohesion: 0.22
Nodes (8): Boolean, Flow, List, Set, String, LauncherPrefsStore, DataStorePrefsStore, Keys

### Community 21 - "UI Module Build"
Cohesion: 0.24
Nodes (6): Boolean, Context, Intent, DefaultHomeHelper, shouldPromptForDefault(), DefaultHomeHelperTest

### Community 22 - "Community 22"
Cohesion: 0.15
Nodes (16): AmbientPhotoCacheSource, Boolean, List, Long, Map, StateFlow, String, DeviceCodeResponse (+8 more)

### Community 23 - "Community 23"
Cohesion: 0.06
Nodes (42): HqApplication, LauncherFocusTarget, Modifier, Modifier, WidgetPlugin, WidgetState, Bundle, Channel (+34 more)

### Community 24 - "Community 24"
Cohesion: 0.23
Nodes (4): Channel, List, String, M3uParserTest

### Community 25 - "Community 25"
Cohesion: 0.47
Nodes (4): List, toWatchNextEntry(), WatchNextEntry, WatchNextPublisher

### Community 26 - "Community 26"
Cohesion: 0.29
Nodes (6): Channel, InputStream, List, MutableSet, String, M3uParser

### Community 28 - "Community 28"
Cohesion: 0.12
Nodes (28): Boolean, Channel, Int, Long, Modifier, String, Boolean, Channel (+20 more)

### Community 29 - "Community 29"
Cohesion: 0.16
Nodes (9): Boolean, ChannelEntity, Flow, GuideChannelEntity, List, Long, ProgramEntity, String (+1 more)

### Community 30 - "Community 30"
Cohesion: 0.20
Nodes (8): Channel, ExoPlayer, Int, String, Tracks, LivePreviewEngine, PlayerView, TextureView

### Community 31 - "Community 31"
Cohesion: 0.32
Nodes (5): Modifier, StateFlow, String, MessageOverlay(), UiMessages

### Community 33 - "Community 33"
Cohesion: 0.16
Nodes (16): Bitmap, String, Boolean, Int, List, Long, Modifier, String (+8 more)

### Community 34 - "Community 34"
Cohesion: 0.16
Nodes (10): Boolean, Channel, Context, ExoPlayer, Int, MediaItem, String, Tracks (+2 more)

### Community 35 - "Community 35"
Cohesion: 0.17
Nodes (9): ActivityManager, Boolean, Float, Flow, Long, Pair, Command Center Dashboard, SystemMonitor (+1 more)

### Community 36 - "Community 36"
Cohesion: 0.22
Nodes (5): List, Map, Program, String, XmltvParserTest

### Community 37 - "Community 37"
Cohesion: 0.28
Nodes (15): HqApplication, Modifier, String, WidgetPlugin, WidgetState, AmbientClock(), ambientDate(), ambientMeridiem() (+7 more)

### Community 38 - "Community 38"
Cohesion: 0.25
Nodes (7): Context, IptvDao, build(), LrhqDatabase, migrate(), RoomDatabase, SupportSQLiteDatabase

### Community 39 - "Community 39"
Cohesion: 0.36
Nodes (4): InputStream, Long, String, XmltvParser

### Community 40 - "Community 40"
Cohesion: 0.29
Nodes (5): AmbientPhoto, Int, List, String, UnsplashClient

### Community 42 - "Community 42"
Cohesion: 0.36
Nodes (4): StateFlow, FullscreenFocusReturn, LauncherFocusReturnEvent, LauncherFocusTarget

### Community 43 - "Community 43"
Cohesion: 0.38
Nodes (5): Collection, List, String, fromAliases(), GuideChannelEntity

### Community 44 - "Community 44"
Cohesion: 0.40
Nodes (3): Channel, Context, ChannelPlayer

### Community 45 - "Community 45"
Cohesion: 0.60
Nodes (3): Channel, ChannelEntity, fromModel()

### Community 46 - "Community 46"
Cohesion: 0.60
Nodes (3): Program, fromModel(), ProgramEntity

### Community 48 - "Community 48"
Cohesion: 0.36
Nodes (3): Float, MediaType, WatchNextTest

### Community 53 - "Community 53"
Cohesion: 0.38
Nodes (4): Boolean, Int, Long, KeyEvent

### Community 59 - "Community 59"
Cohesion: 0.50
Nodes (3): Modifier, Zone, LauncherNavHost()

## Knowledge Gaps
- **239 isolated node(s):** `PreToolUse`, `PreToolUse`, `plugin`, `LauncherPrefsStore`, `LrhqDatabase` (+234 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **13 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `HqApplication` connect `Main Activity Key Handling` to `Community 1`, `System Monitoring`, `Community 37`?**
  _High betweenness centrality (0.216) - this node is a cross-community bridge._
- **Why does `StatBar()` connect `Command Center Dashboard` to `Widget Plugin Contract`, `System Monitoring`, `Community 18`, `Community 23`, `Community 28`?**
  _High betweenness centrality (0.212) - this node is a cross-community bridge._
- **Why does `DownloadJob` connect `Widget Plugin Contract` to `Command Center Dashboard`?**
  _High betweenness centrality (0.176) - this node is a cross-community bridge._
- **Are the 6 inferred relationships involving `PersistentChannelRepository` (e.g. with `.`empty playlist leaves channels empty`()` and `.`loadM3u replaces lineup and persists url`()`) actually correct?**
  _`PersistentChannelRepository` has 6 INFERRED edges - model-reasoned connections that need verification._
- **Are the 15 inferred relationships involving `FocusableGlassCard()` (e.g. with `ConfirmDialog()` and `WidgetCard()`) actually correct?**
  _`FocusableGlassCard()` has 15 INFERRED edges - model-reasoned connections that need verification._
- **What connects `PreToolUse`, `PreToolUse`, `plugin` to the rest of the system?**
  _244 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Command Center Dashboard` be split into smaller, more focused modules?**
  _Cohesion score 0.11576354679802955 - nodes in this community are weakly interconnected._