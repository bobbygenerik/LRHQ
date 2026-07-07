# Graph Report - LRHQ  (2026-07-07)

## Corpus Check
- 119 files · ~974,647 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1417 nodes · 2570 edges · 88 communities (75 shown, 13 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 169 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `a3e3b956`
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
- [[_COMMUNITY_Community 51|Community 51]]
- [[_COMMUNITY_Community 52|Community 52]]
- [[_COMMUNITY_Community 53|Community 53]]
- [[_COMMUNITY_Community 54|Community 54]]
- [[_COMMUNITY_Community 55|Community 55]]
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 58|Community 58]]
- [[_COMMUNITY_Community 59|Community 59]]
- [[_COMMUNITY_Community 60|Community 60]]
- [[_COMMUNITY_Community 61|Community 61]]
- [[_COMMUNITY_Community 62|Community 62]]
- [[_COMMUNITY_Community 63|Community 63]]
- [[_COMMUNITY_Community 64|Community 64]]
- [[_COMMUNITY_Community 65|Community 65]]
- [[_COMMUNITY_Community 66|Community 66]]
- [[_COMMUNITY_Community 67|Community 67]]
- [[_COMMUNITY_Community 68|Community 68]]
- [[_COMMUNITY_Community 69|Community 69]]
- [[_COMMUNITY_Community 70|Community 70]]
- [[_COMMUNITY_Community 71|Community 71]]
- [[_COMMUNITY_Community 72|Community 72]]
- [[_COMMUNITY_Community 73|Community 73]]
- [[_COMMUNITY_Community 74|Community 74]]
- [[_COMMUNITY_Community 75|Community 75]]
- [[_COMMUNITY_Community 76|Community 76]]
- [[_COMMUNITY_Community 77|Community 77]]
- [[_COMMUNITY_Community 79|Community 79]]
- [[_COMMUNITY_Community 80|Community 80]]
- [[_COMMUNITY_Community 81|Community 81]]
- [[_COMMUNITY_Community 82|Community 82]]
- [[_COMMUNITY_Community 83|Community 83]]
- [[_COMMUNITY_Community 84|Community 84]]
- [[_COMMUNITY_Community 85|Community 85]]
- [[_COMMUNITY_Community 86|Community 86]]
- [[_COMMUNITY_Community 87|Community 87]]

## God Nodes (most connected - your core abstractions)
1. `FakeIptvDao` - 41 edges
2. `PersistentChannelRepository` - 38 edges
3. `HomeScreen()` - 28 edges
4. `IptvDao` - 28 edges
5. `HqApplication` - 27 edges
6. `InMemoryPrefsStore` - 27 edges
7. `PersistentChannelRepositoryTest` - 27 edges
8. `LivePreviewEngine` - 26 edges
9. `DataStorePrefsStore` - 26 edges
10. `LauncherPrefsStore` - 26 edges

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

## Communities (88 total, 13 thin omitted)

### Community 0 - "Command Center Dashboard"
Cohesion: 0.24
Nodes (17): Boolean, Channel, Float, ImageVector, Int, Modifier, Shadow, String (+9 more)

### Community 1 - "Community 1"
Cohesion: 0.38
Nodes (6): tvFocusBorder(), tvFocusScale(), Boolean, Dp, Modifier, Shape

### Community 2 - "IPTV Channels & EPG"
Cohesion: 0.07
Nodes (46): Boolean, LauncherNavController, AmbientPhoto, android, Boolean, Channel, FocusRequester, FullscreenFocusReturn (+38 more)

### Community 3 - "Widget Plugin Contract"
Cohesion: 0.06
Nodes (30): ActivityManager, Float, Long, Channel, Int, List, Long, Map (+22 more)

### Community 4 - "Data Models & Installed Apps"
Cohesion: 0.08
Nodes (33): Result, T, Iterable, List, Long, Map, Program, String (+25 more)

### Community 5 - "System Monitoring"
Cohesion: 0.09
Nodes (35): Flow, HqApplication, Set, String, WidgetPlugin, WidgetRegistry, WidgetState, WidgetZone (+27 more)

