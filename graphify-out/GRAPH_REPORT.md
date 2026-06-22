# Graph Report - LRHQ  (2026-06-22)

## Corpus Check
- 90 files · ~964,499 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 990 nodes · 1760 edges · 58 communities (49 shown, 9 thin omitted)
- Extraction: 94% EXTRACTED · 6% INFERRED · 0% AMBIGUOUS · INFERRED: 112 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `a52fafb0`
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
- [[_COMMUNITY_Community 12|Community 12]]
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
- [[_COMMUNITY_Community 54|Community 54]]
- [[_COMMUNITY_Community 55|Community 55]]
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 58|Community 58]]
- [[_COMMUNITY_Community 65|Community 65]]
- [[_COMMUNITY_Community 66|Community 66]]

## God Nodes (most connected - your core abstractions)
1. `PersistentChannelRepository` - 28 edges
2. `FakeIptvDao` - 26 edges
3. `HomeScreen()` - 25 edges
4. `HqApplication` - 24 edges
5. `FocusableGlassCard()` - 24 edges
6. `InMemoryPrefsStore` - 23 edges
7. `AmbientPhotoCacheRepository` - 22 edges
8. `GooglePhotosPickerClient` - 22 edges
9. `IptvDao` - 22 edges
10. `DataStorePrefsStore` - 21 edges

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

## Communities (58 total, 9 thin omitted)

### Community 0 - "Command Center Dashboard"
Cohesion: 0.24
Nodes (16): Boolean, Channel, Float, ImageVector, Int, Modifier, Shadow, String (+8 more)

### Community 1 - "Community 1"
Cohesion: 0.13
Nodes (18): Modifier, WidgetPlugin, WidgetState, Brush, StatBar(), DefaultWidgetBody(), WidgetCard(), Color (+10 more)

### Community 2 - "IPTV Channels & EPG"
Cohesion: 0.08
Nodes (40): AmbientPhoto, android, Boolean, Channel, FocusRequester, HqApplication, LauncherFocusTarget, LauncherNavController (+32 more)

### Community 3 - "Widget Plugin Contract"
Cohesion: 0.08
Nodes (19): Float, Long, Channel, List, Long, Pair, Program, StateFlow (+11 more)

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
Cohesion: 0.05
Nodes (56): AmbientPhotoCacheStats, LauncherPrefsStore, Modifier, Boolean, List, Modifier, String, Boolean (+48 more)

### Community 12 - "Community 12"
Cohesion: 0.26
Nodes (16): android, HqApplication, Modifier, String, WidgetPlugin, WidgetState, AmbientClock(), ambientDate() (+8 more)

### Community 13 - "Media Screen UI"
Cohesion: 0.29
Nodes (6): Architecture, Building, Design language, Knowledge graph, LivingRoom HQ, Spatial navigation

### Community 17 - "Tools Screen"
Cohesion: 0.10
Nodes (14): Boolean, ChannelEntity, Flow, GuideChannelEntity, IptvDao, List, Long, ProgramBrief (+6 more)

### Community 18 - "Community 18"
Cohesion: 0.50
Nodes (3): String, open(), Uri

### Community 19 - "Spatial Navigation Concept"
Cohesion: 0.12
Nodes (15): File Structure, Launcher Completion Implementation Plan, Manual verification on device (post-implementation), Out of scope (deliberately), Task 10: Watch Next publisher + sync, Task 11: Final verification + knowledge graph refresh, Task 1: JVM test infrastructure, Task 2: M3U playlist parser (+7 more)

### Community 20 - "Gradle Settings"
Cohesion: 0.15
Nodes (9): Boolean, Flow, Int, List, Set, String, LauncherPrefsStore, DataStorePrefsStore (+1 more)

### Community 21 - "UI Module Build"
Cohesion: 0.24
Nodes (6): Boolean, Context, Intent, DefaultHomeHelper, shouldPromptForDefault(), DefaultHomeHelperTest

### Community 22 - "Community 22"
Cohesion: 0.15
Nodes (16): AmbientPhotoCacheSource, Boolean, List, Long, Map, StateFlow, String, DeviceCodeResponse (+8 more)

