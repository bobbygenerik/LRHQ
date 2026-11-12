# Validation — 4 October 2026 (America/New_York)

## Production build and tests

Command: `./gradlew :app:testProfileUnitTest :core:data:testReleaseUnitTest :app:assembleProfile --console=plain`.

- Build succeeded, including Profile lint checks and native packaging.
- App: 41 tests, zero failures/errors/skips. Includes six timeline tests and four Live TV state tests.
- Data: 80 tests, zero failures/errors/skips. Existing repository tests now also check full schedule lookup, half-open time boundaries, invalid windows and unmapped channels.
- `git diff --check` passed.
- Packaged application ID: `com.livingroomhq`. The emulator-only fixture activity/package is absent from the production manifest.
- Profile APK signature verified with SDK `apksigner`.
- Artifact: `app/build/outputs/apk/profile/app-profile.apk`.

The current repository prohibits Debug tasks, so validation uses Profile and the data library's Release fallback. Existing unrelated checkout changes are preserved and are also part of this working-tree build.

## Native UI checks

An isolated x86_64 emulator was created using temporary userdata outside the user's existing AVD. A temporary Gradle init script and fixture activity were injected only into a separate `com.livingroomhq.guideqa` package. These fixture sources are not production source files. Native translation libraries were omitted only from that emulator fixture package.

The fixture uses the real LiveScreen, guide, LiveTvViewModel and shared ExoPlayer preview. It supplies 84 illustrative channels, varied durations, a five-minute show, schedule gaps, favorites and an empty category. Video playback uses the public Mux Big Buck Bunny HLS test stream. Channel names and programme titles in screenshots are illustrative; the video is not a BBC broadcast.

Checks performed:

- Native rendering at 1920×1080 / 320 dpi and 1536×864 / 320 dpi. The compact layout shows three complete guide rows and truncates longer text within its bounds.
- D-pad entry from categories, Up/Down preserving airtime between different schedules, Left/Right programme browsing, shared timeline scrolling, and Back to categories.
- Selection of future programmes updates the details and the Watch channel live action while the preview stays on the tuned channel.
- Vertical navigation through channel 52 verifies scrolling beyond the initial 48-channel prefetch batch. The selected row remains visible after scrolling settles.
- OK launches the real fullscreen player with the selected channel; Back restores guide focus to that channel/programme. HLS video played in both preview and fullscreen.
- Empty Sports category renders its empty state; Right reaches preview and Down returns to categories without requesting an unattached guide focus target.
- A failed external test source returned HTTP 403. The Live-tab preview now shows Preview unavailable rather than leaving a black preview marked LIVE. The working HLS fixture subsequently displayed native video.
- No fatal exceptions or uninitialized focus requesters were found during the guide navigation checks.

Screenshots:

- [Current guide, 1080p](screenshots/guide-now.png)
- [Future programme selection](screenshots/guide-future.png)
- [Compact guide](screenshots/guide-compact.png)
- [Empty category](screenshots/guide-empty.png)

## Limits

The actual Shield was inspected as connected but was not updated. Its playlist/EPG data and real provider playback have not been validated with the new layout. The generated screenshots omit the global launcher sidebar because the fixture renders the Live tab in isolation. They prove native guide composition and test-stream behavior, not a deployment or live provider result.

The temporary emulator was shut down after validation. The user’s existing AVD userdata and Shield app data were not modified.

The code graph was refreshed using `graphify update .` (AST-only). Graph HTML generation is automatically skipped when the graph exceeds the tool's visualization limit; JSON and the report remain available.
