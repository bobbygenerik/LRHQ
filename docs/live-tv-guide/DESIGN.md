# Live TV guide redesign

The Live TV tab is a browsing instrument: compare channels at the same broadcast time, inspect a programme, and tune it without losing your place. The direction is restrained broadcast glass: OLED black, cool translucent surfaces, LRHQ's configurable accent, and a compact preview above the schedule. The Home tab retains its existing layout.

## References and evidence

| Service | Source | Useful pattern and adaptation |
| --- | --- | --- |
| BBC iPlayer, UK | [Designer’s TV Guide case study (2015)](https://www.michelangelozampogna.com/bbc-iplayer-tv-guide-2015.html) | Historical primary design evidence, rather than a claim about today's TV app. Its documented usability findings support a continuous current-time rule, persistent return to Now, distinct schedule gaps, and correct short-programme durations. Current iPlayer guide was inaccessible from the research environment. |
| Molotov, France | [Official interface announcement, July 2026](https://press.molotov.tv/fr/article/113/) | Direct TV organized by content type and favorites; LRHQ's category rail already provides this familiar structure. The linked official interface image was consulted. |
| CANAL+, France | [Official myCANAL experience announcement](https://www.canalplus.com/articles/actualites/decouvrez-la-nouvelle-experience-mycanal) | Separates current, replay and upcoming programming and exposes channels/themes. LRHQ labels future selections Coming up and makes the action Watch channel live; it does not imply catch-up or recording capability. |
| France.tv, France | [Official live programme surface](https://www.france.tv/collection/4541725-tele-visionnaire/) | Current-show times, description and viewing progress inform the selected-show details. |
| NOS, Portugal | [Official TV experience walkthrough, June 2025](https://forum.nos.pt/equipamentos-87/experiencia-tv-na-nos-a-mesma-tv-uma-nova-experiencia-48927/) | Guide, categories, favorites and remote navigation are documented with interface screenshots. Back returns to categories in LRHQ; the retained rail also supports international playlist groups. |
| MEO, Portugal | [Official TV functionality help](https://www.meo.pt/ajuda-e-suporte/produtos-meo/tv/funcionalidades) | Directional remote browsing from programme information into the guide. The Android TV manual was located, but could not be reliably rendered, so no current visual detail is inferred from it. |
| RTP Play, Portugal | [Official live TV](https://www.rtp.pt/play/direto/rtp2) and [platform help](https://media.rtp.pt/ajuda/) | Portuguese live broadcasting and connected-TV use. Consulted as a broadcaster reference; its website is not assumed to be an Android TV grid. |
| YouTube TV, US | [Official viewing and Live guide help](https://support.google.com/youtubetv/answer/7067974/watch-shows-sports-events-amp-movies) | A dedicated Live guide and channel customization. Retain LRHQ Favorites/Recent and a stable channel column. |
| Sky, UK | [Official TV guide help](https://www.sky.com/help/articles/your-playlist-and-tv-guide-sky-glass) | Guide as an explicit TV browsing destination. Consulted for behavior rather than asserting an exact current screen layout. |

These are design inputs. The resulting composition, dimensions and visual treatment are LRHQ-specific design decisions, not copies of any service or claims that all referenced services use this arrangement.

## Implementation

- Category rail remains on the left. Right enters the guide, or the preview for an empty category.
- Preview is 16:9 above the schedule. The programme detail panel describes the selected broadcast; the preview remains on the tuned channel and identifies its own channel/current show separately.
- Ninety minutes are visible at once on a common half-hour ruler. Programme widths represent actual durations, including clipped and very short shows. Horizontal browsing reveals up to the existing cached 12-hour horizon.
- Up/Down preserves the temporal cursor between channels. Left from live returns to categories, and Back always returns from the guide to categories. Top-row Up enters the persistent Now control.
- Now returns the schedule and cursor to the current time. Earlier/Later move through the horizon. Future selections still tune the channel live; no unsupported replay, reminders or recordings are offered.
- Cached guide schedules use the repository's existing conservative identity matching. Missing listings show Schedule unavailable; channels remain playable.
- Lazy rows and visible-channel prefetch keep large international playlists bounded. Focused descriptions load after a short settle delay. No new production dependency, global color, or font is introduced.
- Device 12/24-hour formatting and local dates are respected. Times remain UTC epoch values for comparison, including midnight boundaries.

## Validation

Validation results and emulator screenshots are recorded in VALIDATION.md. Emulator fixture schedules illustrate layout and interaction; they are not provider listings or proof of the user's Shield IPTV playback.