### Community 23 - "Community 23"
Cohesion: 0.15
Nodes (16): Boolean, HqApplication, LaunchableApp, List, Modifier, String, Float, MediaType (+8 more)

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
Cohesion: 0.11
Nodes (30): Boolean, Channel, Int, Long, Modifier, String, android, Boolean (+22 more)

### Community 29 - "Community 29"
Cohesion: 0.14
Nodes (10): Boolean, ChannelEntity, Flow, GuideChannelEntity, List, Long, ProgramBrief, ProgramEntity (+2 more)

### Community 30 - "Community 30"
Cohesion: 0.10
Nodes (20): android, Bundle, Channel, HqApplication, Shadow, String, Channel, ExoPlayer (+12 more)

### Community 31 - "Community 31"
Cohesion: 0.10
Nodes (9): Boolean, Flow, Int, List, Set, String, InMemoryPrefsStoreTest, InMemoryPrefsStore (+1 more)

### Community 33 - "Community 33"
Cohesion: 0.16
Nodes (16): Bitmap, String, Boolean, Int, List, Long, Modifier, String (+8 more)

### Community 34 - "Community 34"
Cohesion: 0.16
Nodes (10): Boolean, Channel, Context, ExoPlayer, Int, MediaItem, String, Tracks (+2 more)

### Community 35 - "Community 35"
Cohesion: 0.18
Nodes (17): Flow, List, StateFlow, String, WeatherCondition, DownloadJob, AmbientInfoRepository, httpGet() (+9 more)

### Community 36 - "Community 36"
Cohesion: 0.22
Nodes (5): List, Map, Program, String, XmltvParserTest

### Community 37 - "Community 37"
Cohesion: 0.06
Nodes (27): Boolean, ImageVector, Modifier, String, Zone, Boolean, Bundle, Int (+19 more)

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
Cohesion: 0.43
Nodes (4): Program, fromModel(), ProgramBrief, ProgramEntity

### Community 48 - "Community 48"
Cohesion: 0.17
Nodes (9): ActivityManager, Boolean, Float, Flow, Long, Pair, Command Center Dashboard, SystemMonitor (+1 more)

### Community 55 - "Community 55"
Cohesion: 0.33
Nodes (5): FocusRequester, HqApplication, LauncherFocusTarget, Modifier, fullscreenFocusRestore()

### Community 65 - "Community 65"
Cohesion: 0.50
Nodes (3): Modifier, HqDimens, zonePadding()

### Community 66 - "Community 66"
Cohesion: 0.50
Nodes (3): initialFocus(), FocusRequester, Modifier

## Knowledge Gaps
- **253 isolated node(s):** `LauncherPrefsStore`, `LrhqDatabase`, `ChannelRepository`, `MediaRepository`, `AmbientInfoRepository` (+248 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **9 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `HqApplication` connect `Main Activity Key Handling` to `System Monitoring`, `Community 12`, `Community 37`?**
  _High betweenness centrality (0.222) - this node is a cross-community bridge._
- **Why does `StatBar()` connect `Community 1` to `Community 28`, `System Monitoring`?**
  _High betweenness centrality (0.194) - this node is a cross-community bridge._
- **Why does `PersistentChannelRepository` connect `Data Models & Installed Apps` to `Main Activity Key Handling`, `Tools Screen`?**
  _High betweenness centrality (0.183) - this node is a cross-community bridge._
- **Are the 6 inferred relationships involving `PersistentChannelRepository` (e.g. with `.`empty playlist leaves channels empty`()` and `.`loadM3u replaces lineup and persists url`()`) actually correct?**
  _`PersistentChannelRepository` has 6 INFERRED edges - model-reasoned connections that need verification._
- **Are the 12 inferred relationships involving `HomeScreen()` (e.g. with `.onCreate()` and `AmbientScreen()`) actually correct?**
  _`HomeScreen()` has 12 INFERRED edges - model-reasoned connections that need verification._
- **Are the 15 inferred relationships involving `FocusableGlassCard()` (e.g. with `ConfirmDialog()` and `WidgetCard()`) actually correct?**
  _`FocusableGlassCard()` has 15 INFERRED edges - model-reasoned connections that need verification._
- **What connects `LauncherPrefsStore`, `LrhqDatabase`, `ChannelRepository` to the rest of the system?**
  _258 weakly-connected nodes found - possible documentation gaps or missing edges._