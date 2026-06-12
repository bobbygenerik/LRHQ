# Launcher Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make LivingRoom HQ a complete daily-driver launcher: persisted favorites/recents/playlist, real M3U playlist loading, an in-app "set as default home" prompt, visible feedback when an app fails to launch, and Watch Next row publishing.

**Architecture:** A `LauncherPrefsStore` interface (DataStore-backed in production, in-memory in tests) becomes the persistence seam. `PersistentChannelRepository` replaces `DemoChannelRepository` in the composition root, restoring the saved M3U playlist at startup and falling back to the bundled demo lineup. Error feedback flows through a tiny `UiMessages` bus rendered as a glass toast overlay above the zone host. ROLE_HOME and Watch Next are thin Android-API glue around pure, unit-tested decision/mapping functions.

**Tech Stack:** Kotlin, Jetpack Compose for TV, AndroidX DataStore (Preferences), AndroidX RoleManager, androidx.tvprovider, JUnit 4 + kotlinx-coroutines-test (JVM unit tests, no Robolectric).

**Order matters:** Task 1 (test infra) and Tasks 2–6 (persistence) come first — Task 8 (ROLE_HOME prompt) stores its "dismissed" flag in the same prefs store. Tasks 7, 8, 9–10 are then independent of each other.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `gradle/libs.versions.toml` (modify) | Add datastore, tvprovider, junit, coroutines-test |
| `core/data/build.gradle.kts` (modify) | datastore dep + test deps |
| `core/data/src/main/kotlin/com/livingroomhq/core/data/iptv/M3uParser.kt` (create) | Pure M3U → `List<Channel>` parser |
| `core/data/src/main/kotlin/com/livingroomhq/core/data/persist/LauncherPrefsStore.kt` (create) | Persistence interface + `InMemoryPrefsStore` |
| `core/data/src/main/kotlin/com/livingroomhq/core/data/persist/DataStorePrefsStore.kt` (create) | Production DataStore implementation |
| `core/data/src/main/kotlin/com/livingroomhq/core/data/repo/DemoLineup.kt` (create) | Demo channels/EPG extracted from `DemoChannelRepository` |
| `core/data/src/main/kotlin/com/livingroomhq/core/data/repo/ChannelRepository.kt` (modify) | `DemoChannelRepository` delegates to `DemoLineup` |
| `core/data/src/main/kotlin/com/livingroomhq/core/data/repo/PersistentChannelRepository.kt` (create) | Favorites/recents/playlist persistence + M3U loading |
| `core/data/src/main/kotlin/com/livingroomhq/core/data/repo/InstalledAppsRepository.kt` (modify) | `launch()` returns Boolean, reports failures via callback |
| `core/data/src/test/kotlin/.../M3uParserTest.kt` (create) | Parser tests |
| `core/data/src/test/kotlin/.../InMemoryPrefsStoreTest.kt` (create) | Prefs store contract test |
| `core/data/src/test/kotlin/.../PersistentChannelRepositoryTest.kt` (create) | Repository behavior tests |
| `app/src/main/kotlin/com/livingroomhq/HqApplication.kt` (modify) | appScope, prefs, persistent repo, error wiring |
| `app/src/main/kotlin/com/livingroomhq/ui/UiMessages.kt` (create) | Transient message bus + `MessageOverlay` composable |
| `app/src/main/kotlin/com/livingroomhq/MainActivity.kt` (modify) | Overlay above SpatialNavHost; Watch Next sync |
| `app/src/main/kotlin/com/livingroomhq/home/DefaultHomeHelper.kt` (create) | ROLE_HOME detection/request + `shouldPromptForDefault` |
| `app/src/main/kotlin/com/livingroomhq/home/DefaultHomeBanner.kt` (create) | Glass banner with Set-default / Dismiss actions |
| `app/src/main/kotlin/com/livingroomhq/screens/HomeScreen.kt` (modify) | Mount banner in side column |
| `app/src/main/kotlin/com/livingroomhq/tvintegration/WatchNext.kt` (create) | `WatchNextEntry` mapping + `WatchNextPublisher` |
| `app/src/test/kotlin/.../DefaultHomeHelperTest.kt` (create) | Prompt decision test |
| `app/src/test/kotlin/.../WatchNextTest.kt` (create) | Mapping tests |
| `app/build.gradle.kts` (modify) | tvprovider dep + test deps |

---