### Community 6 - "Community 6"
Cohesion: 0.22
Nodes (11): List, StateFlow, String, Throwable, Failed, FaultEntry, FaultLog, Ok (+3 more)

### Community 7 - "Community 7"
Cohesion: 0.16
Nodes (17): AmbientPhoto, Any, Bitmap, Boolean, Int, List, Long, MutableSet (+9 more)

### Community 8 - "Main Activity Key Handling"
Cohesion: 0.06
Nodes (29): AmbientInfoRepository, AmbientPhoto, AmbientPhotoCacheRepository, Boolean, ChannelRepository, FullscreenFocusReturn, GooglePhotosPickerClient, InstalledAppsRepository (+21 more)

### Community 9 - "Ambient Info Services"
Cohesion: 0.24
Nodes (10): StateFlow, SystemMonitor, ViewModelProvider, CommandCenterEffect, CommandCenterEvent, CommandCenterUiState, CommandCenterViewModel, factory() (+2 more)

### Community 10 - "App Composition Root"
Cohesion: 0.16
Nodes (22): AmbientPhotoCacheStats, Boolean, Color, Composable, CustomSettings, FocusRequester, String, Unit (+14 more)

### Community 12 - "Community 12"
Cohesion: 0.22
Nodes (15): Boolean, Channel, Collection, List, Long, Map, Pair, Program (+7 more)

### Community 13 - "Media Screen UI"
Cohesion: 0.29
Nodes (6): Architecture, Building, Design language, Knowledge graph, LivingRoom HQ, Navigation

### Community 17 - "Tools Screen"
Cohesion: 0.08
Nodes (14): Boolean, ChannelEntity, Flow, GuideChannelEntity, IptvDao, List, Long, PersistentChannelRepository (+6 more)

### Community 18 - "Community 18"
Cohesion: 0.50
Nodes (3): String, open(), Uri

### Community 19 - "Spatial Navigation Concept"
Cohesion: 0.12
Nodes (15): File Structure, Launcher Completion Implementation Plan, Manual verification on device (post-implementation), Out of scope (deliberately), Task 10: Watch Next publisher + sync, Task 11: Final verification + knowledge graph refresh, Task 1: JVM test infrastructure, Task 2: M3U playlist parser (+7 more)

### Community 20 - "Gradle Settings"
Cohesion: 0.13
Nodes (10): Boolean, Flow, HttpSyncMetadata, Int, List, Set, String, LauncherPrefsStore (+2 more)

### Community 21 - "UI Module Build"
Cohesion: 0.24
Nodes (6): Boolean, Context, Intent, DefaultHomeHelper, shouldPromptForDefault(), DefaultHomeHelperTest

### Community 22 - "Community 22"
Cohesion: 0.15
Nodes (16): AmbientPhotoCacheSource, Boolean, JSONObject, List, Long, Map, StateFlow, String (+8 more)

### Community 23 - "Community 23"
Cohesion: 0.19
Nodes (12): LauncherPrefsStore, Modifier, dpadPressable(), FocusableGlassCard(), isOkKey(), Boolean, Dp, Modifier (+4 more)

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
Cohesion: 0.05
Nodes (53): Boolean, Channel, Int, Long, Modifier, String, android, Boolean (+45 more)

### Community 29 - "Community 29"
Cohesion: 0.14
Nodes (10): Boolean, ChannelEntity, Flow, GuideChannelEntity, List, Long, ProgramBrief, ProgramEntity (+2 more)

### Community 30 - "Community 30"
Cohesion: 0.16
Nodes (15): Boolean, Channel, ExoPlayer, Int, String, Tracks, Fullscreen, Idle (+7 more)

### Community 31 - "Community 31"
Cohesion: 0.08
Nodes (10): Boolean, Flow, HttpSyncMetadata, Int, List, Set, String, InMemoryPrefsStoreTest (+2 more)

### Community 33 - "Community 33"
Cohesion: 0.13
Nodes (18): Bitmap, String, Boolean, Int, List, Long, Modifier, String (+10 more)

### Community 34 - "Community 34"
Cohesion: 0.16
Nodes (10): Boolean, Channel, Context, ExoPlayer, Int, MediaItem, String, Tracks (+2 more)

