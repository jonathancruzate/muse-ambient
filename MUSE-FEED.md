# Muse Feed Schema

Muse Ambient polls a single JSON URL (set in MainActivity > Muse feed JSON URL;
empty = bundled sample/demo mode using `app/src/main/assets/home.json`).
Poll interval: every 5 minutes. Last good payload is cached to disk; any
failure is silent and the app keeps cycling art + the last good / sample data.

All fields are optional. The app renders gracefully with any subset.

```json
{
  "houseMode": "Home",
  "whoHome": ["Jonathan", "Stephany"],
  "daisy": { "status": "home", "note": "Walk at 12:30 PM with Mika" },
  "nextEvent": { "title": "Dinner", "time": "7:00 PM" },
  "nowPlaying": "Shield TV: ...",
  "alerts": ["Laundry done", "Bedroom purifier filter due", "Braava finished"],
  "message": { "text": "Daisy walker is here", "expiresAt": "2026-11-21T19:30:00Z" },
  "brief": { "title": "Daily Brief", "lines": ["line 1", "line 2", "line 3", "line 4"] },
  "updatedAt": "2026-11-21T18:00:00Z"
}
```

| Field | Type | Rendering |
|---|---|---|
| `houseMode` | string | Muse Home card: "House: ..." |
| `whoHome` | string[] | Muse Home card: "Home: a, b" |
| `daisy.status` / `daisy.note` | string | Muse Home card Daisy line |
| `nextEvent.title` / `nextEvent.time` | string | Muse Home card "Next:" line |
| `nowPlaying` | string | Muse Home card "Now playing:" line |
| `alerts` | string[] | Muse Home card, one warning line each |
| `message.text` | string | Muse card, plus once as a bottom overlay on the next art slide. No sound, no popup. |
| `message.expiresAt` | ISO-8601 string | Message hidden after this time; omit for no expiry |
| `brief.title` | string | Daily Brief card title (with the Muse wordmark) |
| `brief.lines` | string[] (max 4 used) | Daily Brief card bullets |
| `updatedAt` | ISO-8601 string | Informational; not rendered in v1 |

Rotation: a Muse card replaces an art slide every N slides (default 4,
configurable). When `brief` data is present, Muse slots alternate between
the Home card and the Daily Brief card. The "Muse" wordmark appears only on
Muse cards, never watermarked on art.

## Dashboard rendering (v0.3.0)

The Dashboard face renders the same payload differently (editorial design;
see README):

| Field | Dashboard rendering |
|---|---|
| `houseMode` | Spine line with a 10 px mode dot (accent fill for Home, umber for Night, hollow ring for Away). The exact value `Night` also triggers the nocturnal inversion. |
| `whoHome` | Ledger HOME slot, one line, comma-joined |
| `daisy.status` / `daisy.note` | Ledger DAISY slot: status line plus note (max 2 lines) |
| `nextEvent.title` / `nextEvent.time` | Ledger NEXT slot: title (max 2 lines) with the time flowing under it |
| `nowPlaying` | Bottom-right zone. Split into track / artist on the first ` - ` if present; a leading `Source: ` prefix is dropped. Idle values (`Nothing playing`, empty) hide the zone; a 15 s debounce prevents flicker between tracks. |
| `message.text` | Muse note: Newsreader italic marginalia with a `MUSE` attribution. Expiry rules unchanged. |
| `brief.title` / `brief.lines` | Daily Brief rotation moment below the datum: 45 s every 5 min between 07:00 and 21:00, only when `brief.lines` is non-empty; yields back to the ledger for 60 s when `daisy` or `nextEvent` changes. |
| `alerts` | Dashboard notification queue (top-right veil). Each string is one notification. A short `Category: headline` prefix (20 chars or fewer before the colon) is split into the eyebrow + headline; otherwise the whole string is the headline and no category is invented. Strings containing `doorbell` or `front door` get the 18 s hold and high priority; others hold 12 s. Only newly appearing strings are offered, so a persistent alert does not re-fire on every 5-minute poll. |
| `updatedAt` | For a live (non-sample) feed, a payload older than 45 minutes marks the feed stale: `nextEvent` and `nowPlaying` are silently suppressed until a fresh payload lands. The bundled sample is exempt so demo mode stays legible. |

## Producer (live since 2026-11-21)

The feed is produced by `producer/muse_feed_producer.py` in this project,
running on the Muse VM under the `muse-ambient-feed.timer` systemd timer
(every 5 minutes). A field is published only when its source verified
during that run; nothing is carried over from a previous payload except
the two agent-side caches below, which are date/age gated.

| Field | Source (verified per run) |
|---|---|
| `houseMode` | SmartThings location mode, live REST via the `st` wrapper (`mode-current`); fallback: HA loopback `sensor.house_mode` |
| `whoHome` | Google Location Sharing (`gls people`): Jonathan / Stephany count as home with a fix < 30 min old within 250 m of the home anchor |
| `daisy` | `bin/daisy_home.py` (SmartThings Find collar verdict). Fresh fix: note "Collar fix at home, N min ago"; stale collar: "Collar quiet; assumed home" (the assumed branch is always labeled) |
| `nextEvent` | Google Calendar agenda (`hatch_gws_cli calendar +agenda --days 3`), cache-first via `producer/calendar-cache.json` (see below); earliest timed future event, untitled skipped, cross-calendar duplicates collapsed |
| `brief` | `producer/brief-lines.json`, written by the `muse-ambient-brief-sync` runtime cron (every 15 min, goal `muse-ambient-app-for-shield-tv`) from the Daily Brief artifact's `getbrief` action (topOfMind + lookingAhead, max 4 lines). Used only when `briefDate` is today (America/New_York) and the cache is < 6 h old |
| `nowPlaying` | HA loopback `media_player.shield`, only when playing/paused with a media title and the entity changed within 20 min; otherwise omitted |
| `alerts` | HA HomeWhiz warning sensors that are on (dryer tank full, dryer check filter, washer no water); otherwise `[]` |
| `message` | Intentionally unset: no real Muse note pipeline exists yet, and a stale or invented note is worse than none |

Why the caches: `hatch_gws_cli` works from agent shells but times out
under the systemd unit's environment (diagnosed 2026-11-21), and
`getbrief` is an artifact action only an agent can call. The 15-minute
cron maintains both caches; the producer prefers a calendar cache
younger than 20 minutes and falls back to a direct gws call (45 s cap)
only when the cache is missing or stale.

Hosting: the producer ships the JSON over the existing SSH lane to the
Hetzner VPS (`/home/muse/muse-feed/home.json`, atomic rename, so a
failed run leaves the last good file in place). Tailscale Serve exposes
it on the VPS's **tailnet-only** :8443 service at
`https://vps.taila56db5.ts.net:8443/mf-<token>/home.json` (file target,
no directory listing; wrong paths get 403). The Shield is a tailnet
node, so the feed never leaves the tailnet; the 48-hex-char token in
the path is defense in depth and lives only in
`producer/.feed-token` (mode 600, gitignored, never printed). That URL
is set in the app's Settings (persisted `muse_feed_url` pref, verified
by read-back and by live dream captures on 2026-11-21).

Failure behavior: fail-silent end to end. A source that fails drops its
field for that run; if no source verifies, nothing is published; the
app keeps its last-good cached payload, and its 45-minute staleness
rule suppresses `nextEvent` / `nowPlaying` if publishing ever stalls.
Producer run state lands in `producer/producer-state.json` (gitignored).
