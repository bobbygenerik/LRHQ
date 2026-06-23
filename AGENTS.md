## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, invoke the `skill` tool with `skill: "graphify"` before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Device installs (hard rule)
- **Never `adb uninstall`** or reinstall in a way that wipes app data unless the user explicitly asks.
- Prefer `adb install -r` (replace) so DataStore, Room DB, and caches survive.
- If install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and tell the user — do not uninstall to work around it. Options: sign with the same key, pull a backup first (`adb exec-out run-as com.livingroomhq …` on debug builds), or get explicit approval.
- IPTV defaults live in `DataStorePrefsStore` (`playlist_url`, `epg_url`); on fresh data loss, the app auto-reloads them on launch via `PersistentChannelRepository.restore()`.

## Conversation & Actions
- Do not run terminal commands or write file changes without first answering the user's messages and obtaining confirmation, unless explicitly directed to do so.