### Community 35 - "Community 35"
Cohesion: 0.22
Nodes (9): Boolean, Int, List, MediaItem, MediaType, StateFlow, String, LocalMediaRepository (+1 more)

### Community 36 - "Community 36"
Cohesion: 0.22
Nodes (5): List, Map, Program, String, XmltvParserTest

### Community 37 - "Community 37"
Cohesion: 0.06
Nodes (30): Modifier, Boolean, ImageVector, Modifier, String, Zone, Boolean, Bundle (+22 more)

### Community 38 - "Community 38"
Cohesion: 0.25
Nodes (7): Context, IptvDao, build(), LrhqDatabase, migrate(), RoomDatabase, SupportSQLiteDatabase

### Community 39 - "Community 39"
Cohesion: 0.36
Nodes (4): InputStream, Long, String, XmltvParser

### Community 40 - "Community 40"
Cohesion: 0.20
Nodes (7): build_handler(), CaptionServer, CaptionSession, Cue, main(), parse_args(), Namespace

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
Cohesion: 0.18
Nodes (17): Flow, List, StateFlow, String, WeatherCondition, DownloadJob, AmbientInfoRepository, httpGet() (+9 more)

### Community 51 - "Community 51"
Cohesion: 0.13
Nodes (16): Modifier, String, android, Bundle, Channel, HqApplication, Shadow, String (+8 more)

### Community 52 - "Community 52"
Cohesion: 0.26
Nodes (16): AmbientViewModel, android, Modifier, String, WidgetPlugin, WidgetState, AmbientClock(), ambientDate() (+8 more)

### Community 54 - "Community 54"
Cohesion: 0.50
Nodes (3): Conversation & Actions, Device installs (hard rule), graphify

### Community 55 - "Community 55"
Cohesion: 0.17
Nodes (10): FocusRequester, HqApplication, LauncherFocusTarget, Modifier, FocusRequester, FullscreenFocusReturn, LauncherFocusTarget, Modifier (+2 more)

### Community 59 - "Community 59"
Cohesion: 0.17
Nodes (16): List, CustomSettings, Intent, String, PublicPlaylist, SamplePlaylistsPanel(), ActionUi, ClearCache (+8 more)

### Community 62 - "Community 62"
Cohesion: 0.16
Nodes (16): Brush, StatBar(), Color, Float, Modifier, String, Color, String (+8 more)

### Community 63 - "Community 63"
Cohesion: 0.14
Nodes (11): Activity, Boolean, Context, Int, LaunchableApp, List, StateFlow, String (+3 more)

### Community 64 - "Community 64"
Cohesion: 0.12
Nodes (25): AmbientPhotoCacheRepository, ChannelRepository, GooglePhotosPickerClient, LauncherPrefsStore, StateFlow, String, ViewModelProvider, ClearGuide (+17 more)

### Community 65 - "Community 65"
Cohesion: 0.50
Nodes (4): Modifier, homeZonePadding(), HqDimens, zonePadding()

### Community 66 - "Community 66"
Cohesion: 0.24
Nodes (8): initialFocus(), rememberTvFocusManager(), TvFocusManager, tvInitialFocus(), FocusRequester, Modifier, FocusRequester, Modifier

### Community 67 - "Community 67"
Cohesion: 0.13
Nodes (18): AmbientInfoRepository, Channel, ChannelRepository, List, Long, StateFlow, String, ViewModelProvider (+10 more)

### Community 68 - "Community 68"
Cohesion: 0.30
Nodes (11): Boolean, Channel, List, String, AmbientBackdrops, AmbientPhoto, Artwork, BackdropProvider (+3 more)

### Community 69 - "Community 69"
Cohesion: 0.50
Nodes (3): LRHQ live caption server, Run, Setup

### Community 72 - "Community 72"
Cohesion: 0.18
Nodes (11): ByteArray, String, Int, InputStream, IOException, ConditionalFetchResult, conditionalHttpFetch(), HttpSyncMetadata (+3 more)

### Community 73 - "Community 73"
Cohesion: 0.09
Nodes (26): AmbientPhoto, Int, List, String, Boolean, Long, String, Array (+18 more)

