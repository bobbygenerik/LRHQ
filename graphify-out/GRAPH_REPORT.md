# Graph Report - .  (2026-06-12)

## Corpus Check
- Corpus is ~9,003 words - fits in a single context window. You may not need a graph.

## Summary
- 263 nodes · 428 edges · 17 communities (15 shown, 2 thin omitted)
- Extraction: 89% EXTRACTED · 11% INFERRED · 0% AMBIGUOUS · INFERRED: 47 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 18 edges
2. `widget()` - 14 edges
3. `GlassPanel()` - 14 edges
4. `StatBar()` - 14 edges
5. `DemoChannelRepository` - 13 edges
6. `SystemMonitor` - 13 edges
7. `HqApplication` - 12 edges
8. `SpatialNavController` - 12 edges
9. `ChannelRepository` - 12 edges
10. `FocusableGlassCard()` - 12 edges

## Surprising Connections (you probably didn't know these)
- `Demo Repository Pattern` --rationale_for--> `DemoAmbientInfoRepository`  [INFERRED]
  /home/bobbygenerik/repos/LRHQ/README.md → core/data/src/main/kotlin/com/livingroomhq/core/data/repo/AmbientInfoRepository.kt
- `Demo Repository Pattern` --rationale_for--> `DemoMediaRepository`  [INFERRED]
  /home/bobbygenerik/repos/LRHQ/README.md → core/data/src/main/kotlin/com/livingroomhq/core/data/repo/MediaRepository.kt
- `OLED-Dark Glassmorphism Design System` --conceptually_related_to--> `FocusableGlassCard()`  [INFERRED]
  /home/bobbygenerik/repos/LRHQ/README.md → core/ui/src/main/kotlin/com/livingroomhq/core/ui/components/FocusableGlassCard.kt
- `Demo Repository Pattern` --rationale_for--> `DemoChannelRepository`  [INFERRED]
  /home/bobbygenerik/repos/LRHQ/README.md → core/data/src/main/kotlin/com/livingroomhq/core/data/repo/ChannelRepository.kt
- `Command Center Dashboard` --references--> `SystemMonitor`  [EXTRACTED]
  /home/bobbygenerik/repos/LRHQ/README.md → core/data/src/main/kotlin/com/livingroomhq/core/data/repo/SystemMonitor.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Spatial zone navigation system** — livingroomhq_mainactivity_mainactivity, navigation_spatialnavcontroller_spatialnavcontroller, navigation_spatialnavhost_spatialnavhost, navigation_zone_zone, navigation_zone_zoneindirection [EXTRACTED 1.00]
- **Always-on live preview flow** — player_livepreview_livepreview, screens_homescreen_homescreen, screens_livescreen_livescreen [EXTRACTED 1.00]
- **Widget card pipeline (repos -> WidgetState -> WidgetCard in zones)** — widgets_builtinwidgets_registerbuiltinwidgets, components_widgetcard_widgetcard, screens_homescreen_homescreen, screens_toolsscreen_toolsscreen, screens_commandcenterscreen_commandcenterscreen [EXTRACTED 1.00]
- **OLED-Dark Glassmorphism Design System Components** — components_glasspanel_glasspanel, components_focusableglasscard_focusableglasscard, components_statbar_statbar, theme_theme_hqcolors, theme_theme_hqtype [INFERRED 0.95]
- **Widget Plugin Contract and Registry** — widget_widgetplugin_widgetplugin, widget_widgetplugin_widgetstate, widget_widgetplugin_widgetstat, widget_widgetregistry_widgetregistry [EXTRACTED 1.00]
- **Demo Repository Implementations Behind Stable Interfaces** — repo_ambientinforepository_demoambientinforepository, repo_channelrepository_demochannelrepository, repo_mediarepository_demomediarepository, readme_demo_repository_pattern [INFERRED 0.95]

## Communities (17 total, 2 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.08
Nodes (33): HqApplication, List, SpatialNavController, SystemStats, Boolean, HqApplication, List, MediaItem (+25 more)

### Community 1 - "Community 1"
Cohesion: 0.08
Nodes (24): Boolean, Int, SpatialNavController, Boolean, Direction, Long, Zone, Modifier (+16 more)

### Community 2 - "Community 2"
Cohesion: 0.09
Nodes (22): Modifier, WidgetPlugin, WidgetState, Boolean, Channel, Modifier, HqApplication, SpatialNavController (+14 more)

### Community 3 - "Community 3"
Cohesion: 0.12
Nodes (19): Float, Long, Flow, List, StateFlow, DownloadJob, Channel, MediaItem (+11 more)

### Community 4 - "Community 4"
Cohesion: 0.18
Nodes (10): Channel, List, Pair, StateFlow, String, Map, Program, Demo Repository Pattern (+2 more)

### Community 5 - "Community 5"
Cohesion: 0.12
Nodes (16): Composable, Flow, Set, String, Unit, List, StateFlow, String (+8 more)

### Community 6 - "Community 6"
Cohesion: 0.17
Nodes (9): ActivityManager, Boolean, Float, Flow, Long, Pair, SystemStats, Command Center Dashboard (+1 more)

### Community 7 - "Community 7"
Cohesion: 0.25
Nodes (19): Flow, HqApplication, Set, String, WidgetPlugin, WidgetRegistry, WidgetState, WidgetZone (+11 more)

### Community 8 - "Community 8"
Cohesion: 0.13
Nodes (16): AmbientInfoRepository, WidgetRegistry, HqApplication, SpatialNavController, String, Application, ChannelRepository, InstalledAppsRepository (+8 more)

### Community 9 - "Community 9"
Cohesion: 0.32
Nodes (7): Int, List, MediaItem, StateFlow, MediaType, DemoMediaRepository, MediaRepository

### Community 10 - "Community 10"
Cohesion: 0.25
Nodes (5): List, String, LaunchableApp, LaunchableApp, InstalledAppsRepository

## Knowledge Gaps
- **84 isolated node(s):** `ChannelRepository`, `MediaRepository`, `AmbientInfoRepository`, `SystemMonitor`, `InstalledAppsRepository` (+79 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **2 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `StatBar()` connect `Community 0` to `Community 1`, `Community 2`, `Community 7`?**
  _High betweenness centrality (0.438) - this node is a cross-community bridge._
- **Why does `WidgetStat` connect `Community 7` to `Community 0`, `Community 3`, `Community 5`?**
  _High betweenness centrality (0.321) - this node is a cross-community bridge._
- **Why does `DownloadJob` connect `Community 0` to `Community 3`?**
  _High betweenness centrality (0.235) - this node is a cross-community bridge._
- **Are the 6 inferred relationships involving `GlassPanel()` (e.g. with `Color` and `NetworkPanel()`) actually correct?**
  _`GlassPanel()` has 6 INFERRED edges - model-reasoned connections that need verification._
- **Are the 6 inferred relationships involving `StatBar()` (e.g. with `DownloadJob` and `WidgetStat`) actually correct?**
  _`StatBar()` has 6 INFERRED edges - model-reasoned connections that need verification._
- **What connects `ChannelRepository`, `MediaRepository`, `AmbientInfoRepository` to the rest of the system?**
  _87 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.07557354925775979 - nodes in this community are weakly interconnected._