### Task 1: JVM test infrastructure

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/data/build.gradle.kts`
- Test: `core/data/src/test/kotlin/com/livingroomhq/core/data/SanityTest.kt`

- [ ] **Step 1: Add versions and libraries to the catalog**

In `gradle/libs.versions.toml`, add under `[versions]`:

```toml
datastore = "1.1.1"
tvprovider = "1.0.0"
junit = "4.13.2"
```

Add under `[libraries]`:

```toml
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-tvprovider = { group = "androidx.tvprovider", name = "tvprovider", version.ref = "tvprovider" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
```

- [ ] **Step 2: Add test dependencies to core:data**

In `core/data/build.gradle.kts`, append to the `dependencies` block:

```kotlin
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 3: Write a sanity test**

Create `core/data/src/test/kotlin/com/livingroomhq/core/data/SanityTest.kt`:

```kotlin
package com.livingroomhq.core.data

import org.junit.Assert.assertTrue
import org.junit.Test

class SanityTest {
    @Test
    fun `test harness runs`() {
        assertTrue(true)
    }
}
```

- [ ] **Step 4: Run it**

Run: `./gradlew :core:data:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, 1 test passed.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml core/data/build.gradle.kts core/data/src/test
git commit -m "test: add JVM unit test infrastructure to core:data"
```

---

### Task 2: M3U playlist parser

**Files:**
- Create: `core/data/src/main/kotlin/com/livingroomhq/core/data/iptv/M3uParser.kt`
- Test: `core/data/src/test/kotlin/com/livingroomhq/core/data/iptv/M3uParserTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.livingroomhq.core.data.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    private val playlist = """
        #EXTM3U
        #EXTINF:-1 tvg-id="atlas.news" tvg-logo="http://x/logo1.png" group-title="News",Atlas News
        http://stream.example/1.m3u8
        #EXTINF:-1,Bare Channel
        http://stream.example/2.m3u8
    """.trimIndent()

    @Test
    fun `parses attributes, name and url`() {
        val channels = M3uParser.parse(playlist)
        assertEquals(2, channels.size)
        val first = channels[0]
        assertEquals("atlas.news", first.id)
        assertEquals("Atlas News", first.name)
        assertEquals("News", first.group)
        assertEquals("http://x/logo1.png", first.logoUrl)
        assertEquals("http://stream.example/1.m3u8", first.streamUrl)
        assertEquals(1, first.number)
    }

    @Test
    fun `missing attributes fall back to url id and Other group`() {
        val second = M3uParser.parse(playlist)[1]
        assertEquals("http://stream.example/2.m3u8", second.id)
        assertEquals("Bare Channel", second.name)
        assertEquals("Other", second.group)
        assertEquals(null, second.logoUrl)
        assertEquals(2, second.number)
    }

    @Test
    fun `skips EXTINF without a following url and ignores junk lines`() {
        val broken = "#EXTM3U\n#EXTINF:-1,Orphan\n#EXTINF:-1,Real\nhttp://s/1.m3u8\n# comment"
        val channels = M3uParser.parse(broken)
        assertEquals(1, channels.size)
        assertEquals("Real", channels[0].name)
    }

    @Test
    fun `empty input parses to empty list`() {
        assertTrue(M3uParser.parse("").isEmpty())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :core:data:testDebugUnitTest --console=plain`
Expected: FAIL — `Unresolved reference: M3uParser`.

- [ ] **Step 3: Implement the parser**

Create `core/data/src/main/kotlin/com/livingroomhq/core/data/iptv/M3uParser.kt`:

```kotlin
package com.livingroomhq.core.data.iptv

import com.livingroomhq.core.data.model.Channel

/**
 * Minimal M3U/M3U8 IPTV playlist parser. Channel ids prefer `tvg-id` and fall
 * back to the stream URL so favorites/recents stay stable across reloads.
 */
object M3uParser {

    private val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(text: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var pendingExtinf: String? = null

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> pendingExtinf = line
                line.isEmpty() || line.startsWith("#") -> Unit // headers, comments
                else -> {
                    val extinf = pendingExtinf
                    pendingExtinf = null
                    if (extinf != null) {
                        val attrs = attrRegex.findAll(extinf)
                            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                        val name = extinf.substringAfterLast(',').trim().ifEmpty { line }
                        channels += Channel(
                            id = attrs["tvg-id"]?.takeIf { it.isNotBlank() } ?: line,
                            number = channels.size + 1,
                            name = name,
                            group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: "Other",
                            streamUrl = line,
                            logoUrl = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        )
                    }
                }
            }
        }
        return channels
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :core:data:testDebugUnitTest --console=plain`
Expected: PASS (5 tests total including sanity).

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/kotlin/com/livingroomhq/core/data/iptv core/data/src/test/kotlin/com/livingroomhq/core/data/iptv
git commit -m "feat: M3U playlist parser with stable channel ids"
```

---

### Task 3: LauncherPrefsStore interface + in-memory implementation

**Files:**
- Create: `core/data/src/main/kotlin/com/livingroomhq/core/data/persist/LauncherPrefsStore.kt`
- Test: `core/data/src/test/kotlin/com/livingroomhq/core/data/persist/InMemoryPrefsStoreTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.livingroomhq.core.data.persist

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryPrefsStoreTest {

    @Test
    fun `defaults are empty`() = runTest {
        val store = InMemoryPrefsStore()
        assertEquals(emptySet<String>(), store.favorites.first())
        assertEquals(emptyList<String>(), store.recents.first())
        assertNull(store.playlistUrl.first())
        assertEquals(false, store.defaultPromptDismissed.first())
    }

    @Test
    fun `writes are observable`() = runTest {
        val store = InMemoryPrefsStore()
        store.setFavorites(setOf("a", "b"))
        store.setRecents(listOf("b", "a"))
        store.setPlaylistUrl("http://x/playlist.m3u")
        store.setDefaultPromptDismissed(true)
        assertEquals(setOf("a", "b"), store.favorites.first())
        assertEquals(listOf("b", "a"), store.recents.first())
        assertEquals("http://x/playlist.m3u", store.playlistUrl.first())
        assertEquals(true, store.defaultPromptDismissed.first())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :core:data:testDebugUnitTest --console=plain`
Expected: FAIL — `Unresolved reference: InMemoryPrefsStore`.

- [ ] **Step 3: Implement interface + in-memory store**

Create `core/data/src/main/kotlin/com/livingroomhq/core/data/persist/LauncherPrefsStore.kt`:

```kotlin
package com.livingroomhq.core.data.persist

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Persistence seam for launcher state. Production uses DataStore
 * ([DataStorePrefsStore]); tests use [InMemoryPrefsStore].
 */
interface LauncherPrefsStore {
    val favorites: Flow<Set<String>>
    val recents: Flow<List<String>>
    val playlistUrl: Flow<String?>
    val defaultPromptDismissed: Flow<Boolean>

    suspend fun setFavorites(ids: Set<String>)
    suspend fun setRecents(ids: List<String>)
    suspend fun setPlaylistUrl(url: String?)
    suspend fun setDefaultPromptDismissed(dismissed: Boolean)
}

class InMemoryPrefsStore : LauncherPrefsStore {
    override val favorites = MutableStateFlow<Set<String>>(emptySet())
    override val recents = MutableStateFlow<List<String>>(emptyList())
    override val playlistUrl = MutableStateFlow<String?>(null)
    override val defaultPromptDismissed = MutableStateFlow(false)

    override suspend fun setFavorites(ids: Set<String>) { favorites.value = ids }
    override suspend fun setRecents(ids: List<String>) { recents.value = ids }
    override suspend fun setPlaylistUrl(url: String?) { playlistUrl.value = url }
    override suspend fun setDefaultPromptDismissed(dismissed: Boolean) { defaultPromptDismissed.value = dismissed }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :core:data:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/kotlin/com/livingroomhq/core/data/persist core/data/src/test/kotlin/com/livingroomhq/core/data/persist
git commit -m "feat: LauncherPrefsStore persistence seam with in-memory impl"
```

---

### Task 4: DataStore-backed implementation

**Files:**
- Modify: `core/data/build.gradle.kts`
- Create: `core/data/src/main/kotlin/com/livingroomhq/core/data/persist/DataStorePrefsStore.kt`

This is thin Android glue over the tested interface — no JVM test; the build is the check.

- [ ] **Step 1: Add the DataStore dependency**

In `core/data/build.gradle.kts` dependencies block, add:

```kotlin
    implementation(libs.androidx.datastore.preferences)
```

- [ ] **Step 2: Implement the store**

Create `core/data/src/main/kotlin/com/livingroomhq/core/data/persist/DataStorePrefsStore.kt`:

```kotlin
package com.livingroomhq.core.data.persist

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherDataStore by preferencesDataStore(name = "launcher_prefs")

/** DataStore-backed [LauncherPrefsStore]. Recents keep order via a joined string. */
class DataStorePrefsStore(context: Context) : LauncherPrefsStore {

    private val store = context.applicationContext.launcherDataStore

    private object Keys {
        val FAVORITES = stringSetPreferencesKey("favorite_channel_ids")
        val RECENTS = stringPreferencesKey("recent_channel_ids")
        val PLAYLIST_URL = stringPreferencesKey("playlist_url")
        val PROMPT_DISMISSED = booleanPreferencesKey("default_prompt_dismissed")
    }

    override val favorites: Flow<Set<String>> =
        store.data.map { it[Keys.FAVORITES] ?: emptySet() }

    override val recents: Flow<List<String>> =
        store.data.map { prefs ->
            prefs[Keys.RECENTS]?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
        }

    override val playlistUrl: Flow<String?> =
        store.data.map { it[Keys.PLAYLIST_URL] }

    override val defaultPromptDismissed: Flow<Boolean> =
        store.data.map { it[Keys.PROMPT_DISMISSED] ?: false }

    override suspend fun setFavorites(ids: Set<String>) {
        store.edit { it[Keys.FAVORITES] = ids }
    }

    override suspend fun setRecents(ids: List<String>) {
        store.edit { it[Keys.RECENTS] = ids.joinToString("\n") }
    }

    override suspend fun setPlaylistUrl(url: String?) {
        store.edit { prefs ->
            if (url == null) prefs.remove(Keys.PLAYLIST_URL) else prefs[Keys.PLAYLIST_URL] = url
        }
    }

    override suspend fun setDefaultPromptDismissed(dismissed: Boolean) {
        store.edit { it[Keys.PROMPT_DISMISSED] = dismissed }
    }
}
```

- [ ] **Step 3: Build check**

Run: `./gradlew :core:data:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add core/data/build.gradle.kts core/data/src/main/kotlin/com/livingroomhq/core/data/persist/DataStorePrefsStore.kt
git commit -m "feat: DataStore-backed launcher preferences"
```

---

### Task 5: Extract DemoLineup, add PersistentChannelRepository

**Files:**
- Create: `core/data/src/main/kotlin/com/livingroomhq/core/data/repo/DemoLineup.kt`
- Modify: `core/data/src/main/kotlin/com/livingroomhq/core/data/repo/ChannelRepository.kt`
- Create: `core/data/src/main/kotlin/com/livingroomhq/core/data/repo/PersistentChannelRepository.kt`
- Test: `core/data/src/test/kotlin/com/livingroomhq/core/data/repo/PersistentChannelRepositoryTest.kt`

- [ ] **Step 1: Extract demo data into DemoLineup**

Create `core/data/src/main/kotlin/com/livingroomhq/core/data/repo/DemoLineup.kt` by moving the two private functions from `DemoChannelRepository` verbatim into an object (so both repositories share them):

```kotlin
package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program

/** Bundled demo lineup + EPG so the launcher is navigable with no playlist configured. */
object DemoLineup {

    fun channels(): List<Channel> {
        val groups = listOf("News", "Sports", "Movies", "Kids", "Music")
        val names = listOf(
            "Atlas News", "World 24", "Court Center", "Arena One", "Prime Field",
            "Cinema Gold", "Noir Classics", "Tiny Planet", "Cartoon Bay", "Wave FM",
            "Symphony HD", "Docu Sphere", "Metro Live", "Night Owl", "Galaxy Sports",
        )
        return names.mapIndexed { i, name ->
            Channel(
                id = "ch-$i",
                number = i + 1,
                name = name,
                group = groups[i % groups.size],
                streamUrl = "https://demo.livingroomhq.local/streams/${i + 1}.m3u8",
                isFavorite = i % 4 == 0,
            )
        }
    }

    fun epg(): Map<String, List<Program>> {
        val now = System.currentTimeMillis()
        val half = 30 * 60 * 1000L
        val shows = listOf(
            "Evening Report" to "The day's stories with in-depth analysis.",
            "Championship Live" to "Live coverage from the arena floor.",
            "Golden Age Cinema" to "A restored classic from the studio vaults.",
            "Deep Blue" to "Documentary diving beneath the polar ice.",
            "Late Session" to "Unplugged performances after dark.",
        )
        return channels().associate { channel ->
            val (title, desc) = shows[channel.number % shows.size]
            channel.id to listOf(
                Program(channel.id, title, desc, now - half, now + half),
                Program(channel.id, "$title: Next Hour", desc, now + half, now + 3 * half),
            )
        }
    }
}
```

In `ChannelRepository.kt`, delete the private `demoLineup()` / `demoEpg()` functions from `DemoChannelRepository` and replace their two call sites:
- `MutableStateFlow(demoLineup())` → `MutableStateFlow(DemoLineup.channels())`
- `private val epg = demoEpg()` → `private val epg = DemoLineup.epg()`

- [ ] **Step 2: Build check (refactor must not break anything)**

Run: `./gradlew :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Write the failing repository tests**

Create `core/data/src/test/kotlin/com/livingroomhq/core/data/repo/PersistentChannelRepositoryTest.kt`:

```kotlin
package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.persist.InMemoryPrefsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentChannelRepositoryTest {

    private val playlist = """
        #EXTM3U
        #EXTINF:-1 tvg-id="one" group-title="News",One
        http://s/1.m3u8
        #EXTINF:-1 tvg-id="two" group-title="Sports",Two
        http://s/2.m3u8
    """.trimIndent()

    @Test
    fun `starts with demo lineup`() = runTest {
        val repo = PersistentChannelRepository(InMemoryPrefsStore(), backgroundScope) { playlist }
        advanceUntilIdle()
        assertEquals(DemoLineup.channels().size, repo.channels.first().size)
    }

    @Test
    fun `toggleFavorite persists and reflects in channels`() = runTest {
        val prefs = InMemoryPrefsStore()
        val repo = PersistentChannelRepository(prefs, backgroundScope) { playlist }
        advanceUntilIdle()
        val target = repo.channels.first().first { !it.isFavorite }

        repo.toggleFavorite(target.id)
        advanceUntilIdle()
        assertTrue(target.id in prefs.favorites.first())
        assertTrue(repo.channels.first().first { it.id == target.id }.isFavorite)

        repo.toggleFavorite(target.id)
        advanceUntilIdle()
        assertFalse(target.id in prefs.favorites.first())
    }

    @Test
    fun `markWatched orders recents newest-first, dedupes, caps at 8`() = runTest {
        val prefs = InMemoryPrefsStore()
        val repo = PersistentChannelRepository(prefs, backgroundScope) { playlist }
        advanceUntilIdle()
        val ids = repo.channels.first().take(10).map { it.id }

        ids.forEach { repo.markWatched(it); advanceUntilIdle() }
        repo.markWatched(ids[5]); advanceUntilIdle()

        val recents = repo.recents.first().map { it.id }
        assertEquals(8, recents.size)
        assertEquals(ids[5], recents.first())
        assertEquals(1, recents.count { it == ids[5] })
    }

    @Test
    fun `loadM3u replaces lineup and persists url`() = runTest {
        val prefs = InMemoryPrefsStore()
        val repo = PersistentChannelRepository(prefs, backgroundScope) { playlist }
        advanceUntilIdle()

        repo.loadM3u("http://x/list.m3u")
        advanceUntilIdle()

        assertEquals(listOf("one", "two"), repo.channels.first().map { it.id })
        assertEquals("http://x/list.m3u", prefs.playlistUrl.first())
    }

    @Test
    fun `restore reloads persisted playlist at startup`() = runTest {
        val prefs = InMemoryPrefsStore().apply { setPlaylistUrl("http://x/list.m3u") }
        val repo = PersistentChannelRepository(prefs, backgroundScope) { playlist }
        repo.restore()
        advanceUntilIdle()
        assertEquals(listOf("one", "two"), repo.channels.first().map { it.id })
    }

    @Test
    fun `empty playlist keeps current lineup`() = runTest {
        val prefs = InMemoryPrefsStore()
        val repo = PersistentChannelRepository(prefs, backgroundScope) { "" }
        advanceUntilIdle()
        repo.loadM3u("http://x/empty.m3u")
        advanceUntilIdle()
        assertEquals(DemoLineup.channels().size, repo.channels.first().size)
        assertEquals(null, prefs.playlistUrl.first())
    }
}
```

- [ ] **Step 4: Run to verify failure**

Run: `./gradlew :core:data:testDebugUnitTest --console=plain`
Expected: FAIL — `Unresolved reference: PersistentChannelRepository`.

- [ ] **Step 5: Implement the repository**

Create `core/data/src/main/kotlin/com/livingroomhq/core/data/repo/PersistentChannelRepository.kt`:

```kotlin
package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.iptv.M3uParser
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.livingroomhq.core.data.persist.LauncherPrefsStore
import java.net.URL

/**
 * [ChannelRepository] with persisted favorites, recents and playlist URL.
 * Starts on the bundled [DemoLineup]; [restore] swaps in the saved M3U
 * playlist at startup, and [loadM3u] configures a new one. EPG data is only
 * available for the demo lineup until an XMLTV source is added.
 */
class PersistentChannelRepository(
    private val prefs: LauncherPrefsStore,
    private val scope: CoroutineScope,
    private val fetchPlaylist: suspend (String) -> String = ::httpGet,
) : ChannelRepository {

    private val lineup = MutableStateFlow(DemoLineup.channels())
    private val demoEpg = DemoLineup.epg()

    override val channels: StateFlow<List<Channel>> =
        combine(lineup, prefs.favorites) { list, favs ->
            list.map { it.copy(isFavorite = it.id in favs) }
        }.stateIn(scope, SharingStarted.Eagerly, lineup.value)

    override val recents: StateFlow<List<Channel>> =
        combine(channels, prefs.recents) { list, ids ->
            ids.mapNotNull { id -> list.firstOrNull { it.id == id } }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun groups(): List<String> =
        channels.value.map { it.group }.distinct()

    override fun epgNowNext(channelId: String): Pair<Program?, Program?> {
        val now = System.currentTimeMillis()
        val programs = demoEpg[channelId].orEmpty().sortedBy { it.startMillis }
        val current = programs.firstOrNull { now in it.startMillis until it.endMillis }
        val next = programs.firstOrNull { it.startMillis >= (current?.endMillis ?: now) }
        return current to next
    }

    override fun markWatched(channelId: String) {
        scope.launch {
            val current = prefs.recents.first()
            prefs.setRecents((listOf(channelId) + current.filterNot { it == channelId }).take(8))
        }
    }

    override fun toggleFavorite(channelId: String) {
        scope.launch {
            val favs = prefs.favorites.first()
            prefs.setFavorites(if (channelId in favs) favs - channelId else favs + channelId)
        }
    }

    override suspend fun loadM3u(playlistUrl: String) {
        val parsed = M3uParser.parse(fetchPlaylist(playlistUrl))
        if (parsed.isNotEmpty()) {
            lineup.value = parsed
            prefs.setPlaylistUrl(playlistUrl)
        }
    }

    /** Re-applies the persisted playlist; network errors keep the demo lineup. */
    fun restore() {
        scope.launch {
            prefs.playlistUrl.first()?.let { url ->
                runCatching { loadM3u(url) }
            }
        }
    }
}

private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
    URL(url).readText()
}
```

- [ ] **Step 6: Run to verify pass**

Run: `./gradlew :core:data:testDebugUnitTest --console=plain`
Expected: PASS — all tests green.

- [ ] **Step 7: Commit**

```bash
git add core/data/src
git commit -m "feat: persistent channel repository with M3U loading and demo fallback"
```

---

### Task 6: Wire persistence into the composition root

**Files:**
- Modify: `app/src/main/kotlin/com/livingroomhq/HqApplication.kt`

- [ ] **Step 1: Replace the demo repository**

In `HqApplication.kt`, replace the imports of `DemoChannelRepository`/`ChannelRepository` block and the `channels` property, and add `appScope` + `prefs`:

```kotlin
package com.livingroomhq

import android.app.Application
import com.livingroomhq.core.data.persist.DataStorePrefsStore
import com.livingroomhq.core.data.persist.LauncherPrefsStore
import com.livingroomhq.core.data.repo.AmbientInfoRepository
import com.livingroomhq.core.data.repo.ChannelRepository
import com.livingroomhq.core.data.repo.DemoAmbientInfoRepository
import com.livingroomhq.core.data.repo.DemoMediaRepository
import com.livingroomhq.core.data.repo.InstalledAppsRepository
import com.livingroomhq.core.data.repo.MediaRepository
import com.livingroomhq.core.data.repo.PersistentChannelRepository
import com.livingroomhq.core.data.repo.SystemMonitor
import com.livingroomhq.core.widget.WidgetRegistry
import com.livingroomhq.widgets.registerBuiltInWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Composition root. The launcher must cold-start fast, so wiring is plain
 * lazy properties rather than a DI framework — nothing is constructed until
 * the first screen asks for it.
 */
class HqApplication : Application() {

    /** App-lifetime scope for repository persistence and background sync. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val prefs: LauncherPrefsStore by lazy { DataStorePrefsStore(this) }
    val channels: ChannelRepository by lazy {
        PersistentChannelRepository(prefs, appScope).also { it.restore() }
    }
    val media: MediaRepository by lazy { DemoMediaRepository() }
    val ambientInfo: AmbientInfoRepository by lazy { DemoAmbientInfoRepository() }
    val systemMonitor: SystemMonitor by lazy { SystemMonitor(this) }
    val installedApps: InstalledAppsRepository by lazy { InstalledAppsRepository(this) }
    val widgets: WidgetRegistry by lazy {
        WidgetRegistry().also { registerBuiltInWidgets(it, this) }
    }
}
```

- [ ] **Step 2: Build check**

Run: `./gradlew :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/livingroomhq/HqApplication.kt
git commit -m "feat: wire persistent channel repository into composition root"
```

---

### Task 7: App launch error handling

**Files:**
- Modify: `core/data/src/main/kotlin/com/livingroomhq/core/data/repo/InstalledAppsRepository.kt`
- Create: `app/src/main/kotlin/com/livingroomhq/ui/UiMessages.kt`
- Modify: `app/src/main/kotlin/com/livingroomhq/HqApplication.kt`
- Modify: `app/src/main/kotlin/com/livingroomhq/MainActivity.kt`

- [ ] **Step 1: Make launch failures observable**

In `InstalledAppsRepository.kt`, change the constructor and `launch`:

```kotlin
class InstalledAppsRepository(
    private val context: Context,
    private val onLaunchError: (packageName: String) -> Unit = {},
) {
```

```kotlin
    /** Launches the app, reporting failure (uninstalled race, no intent, blocked). */
    fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            onLaunchError(packageName)
            return false
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            // ActivityNotFoundException or SecurityException from a stale entry.
            onLaunchError(packageName)
            false
        }
    }
```

Call sites pass `app.installedApps::launch` as `(String) -> Unit`; a `Boolean` return is still assignable to that shape only via adaptation — Kotlin does NOT auto-adapt method references on return type. Update the three call sites (`HomeScreen.kt`, `ToolsScreen.kt`, `CommandCenterScreen.kt`) from `onLaunch = app.installedApps::launch` to:

```kotlin
onLaunch = { pkg -> app.installedApps.launch(pkg) }
```

- [ ] **Step 2: Create the message bus + overlay**

Create `app/src/main/kotlin/com/livingroomhq/ui/UiMessages.kt`:

```kotlin
package com.livingroomhq.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.theme.HqType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Transient user-facing messages (launch failures, playlist errors). */
object UiMessages {
    private val _current = MutableStateFlow<String?>(null)
    val current: StateFlow<String?> = _current.asStateFlow()

    fun post(message: String) { _current.value = message }
    fun clear() { _current.value = null }
}

/** Glass toast pinned bottom-center; auto-dismisses after three seconds. */
@Composable
fun MessageOverlay(modifier: Modifier = Modifier) {
    val message by UiMessages.current.collectAsState()

    LaunchedEffect(message) {
        if (message != null) {
            delay(3_000)
            UiMessages.clear()
        }
    }

    Box(modifier.fillMaxSize().padding(bottom = 48.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
        ) {
            GlassPanel {
                Text(message.orEmpty(), style = HqType.Body)
            }
        }
    }
}
```

- [ ] **Step 3: Wire the error callback and overlay**

In `HqApplication.kt`, change the `installedApps` property:

```kotlin
    val installedApps: InstalledAppsRepository by lazy {
        InstalledAppsRepository(this) { UiMessages.post("Couldn't open that app") }
    }
```

Add import `com.livingroomhq.ui.UiMessages`.

In `MainActivity.kt`, wrap the host so the overlay floats above every zone — replace the `SpatialNavHost(...)` call inside `setContent` with:

```kotlin
            Box(Modifier.fillMaxSize()) {
                SpatialNavHost(zone = controller.zone, modifier = Modifier.fillMaxSize()) { zone ->
                    when (zone) {
                        Zone.HOME -> HomeScreen(app, controller)
                        Zone.LIVE -> LiveScreen(app, controller)
                        Zone.MEDIA -> MediaScreen(app, controller)
                        Zone.TOOLS -> ToolsScreen(app, controller)
                        Zone.AMBIENT -> AmbientScreen(app, controller)
                        Zone.COMMAND_CENTER -> CommandCenterScreen(app, controller)
                    }
                }
                MessageOverlay()
            }
```

Add imports: `androidx.compose.foundation.layout.Box`, `com.livingroomhq.ui.MessageOverlay`.

- [ ] **Step 4: Build check**

Run: `./gradlew :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/kotlin/com/livingroomhq/core/data/repo/InstalledAppsRepository.kt app/src
git commit -m "feat: surface app launch failures as glass toast overlay"
```

---

### Task 8: ROLE_HOME "set as default" prompt

**Files:**
- Create: `app/src/main/kotlin/com/livingroomhq/home/DefaultHomeHelper.kt`
- Create: `app/src/main/kotlin/com/livingroomhq/home/DefaultHomeBanner.kt`
- Modify: `app/src/main/kotlin/com/livingroomhq/screens/HomeScreen.kt`
- Modify: `app/build.gradle.kts` (test deps)
- Test: `app/src/test/kotlin/com/livingroomhq/home/DefaultHomeHelperTest.kt`

- [ ] **Step 1: Add JVM test deps to :app**

In `app/build.gradle.kts` dependencies block, add:

```kotlin
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 2: Write the failing decision test**

Create `app/src/test/kotlin/com/livingroomhq/home/DefaultHomeHelperTest.kt`:

```kotlin
package com.livingroomhq.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultHomeHelperTest {
    @Test
    fun `prompts only when not default and not dismissed`() {
        assertTrue(shouldPromptForDefault(isDefault = false, dismissed = false))
        assertFalse(shouldPromptForDefault(isDefault = true, dismissed = false))
        assertFalse(shouldPromptForDefault(isDefault = false, dismissed = true))
        assertFalse(shouldPromptForDefault(isDefault = true, dismissed = true))
    }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: FAIL — `Unresolved reference: shouldPromptForDefault`.

- [ ] **Step 4: Implement the helper**

Create `app/src/main/kotlin/com/livingroomhq/home/DefaultHomeHelper.kt`:

```kotlin
package com.livingroomhq.home

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Show the banner only while we are not the default home and the user hasn't dismissed it. */
fun shouldPromptForDefault(isDefault: Boolean, dismissed: Boolean): Boolean =
    !isDefault && !dismissed

object DefaultHomeHelper {

    fun isDefaultHome(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager
            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /**
     * Intent that lets the user make us the default home: the RoleManager
     * system dialog on API 29+, the home-settings screen otherwise. Null when
     * the role is unavailable or already held.
     */
    fun createRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
            if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return null
            if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return null
            return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }
}
```

- [ ] **Step 5: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 6: Implement the banner**

Create `app/src/main/kotlin/com/livingroomhq/home/DefaultHomeBanner.kt`:

```kotlin
package com.livingroomhq.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.livingroomhq.core.data.persist.LauncherPrefsStore
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.components.GlassPanel
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import kotlinx.coroutines.launch

/**
 * Glass banner asking the user to make LivingRoom HQ the default home app.
 * Disappears once the role is held or the user dismisses it (persisted).
 */
@Composable
fun DefaultHomeBanner(prefs: LauncherPrefsStore, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismissed by prefs.defaultPromptDismissed.collectAsState(initial = true)
    var isDefault by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { isDefault = DefaultHomeHelper.isDefaultHome(context) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        isDefault = DefaultHomeHelper.isDefaultHome(context)
    }

    if (!shouldPromptForDefault(isDefault, dismissed)) return

    GlassPanel(modifier = modifier.fillMaxWidth()) {
        Column {
            Text("MAKE THIS YOUR HOME", style = HqType.Label)
            Spacer(Modifier.height(6.dp))
            Text("Set LivingRoom HQ as the default launcher", style = HqType.Body)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusableGlassCard(
                    onClick = {
                        DefaultHomeHelper.createRequestIntent(context)?.let(launcher::launch)
                    },
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) { _ ->
                    Text("SET AS DEFAULT", style = HqType.Label.copy(color = HqColors.Accent))
                }
                FocusableGlassCard(
                    onClick = { scope.launch { prefs.setDefaultPromptDismissed(true) } },
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) { _ ->
                    Text("NOT NOW", style = HqType.Label)
                }
            }
        }
    }
}
```

- [ ] **Step 7: Mount the banner on HomeScreen**

In `HomeScreen.kt`, inside the side column (the `Column(Modifier.weight(0.38f), ...)`), add as the first child before the clock block:

```kotlin
            DefaultHomeBanner(prefs = app.prefs)
```

Add import `com.livingroomhq.home.DefaultHomeBanner`.

- [ ] **Step 8: Build + test check**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, tests pass.

- [ ] **Step 9: Commit**

```bash
git add app/build.gradle.kts app/src
git commit -m "feat: in-app ROLE_HOME default-launcher prompt with persisted dismissal"
```

---

### Task 9: Watch Next mapping (pure logic)

**Files:**
- Create: `app/src/main/kotlin/com/livingroomhq/tvintegration/WatchNext.kt`
- Test: `app/src/test/kotlin/com/livingroomhq/tvintegration/WatchNextTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/com/livingroomhq/tvintegration/WatchNextTest.kt`:

```kotlin
package com.livingroomhq.tvintegration

import com.livingroomhq.core.data.model.MediaItem
import com.livingroomhq.core.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchNextTest {

    private fun item(progress: Float, type: MediaType = MediaType.MOVIE) = MediaItem(
        id = "m1", title = "Signal Lost", type = type,
        description = "desc", runtimeMinutes = 100, watchProgress = progress,
    )

    @Test
    fun `in-progress item maps with duration and position`() {
        val entry = item(0.5f).toWatchNextEntry()!!
        assertEquals("m1", entry.id)
        assertEquals("Signal Lost", entry.title)
        assertEquals(100 * 60_000L, entry.durationMillis)
        assertEquals(50 * 60_000L, entry.lastPositionMillis)
    }

    @Test
    fun `unwatched and finished items are excluded`() {
        assertNull(item(0f).toWatchNextEntry())
        assertNull(item(0.96f).toWatchNextEntry())
    }

    @Test
    fun `music is excluded from watch next`() {
        assertNull(item(0.5f, MediaType.MUSIC).toWatchNextEntry())
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: FAIL — `Unresolved reference: toWatchNextEntry`.

- [ ] **Step 3: Implement the mapping**

Create `app/src/main/kotlin/com/livingroomhq/tvintegration/WatchNext.kt`:

```kotlin
package com.livingroomhq.tvintegration

import com.livingroomhq.core.data.model.MediaItem
import com.livingroomhq.core.data.model.MediaType

/** Provider-agnostic Watch Next row entry; pure so the mapping is unit-testable. */
data class WatchNextEntry(
    val id: String,
    val title: String,
    val description: String,
    val durationMillis: Long,
    val lastPositionMillis: Long,
    val isEpisode: Boolean,
)

/** Null when the item doesn't belong in Watch Next (music, unwatched, finished). */
fun MediaItem.toWatchNextEntry(): WatchNextEntry? {
    if (type == MediaType.MUSIC) return null
    if (watchProgress !in 0.01f..0.95f) return null
    val duration = runtimeMinutes * 60_000L
    return WatchNextEntry(
        id = id,
        title = title,
        description = description,
        durationMillis = duration,
        lastPositionMillis = (duration * watchProgress).toLong(),
        isEpisode = type == MediaType.SHOW,
    )
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/livingroomhq/tvintegration app/src/test/kotlin/com/livingroomhq/tvintegration
git commit -m "feat: watch-next entry mapping for continue-watching items"
```

---

### Task 10: Watch Next publisher + sync

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/kotlin/com/livingroomhq/tvintegration/WatchNext.kt`
- Modify: `app/src/main/kotlin/com/livingroomhq/HqApplication.kt`

- [ ] **Step 1: Add the tvprovider dependency**

In `app/build.gradle.kts` dependencies block, add:

```kotlin
    implementation(libs.androidx.tvprovider)
```

- [ ] **Step 2: Implement the publisher**

In `app/src/main/kotlin/com/livingroomhq/tvintegration/WatchNext.kt`, add these three imports to the import block at the top of the file:

```kotlin
import android.content.Context
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
```

Then append the class at the end of the file:

```kotlin
/**
 * Publishes continue-watching entries to the system Watch Next row, so this
 * launcher's library surfaces even when the stock Google TV launcher is
 * active. The provider is absent on some devices; every call is best-effort.
 */
class WatchNextPublisher(private val context: Context) {

    fun sync(entries: List<WatchNextEntry>) {
        runCatching {
            val resolver = context.contentResolver
            // Apps only see their own rows; null selection clears just ours.
            resolver.delete(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null)
            entries.forEach { entry ->
                val program = WatchNextProgram.Builder()
                    .setType(
                        if (entry.isEpisode) TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
                        else TvContractCompat.WatchNextPrograms.TYPE_MOVIE
                    )
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setInternalProviderId(entry.id)
                    .setTitle(entry.title)
                    .setDescription(entry.description)
                    .setDurationMillis(entry.durationMillis.toInt())
                    .setLastPlaybackPositionMillis(entry.lastPositionMillis.toInt())
                    .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                    .build()
                resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())
            }
        }
    }
}
```

- [ ] **Step 3: Sync on library changes**

In `HqApplication.kt`, add a lazy publisher and start collection in `onCreate`:

```kotlin
    val watchNext: WatchNextPublisher by lazy { WatchNextPublisher(this) }

    override fun onCreate() {
        super.onCreate()
        // Keep the system Watch Next row in step with the library.
        appScope.launch {
            media.library.collect { items ->
                watchNext.sync(items.mapNotNull { it.toWatchNextEntry() })
            }
        }
    }
```

Add imports: `com.livingroomhq.tvintegration.WatchNextPublisher`, `com.livingroomhq.tvintegration.toWatchNextEntry`, `kotlinx.coroutines.launch`.

Note: this intentionally constructs `media` during `onCreate` (the collect touches it). The demo repository is a cheap in-memory list; if cold-start profiling ever flags it, delay the collect behind `delay(5_000)` inside the launch block.

- [ ] **Step 4: Build check**

Run: `./gradlew :app:assembleDebug --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/kotlin/com/livingroomhq
git commit -m "feat: publish continue-watching to system Watch Next row"
```

---

### Task 11: Final verification + knowledge graph refresh

**Files:**
- Modify: `graphify-out/` (regenerated)

- [ ] **Step 1: Full build + all tests**

Run: `./gradlew clean :app:assembleDebug :core:data:testDebugUnitTest :app:testDebugUnitTest --console=plain`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Refresh the knowledge graph**

Run: `graphify /home/bobbygenerik/repos/LRHQ --update` (or re-run the AST + cached-semantic merge as done previously in this repo).
Expected: graph.json/graph.html/GRAPH_REPORT.md updated with the new `persist`, `iptv`, `home`, `tvintegration`, and `ui` packages.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: refresh knowledge graph after launcher-completion work"
```

---

## Manual verification on device (post-implementation)

Sideload and check, in order:
1. `adb install app/build/outputs/apk/debug/app-debug.apk`, press HOME → system shows the launcher picker including LivingRoom HQ (or the in-app banner's SET AS DEFAULT triggers the RoleManager dialog).
2. Favorite a channel in Live, force-stop the app, relaunch → star persists.
3. Surf three channels, relaunch → Recent Channels shows them newest-first.
4. From Tools, launch an app, uninstall it via adb, launch its card again → glass toast "Couldn't open that app", no crash.
5. Switch to the stock launcher → Watch Next row shows "Signal Lost" and "Glasslands" with progress.

## Out of scope (deliberately)

- XMLTV EPG for M3U-loaded channels (`epgNowNext` returns empty for non-demo channels; interface unchanged, slot in later).
- A settings screen for entering the playlist URL from the remote (needs an on-screen keyboard flow; `loadM3u` is callable and persisted, UI entry point is a separate feature).
- Channel logos via Coil in the Live list (dep already present; cosmetic).