### Community 74 - "Community 74"
Cohesion: 0.24
Nodes (9): Boolean, Context, LaunchableApp, List, StateFlow, String, mergeOrder(), ToolsUiState (+1 more)

### Community 75 - "Community 75"
Cohesion: 0.22
Nodes (13): androidx, Boolean, LaunchableApp, LauncherNavController, Modifier, String, Drawable, AppActionMenu() (+5 more)

### Community 76 - "Community 76"
Cohesion: 0.32
Nodes (11): ArmGridFocus, ArmPreviewFocus, FocusCategories, FocusChannel, LaunchPlayer, LiveTvEffect, LiveTvEvent, OpenChannel (+3 more)

### Community 77 - "Community 77"
Cohesion: 0.27
Nodes (9): ConfirmDialog(), ModalGlassPanel(), ModalMessage(), ModalTitle(), Modifier, String, Modifier, PaddingValues (+1 more)

### Community 79 - "Community 79"
Cohesion: 0.22
Nodes (8): AmbientUiState, AmbientViewModel, Channel, Pair, Program, StateFlow, String, ViewModel

### Community 80 - "Community 80"
Cohesion: 0.22
Nodes (9): factory(), AmbientPhoto, Boolean, ChannelRepository, Flow, List, MediaRepository, ViewModelProvider (+1 more)

### Community 81 - "Community 81"
Cohesion: 0.25
Nodes (7): Boolean, Channel, List, StateFlow, String, LiveTvUiState, LiveTvViewModel

### Community 82 - "Community 82"
Cohesion: 0.29
Nodes (7): Boolean, List, Modifier, String, CustomButtonToggle(), GlassTextField(), PublicPlaylist

### Community 83 - "Community 83"
Cohesion: 0.36
Nodes (3): Float, MediaType, WatchNextTest

### Community 84 - "Community 84"
Cohesion: 0.47
Nodes (5): Modifier, WidgetPlugin, WidgetState, DefaultWidgetBody(), WidgetCard()

### Community 85 - "Community 85"
Cohesion: 0.33
Nodes (5): LoadingRow(), Color, ImageVector, Modifier, String

### Community 86 - "Community 86"
Cohesion: 0.50
Nodes (4): InstalledAppsRepository, LauncherPrefsStore, ViewModelProvider, factory()

### Community 87 - "Community 87"
Cohesion: 0.67
Nodes (3): ChannelRepository, ViewModelProvider, factory()

## Knowledge Gaps
- **351 isolated node(s):** `PreToolUse`, `PreToolUse`, `$schema`, `plugin`, `LauncherPrefsStore` (+346 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **13 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `HqApplication` connect `Main Activity Key Handling` to `System Monitoring`, `Community 68`, `Community 37`, `Community 52`?**
  _High betweenness centrality (0.131) - this node is a cross-community bridge._
- **Why does `PersistentChannelRepository` connect `Data Models & Installed Apps` to `Main Activity Key Handling`, `Tools Screen`?**
  _High betweenness centrality (0.122) - this node is a cross-community bridge._
- **Why does `GlassPanel()` connect `App Composition Root` to `IPTV Channels & EPG`, `Community 37`, `Community 77`, `Community 51`, `Community 52`, `Community 23`, `Community 59`, `Community 28`, `Community 62`?**
  _High betweenness centrality (0.082) - this node is a cross-community bridge._
- **Are the 6 inferred relationships involving `PersistentChannelRepository` (e.g. with `.`empty playlist leaves channels empty`()` and `.`loadM3u replaces lineup and persists url`()`) actually correct?**
  _`PersistentChannelRepository` has 6 INFERRED edges - model-reasoned connections that need verification._
- **Are the 13 inferred relationships involving `HomeScreen()` (e.g. with `.onCreate()` and `AmbientScreen()`) actually correct?**
  _`HomeScreen()` has 13 INFERRED edges - model-reasoned connections that need verification._
- **What connects `PreToolUse`, `PreToolUse`, `$schema` to the rest of the system?**
  _356 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `IPTV Channels & EPG` be split into smaller, more focused modules?**
  _Cohesion score 0.06693877551020408 - nodes in this community are weakly interconnected._