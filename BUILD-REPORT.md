# Muse Ambient v0.1.0, Build Report

Date: 2026-11-21. Builder: Smith subagent. Nothing was installed on the Shield and Jonathan was not messaged.

## Result: APK BUILT AND VERIFIED

- APK: `Muse-Ambient-0.1.0.apk` in this directory, 29 KB
- SHA-256: `f9fa8cf3041abcd1481dffe2bbaafc01bf41376440a11149afad64829aa1e82a`
- aapt2 badging: package `com.jonathancruzate.museambient`, versionCode 1, versionName 0.1.0, minSdk 26, targetSdk/compileSdk 30 (API 30 = the Shield's Android 11; no API-34 platform jar exists on this VM, the `~/android-sdk` symlink is broken, so 30 is the matched SDK per spec fallback)
- Badging confirms: `provides-component:'dream'`, leanback-launchable MainActivity, application banner present
- Manifest xmltree confirms: service `.AmbientDreamService` with `android.permission.BIND_DREAM_SERVICE`, exported=true, action `android.service.dreams.DreamService`, categories DEFAULT + DREAM, meta-data `android.service.dream` -> `@xml/dream`; leanback and touchscreen features required=false
- apksigner verify: v2 true, v3 true (debug keystore generated locally under `.toolchain/`, gitignored)
- APK contents confirm `assets/home.json` (bundled Muse sample) is packaged

## Build lane

VM first, and it worked, no VPS build needed. No system Java/Gradle on the VM, so the build uses the house framework-only lane (same as muse-gadget-shield): JDK 17.0.21 + build-tools 34 + android-11 platform jar from `~/workspace/shield-wifi-sense/.toolchain` (read-only), driven by `./build.sh` (aapt2 compile/link, javac, d8, zipalign, apksigner). A standard Gradle project is also scaffolded for Android Studio. One fix during the build: lambdas fail under `-bootclasspath android.jar` (no LambdaMetafactory in Android stubs), so all lambdas were rewritten as anonymous classes, after which the build passed clean.

## What was built

Standard project at `~/workspace/muse-ambient/`: settings.gradle, build.gradle, app/build.gradle, AndroidManifest.xml, Java sources, res (banner 320x180 PNG, ic_launcher vector, dream.xml, strings, layout), assets, README.md, MUSE-FEED.md, LICENSE (MIT), .gitignore, build.sh.

Core:
- `AmbientDreamService` (DreamService, interactive=false, fullscreen) and Leanback `MainActivity` (preview + settings, reflective DreamManager start with Settings fallback)
- Shared `AmbientView` slideshow: 14 bundled public-domain artworks via Wikimedia Commons Special:FilePath (stable pattern; per-museum IIIF URLs were not assumed), configurable JSON art feed with disk cache, crossfade, Ken Burns drift max ~6%, blurred-fill background (art never cropped), wall label, clock/date, Open-Meteo Astoria weather (hourly, fail-silent), night dimming 23:00-07:00 at 60% black overlay (~40% brightness), overlay position shifting, disk image cache, offline fail-silent throughout, D-pad skip in preview

## Muse integration (spec addition, folded into v1, not a later phase)

- Muse Home card every Nth slide (default 4, configurable) over a blurred wash of the last artwork: house mode, who is home, Daisy status/note, next event, now playing, alerts. "Muse" wordmark on Muse cards only, never on art.
- Daily Brief card variant (brief title + up to 4 lines); Muse slots alternate Home/Brief when brief data exists.
- Muse message: shown on the Muse card and once as a bottom overlay on the next art slide, expiry-aware, no sound/popups.
- Polling data source: configurable Muse feed URL (default empty = sample mode), polled every 5 minutes, last-good payload cached to disk, fail-silent. Full schema documented in `MUSE-FEED.md`; complete sample bundled at `app/src/main/assets/home.json` (verified inside the APK) so cards render with no live feed.
- Settings toggles: Muse cards on/off, Muse message overlay on/off, both default ON. Producer side is documented as out of scope in MUSE-FEED.md (a future VPS/runtime job publishing house-state JSON); no live URL was invented.

## Quality gate (re-read after build)

Manifest and DreamService re-checked: BIND_DREAM_SERVICE present, DREAM category present, dream meta-data present, exported flags set (activity and service), Leanback banner present, settingsActivity points at MainActivity.

## Honest limitations / not done

- Not installed or run on the Shield, per instructions, so on-device behavior (screensaver selection UI, actual image URLs resolving, DreamManager reflective start) is unverified. Individual Wikimedia filenames follow the stable Special:FilePath pattern but were not fetched/verified one by one; the loader skips any item that fails to decode and moves on.
- Weather and feeds need the Shield online; offline paths are code-reviewed, not device-tested.
- Debug-signed only. Gradle build itself was not run (no Gradle/network on VM); the Gradle files are for Android Studio, the verified artifact came from build.sh.
- No producer job yet: Muse cards run on the bundled sample until a feed URL is entered in Settings.

---

# Muse Ambient v0.2.0, Build Addendum

Date: 2026-11-21. Built at Jonathan's spec: "Art mode should be high
resolution image filtered to look as matte as possible on my tv model
with gentle muse popups in a non intrusive way that blend into the art."
v0.1.0 had by then been installed and verified live on the Shield by the
parent agent (screenshots `~/workspace/shield/muse-ambient-first-launch.png`
and `muse-ambient-dream-live.png`).

## Result: APK BUILT, VERIFIED, INSTALLED, DREAM-VERIFIED LIVE

- APK: `Muse-Ambient-0.2.0.apk`, 33 KB, versionCode 2, versionName 0.2.0
- SHA-256: `83f64945e29d59fb2b0f3f8e4253170aa964a84b809495b589b2c64c9efa6bf3`
- aapt2 badging: package/version as above, `provides-component:'dream'`,
  leanback-launchable MainActivity; apksigner v2 true, v3 true.
- Installed over adb (`install -r`, Success); device read-back
  versionCode 2 / 0.2.0; `screensaver_components` still
  `com.jonathancruzate.museambient/.AmbientDreamService`.
- Dream verified: temporary `screen_off_timeout` 15000 trigger produced
  `mCurrentDreamName=ComponentInfo{com.jonathancruzate.museambient/...AmbientDreamService}`
  and `mWakefulness=Dreaming`; timeout restored to 300000 and the device
  woken afterwards. Captures: `muse-ambient-v020-preview.png`,
  `muse-ambient-v020-dream.png`.

## Root cause of the v0.1.0 "low resolution" (found this build)

The dream was never showing the artwork at all. In v0.1.0's `showArt`,
the crossfade animator on the foreground ImageView was cancelled to
start the Ken Burns animation, freezing the foreground alpha at ~0; only
the blurred-fill background (downscaled 24x) ever displayed. A 24x-blur
of the right painting reads as "very low resolution". v0.2.0 separates
the two: foreground alpha crossfade and Ken Burns are independent
ObjectAnimators, so cancelling one can never freeze the other.

## What changed in v0.2.0

1. **High resolution**: BuiltInFeed requests `Special:FilePath?width=3840`
   (was 1920); ImageLoader steps down 3840 -> 2560 -> 1920 per item on
   failure, decodes at full available resolution capped at 3840 wide
   (bounds decode + power-of-two sampling), logs every decoded size, and
   uses a versioned cache dir (`images-v2`) so no v0.1.0 cache entries
   are reused. On-device logcat proof: Starry Night decoded at
   3840x3041. (One v0.1.0 file, Impression Sunrise, maxes out at
   1051x808 on Commons; that is the file's ceiling, not a fetch failure.)
2. **Matte finish** (new `ImageLoader.matte` + `MatteOverlay` view):
   ColorMatrix chain per slide, precomputed once: saturation x0.85,
   contrast x0.90, blacks lifted (+18.75 offset). Over the art at render
   time: generated paper grain (256px noise tile, ~6 percent alpha,
   OVERLAY blend) and a radial vignette (edges darkened ~12 percent).
   The artwork bitmap itself is never blurred.
3. **Gentle blended Muse elements**: the message overlay was moved out
   of the bottom band into its own reserved zone and given slow
   1500 ms fades (this pill form was then replaced entirely by the
   signature treatment in v0.2.1, below); Muse Home / Daily Brief cards
   got a blurred, darkened artwork-wash surface (~70 percent opacity,
   rounded corners) and crossfades instead of hard panels/pops.

## Honest read of the v0.2.0 captures

Preview and dream captures show the artwork genuinely sharp (individual
brushstrokes in Starry Night at 3840 wide), clock/weather bottom-left
and wall label bottom-right with no overlaps. The matte treatment is
gentle by design: from screenshots the image reads less glossy-hot than
v0.1.0 with slightly muted saturation; the final matte judgment is
Jonathan's, on the panel. Superseded the same day by v0.2.1 (below).

---

# Muse Ambient v0.2.1, Build Addendum

Date: 2026-11-21. Two spec additions from Jonathan landed while v0.2.0
was being built/installed, so they shipped together as v0.2.1
(versionCode 3): (1) "Only use art that fits my tvs aspect ratio."
(2) Muse notifications as an artist's signature blended into the art.

## Result: APK BUILT, VERIFIED, INSTALLED, DREAM-VERIFIED LIVE

- APK: `Muse-Ambient-0.2.1.apk`, 33 KB, versionCode 3, versionName 0.2.1
- SHA-256 (final build): `ae1ba3db30e2f1323e73668ca8051b2bf4e6ae006f5ba8307abe3a4231b45001`
  (An earlier v0.2.1 build, SHA `9fd01d48...`, was installed and dream-
  verified first; a message-timing race found in its captures — the
  message state was snapshotted before the feed finished loading, so the
  signature missed the first slide — was fixed by evaluating the message
  after the slide lands, and the APK was rebuilt under the same version
  before delivery. The SHA above is the delivered artifact.)
- aapt2 badging: versionCode 3 / 0.2.1, `provides-component:'dream'`;
  apksigner v2 true, v3 true. Installed with `install -r` (Success);
  device read-back versionCode 3 / 0.2.1; `screensaver_components`
  unchanged. Dream verified the same way as v0.2.0 (temporary 15000
  timeout, `mCurrentDreamName` = Muse Ambient, `mWakefulness=Dreaming`),
  timeout restored to 300000 and device woken, both re-read and confirmed.
- On-device logcat proof of the new playlist loading at full width, in
  order: Autumn On the Hudson River 3840x2113, Washington Crossing the
  Delaware 3840x2257, The Icebergs 3840x2175. No aspect skips logged.
- Captures: `muse-ambient-v021-preview.png`, `muse-ambient-v021-dream.png`.

## Aspect rule (hard curation + enforcement)

- Rule: keep only art within plus/minus 5 percent of 16:9 (1.778):
  accept ratio 1.69 to 1.87. No exceptions for fame.
- Every candidate's real pixel dimensions were verified through the
  Wikimedia Commons API (`action=query, prop=imageinfo, iiprop=size|url,
  iiurlwidth=3840`) before inclusion; ratios below are from metadata,
  not memory. Two of the spec's own estimates did not survive
  verification: the famous MMA scan of Washington Crossing the Delaware
  measures 1.561 (out; a different Met scan of the same canvas measures
  1.702 and is used instead), and The Gulf Stream measures 1.663 in
  every Commons version (out).
- **Kept (11)**, dimensions are Commons originals:
  The Heart of the Andes (Church, 1859) 6400x3503, 1.827;
  Autumn, On the Hudson River (Cropsey, 1860) 4000x2201, 1.817;
  Fox Hunt (Homer, 1893) 5000x2788, 1.793;
  Rye (Shishkin, 1878) 4000x2254, 1.775;
  A Dash for the Timber (Remington, 1889) 4236x2400, 1.765;
  The Icebergs (Church, 1861) 4000x2266, 1.765;
  The Grand Canyon of the Yellowstone (Moran, 1872) 1555x895, 1.737;
  The Chasm of the Colorado (Moran, 1873) 1565x902, 1.735;
  The Valley of Wyoming (Cropsey, 1865) 3807x2199, 1.731;
  Cosmic Cliffs in the Carina Nebula (JWST, 2022) 14575x8441, 1.727;
  Washington Crossing the Delaware (Leutze, 1851) 3888x2285, 1.702.
- **Removed from the v0.1.0 playlist (all 14)**, verified ratios:
  The Starry Night 1.263, Impression Sunrise 1.301, The Milkmaid 0.892,
  Girl with a Pearl Earring 0.844, Mona Lisa 0.671, The Kiss 0.997,
  Wanderer above the Sea of Fog 0.781, The Night Watch 1.194,
  The Fighting Temeraire 1.346, The Great Wave off Kanagawa 1.461,
  Red Fuji 1.498, Grande Jatte 1.502, The Birth of Venus 1.592,
  Water Lilies (its v0.1.0 filename no longer resolves on Commons).
  Also checked and rejected: The Gulf Stream 1.663, Eaton's Neck 2.071,
  Emigrants Crossing the Plains 1.605, The Aegean Sea ~1.58,
  Wet Meadow ~1.62-1.68, Dream of Arcadia 1.632, Among the Sierra
  Nevada 1.673, Breezing Up 1.591, The Lackawanna Valley 1.491.
- **Loader enforcement**: `ImageLoader.load` checks the decoded bitmap's
  ratio for ANY feed (bundled or remote JSON) and silently skips
  anything outside 1.69-1.87 (logged as `skip (aspect ...)`), so a
  mismatched piece can never be shown even from a remote playlist.
- **Display**: foreground is now CENTER_CROP, so in-tolerance art fills
  the 16:9 frame exactly, cropping at most the few-percent remainder.
  The blurred fill remains only as the crossfade backdrop.

## Signature notifications (replaces the v0.2.0 pill)

- The Muse message on art slides now renders as an **artist's
  signature**: cursive italic typeface (`Typeface.create("cursive",
  ITALIC)`), no pill, box, or border; text is the message plus a
  right-aligned "Muse" sign-off line, in the bottom-right corner column
  stacked above the wall label so it cannot overlap the label, clock,
  or weather.
- **Automatic colour matching**: per slide, the app samples the corner
  region's average luminance and the whole-canvas average colour from
  the current (matte) bitmap, picks a light or dark tone drawn from the
  art's own palette on the opposite side of the corner luminance
  (with a warm near-black / near-white legibility fallback), renders at
  ~80 percent opacity, and adds the faintest emboss shadow in a sampled
  dark tone.
- Behaviour unchanged otherwise: slow fade in (~1500 ms), hold for the
  slide, slow fade out, once per message, plus its appearance on the
  Muse card. The Muse Home / Daily Brief cards keep the v0.2.0 washed-
  panel treatment.

## Honest read of the v0.2.1 captures

- Preview (`muse-ambient-v021-preview.png`): The Icebergs fills the
  preview frame edge to edge, no letterbox bars, fine ice/water detail
  at 3840 wide; clock/weather and label clean in their corners.
- Dream (`muse-ambient-v021-dream.png`): Autumn, On the Hudson River
  fills the full 4K frame. At full resolution (crop inspected at
  2100-3840 x 1300-1850), the sample message renders exactly as
  specified: "Welcome to Muse Ambient (sample mode)" in cream cursive
  script with "Muse" signed beneath it, right-aligned in the corner
  above the wall label, legible against the foliage, no box or pill,
  no overlap with label, clock, or weather. In the full-frame downscale
  it is easy to miss, which is the point of the treatment.
- The signature sits somewhat higher above the label than the nominal
  250px bottom margin suggests (dream-window insets appear to add
  clearance); the visible result is a comfortable gap, verified
  non-overlapping in the capture.
- Limitations: the two Thomas Moran originals are only ~1.5k wide on
  Commons, so those two slides will look softer than the 3840-wide
  rest; they are the best Commons scans of those canvases. The matte
  strength and the signature's legibility on bright pieces (e.g. The
  Icebergs' pale sky corner) are judged from the Autumn capture and
  code review; bright-corner slides use the dark sampled tone by the
  same luminance rule. The Shield ADB lane dropped once during this
  verification (a 0-byte screencap and a timeout restore that did not
  land); the lane was restarted, the restore was re-applied and
  re-read (300000, Awake, screensaver component intact) before the
  final capture run. No producer job yet: Muse cards/message still
  run on the bundled sample until a feed URL is set.

# Muse Ambient v0.2.2 (FINAL), Build Addendum

Date: 2026-11-21. Owner direction folded into this build: art perfectly
still (no Ken Burns), fixed zones, ALL overlay text in cursive, ONE ink
tone per slide sampled from that slide's painting, weather stays on art
slides, the signed note appears when the feed has a message, Muse cards
stay in the rotation, and live feed alerts render top-right. A Gemini
design consultation (via the Gemini Spark MCP lane) supplied the detailed
typography/contrast parameters; the full consult lives at
`~/workspace/shield/gemini-ambient-design-consult.md`. Per the owner's
later direction, v0.2.2 is the app's Art mode baseline; a Dashboard mode
follows separately as v0.3. No further polish rounds were added to this
build.

## Result: APK BUILT, VERIFIED, INSTALLED, DREAM-VERIFIED LIVE

- APK: `Muse-Ambient-0.2.2.apk`, 107,484 bytes, SHA-256
  `9bffec33a207bd8c945a1da3e522aa8693e3b86d56a858a38639ddeb8c4f6bc9`.
- versionName 0.2.2, **versionCode 5** (the interim v0.2.2 build used
  code 4; the final went to 5 to keep the record clean). versionCode is
  set in both `app/build.gradle` and the manifest (the manifest carries
  it for the aapt2 build lane).
- aapt2 badging: package `com.jonathancruzate.museambient`, sdkVersion
  26, targetSdkVersion 30, leanback-launchable MainActivity,
  `provides-component:'dream'`. APK contains
  `assets/fonts/Caveat-Regular.ttf` (55,564 B), `Caveat-Medium.ttf`
  (55,612 B), `assets/fonts/OFL.txt`, and the cleaned sample
  `assets/home.json`.
- apksigner verify: v2 true, v3 true (debug key, house lane).
- Installed with `install -r` over the Shield ADB tailnet lane;
  `dumpsys package` read back versionCode 5 / versionName 0.2.2;
  `screensaver_components` unchanged
  (`com.jonathancruzate.museambient/.AmbientDreamService`).
- Real dream verified twice: temporary `screen_off_timeout` 15000, HOME,
  `dumpsys dreams` = AmbientDreamService current, `mWakefulness=Dreaming`;
  timeout restored to 300000 and re-read after each run.

## Font and density math (measured on the Shield)

- Device: physical 3840x2160, override 1920x1080, density 320 (factor
  2.0). The UI therefore lays out on a 1920x1080 grid upscaled x2 to the
  panel, so **panel px / 4 = sp exactly**. Gemini's 2160p pixel scale
  converts 1:4 into sp with no rounding drift.
- Typeface: **Caveat** (Google Fonts, SIL OFL 1.1), chosen per the
  consult's guardrails (open, low-contrast cursive, high x-height).
  Regular (400) and Medium (500) TTFs, subset with fontTools to Latin
  basic + Latin-1 supplement + general punctuation (em/en dash, curly
  quotes, ellipsis, bullet, euro, trademark), kern/liga/tnum kept, no
  hinting: ~55.6 KB each. Loaded via `Typeface.createFromAsset`; stock
  "cursive" is the fallback. OFL text ships in the APK.
- Scale applied (panel px -> sp): clock 84 -> 21 (Medium), AM/PM suffix
  44 -> 11 as its own baseline-aligned TextView, date 42 -> 10.5, weather
  38 -> 9.5, label title 44 -> 11 (Medium, right-aligned), artist 36 ->
  9, collection 32 -> 8 (**the consult's 32px floor, exactly met**),
  note 46 -> 11.5, sign-off 36 -> 9, alert 44 -> 11. Card scale: hero
  27 (Medium), ledger value 14.5, ledger label 9.5, brief title 26,
  brief lines 14, card sign-off 10.
- Margins: 144px L/R -> 36dp, 120px T/B -> 30dp. Note max width 36% of
  frame; alert max width 28%.

## Gemini parameters: applied, approximated, rejected

Applied as specified:

- Shared base text opacity 0.74 (0.72 light ink / 0.76 dark ink), bounds
  respected (never above 0.82, never below 0.62); low-contrast slides
  collapse to 0.78. Alert text peaks at 0.82.
- Polarity-aware shadow, single treatment, no pure black/white, no hard
  outlines: light ink gets the painting's deepest tone at ~0.46 alpha,
  dy ~3px panel, blur ~12px panel; dark ink gets a soft highlight cushion
  from the painting's lightest tone.
- Scrim: conditional radial corner vignette only, default OFF, triggered
  per corner below 4.5:1 local contrast, caps 0.26 dark / 0.22 pale,
  tinted from the painting (deepest/highlight tone). Never a linear bar.
- Reactive ink: 192x108 downscale; hue from a saturation-weighted 36-bin
  histogram over mid-chroma pixels (circular mean), warm ~75 degree
  fallback for monochrome; polarity from the occupied corners (TL, BL,
  BR) using median luminance with p85/p15 tail scoring and a light-ink
  tie-break; one tone for ALL text on the slide; light ink clamped to a
  warm off-white band, dark ink to a deep band; contrast loop targets
  4.5:1-7.5:1 and feeds the scrim when it cannot get there.
- Note composition: 5-14 words, no metadata parentheticals, "— Muse"
  sign-off indented beneath; silence rule (no feed message = empty
  corner). The bundled sample message is now "Good light for the gallery
  today, enjoy the quiet." (9 words).
- Alerts: same font/ink/shadow, max 2 lines, fade-in 1400 ms, hold
  14 s, fade-out 2200 ms, one at a time, 5-minute per-alert re-show
  cooldown, suppressed while a Muse card is up.
- Muse cards toward the broadside direction: full-frame blurred wash of
  the preceding painting (saturation 0.65, tonal veil), no boxes, panels,
  or borders; Home as a single ledger (label + value rows) instead of
  any grid; Brief as a centered broadside with hanging-indent lines and
  the "— Muse" sign-off; card text in the slide's cursive ink at
  ~0.86 effective opacity.

Approximated (recorded honestly):

- The consult's color math is specified in OKLCH; the implementation
  uses HSV/HSL approximations in the existing Java (hue histogram,
  HSL lightness walk). The intent (hue family, polarity, contrast band)
  is preserved; it is not literal OKLCH.
- The shadow is a single Android shadow layer per TextView rather than
  the consult's dual-pass render; radius/dy follow its panel values.
- Ledger row pitch and the brief column's optical centering are
  approximated with padding (no 47% optical-bias offset).

Rejected per the owner's rules (outrank the consult):

- Per-corner polarity switching (breaks the one-ink rule). Its
  worst-corner scoring method for CHOOSING the single tone was adopted.
- The serif alternative to cursive; dropping weather from art slides;
  making the note intermittent; virtual museum mats.
- The consult's invented example copy was not used anywhere.

## The boring-layout bug (found and fixed in this pass)

Non-"boring" TextView layouts built at setText time (an AbsoluteSizeSpan
in the clock, BREAK_STRATEGY_BALANCED on the note) reuse the view's
stale narrow measured width and clip everything past the first token.
Ground truth came from `uiautomator dump` in the MainActivity preview:
the clock's text was '3:20 pm' with bounds only 85 layout px wide, and
the note carried the full string in 60 px. Fixes: the clock suffix is
its own plain TextView in a baseline-aligned row; the note lost the
balanced break strategy and got an EXACT width (36% of frame); spanned
card TextViews (ledger rows, brief lines) got exact/MATCH_PARENT widths
for the same reason. Post-fix dump verified full bounds for clock,
suffix, note, and alert. Lesson: any spanned or break-strategied
TextView in this codebase gets an explicit width.

## Alert verification (test build disclosure)

The alert treatment was verified on the real dream with a test build:
identical final code, with the bundled sample temporarily carrying one
alert ("Package delivered at the front door"). Full-res corner crops of
that capture (`muse-ambient-v022-test-alert.png`, SHA-256
`b7bec9dd6ffd2fb6ae98a498237db67868dc4a5a07245349193c00d510689fc1`)
show the alert top-right in Caveat, right-aligned, no box. The shipped
build differs only in the bundled sample's alerts array (empty).

## Honest read of the final captures

- Capture 1, dream, Autumn, On the Hudson River
  (`muse-ambient-v022-final-dream.png`): every occupied corner inspected
  at full resolution. Clock "3:24" + suffix, date, and weather sit
  top-left in warm light ink, legible over sky and foliage. The note
  renders in full on one line ("Good light for the gallery today, enjoy
  the quiet.") with the "— Muse" sign-off beneath, bottom-left, no
  collisions. The wall label is right-aligned bottom-right, three clean
  lines. Top-right is empty, as designed (no alerts in the shipped
  sample). The painting is full-frame and still.
- Capture 2, dream, Washington Crossing the Delaware: verified from the
  full frame during the run (a 720p downscale is kept as
  `muse-ambient-v022-final-dream2-washington-720p.png`; the full-res
  file was overwritten by a later capture attempt, see below). Light ink
  over a bright sky top-left and bright ice bottom-right: readable with
  the shadow's help, the softest text in the set. This is the known cost
  of the one-ink rule on split-luminance paintings, disclosed rather
  than engineered around.
- The AM/PM suffix renders (proven in the hierarchy dump: text 'pm',
  correct baseline-aligned bounds) but at 11sp over textured corners it
  reads faintly in stills. Noted as-is; no extra polish round was taken.
- **Not captured with the final build**: The Icebergs (the pale,
  dark-ink case) and the Muse Home card. After capture 1, the living
  room TV came into active use (a game stream playing on the Shield):
  it repeatedly dismissed the dream, held the screen against later dream
  attempts, and absorbed stray key events. Rather than keep interrupting
  the room, verification stopped. The dark-ink polarity path is the same
  `computeInk` code exercised on the other slides, and the card layout
  uses the same exact-width defenses proven in the dumps, but neither
  has a final-build screenshot. Both should be grabbed opportunistically
  the next time the TV is idle.
- The test-build alert capture (above) plus capture 1 together cover
  every overlay zone of the final design on the real dream surface.

## Standing state after this build

- Shield: Muse Ambient v0.2.2 (code 5) installed, system screensaver,
  `screensaver_activate_on_sleep` 1, `screen_off_timeout` restored to
  300000, device left awake.
- Still no live feed producer: Muse cards, note, and alerts run on the
  bundled sample until a producer job publishes the feed JSON and its
  URL is entered in Settings.

---

# Muse Ambient v0.3.0, Build Addendum (Dashboard mode)

Date: 2026-11-21. Built to the fresh Gemini redesign consultation
(`~/workspace/shield/gemini-dashboard-redesign-consult.md`): the cursive
system stays on the Art face only; the new default Dashboard face is an
editorial design (Newsreader + Source Sans 3) on the static quiet
plaster backdrop selected in `dashboard-bg/BG-NOTES.md`. Art mode
rendering was not touched.

## Result: APK BUILT, VERIFIED, INSTALLED, DREAM-VERIFIED LIVE

- APK: `Muse-Ambient-0.3.0.apk`, 1,476,196 bytes, versionCode 6,
  versionName 0.3.0
- SHA-256: `3008ddf0861e9daac2b9595bebc86fa76e57b86e99a32f4e75f3460f7d1bf1ba`
- aapt2 badging: package/version as above, `provides-component:'dream'`,
  leanback-launchable MainActivity, banner present
- apksigner verify: v2 true, v3 true (debug keystore, as before)
- APK contents confirm: `assets/dashboard-bg.jpg` (byte-identical to the
  final plaster asset, SHA-256 `a479d3cc4ed7348def229d184361efb916c6068fd4d2acb3bf968a0395bcce1f`),
  the seven new font files (Newsreader Light/Regular/Italic instances,
  Source Sans 3 Regular/Medium/SemiBold), and `assets/fonts/OFL.txt`
  carrying all three OFL texts (Caveat, Newsreader, Source Sans 3)
- Density math (verified like v0.2.2): the Shield UI grid is 1920x1080
  at density 320 on the 3840x2160 panel, so view px = panel px / 2 and
  sp = panel px / 4. The consult's 44 px legibility floor is 11 sp; the
  smallest dashboard text (44 px labels) sits at 11 sp, the clock at
  59 sp (236 px). Screencaps from the device are full 3840x2160.

## Adopted from the consult

- Two-family editorial pairing, no cursive on this face; full type scale
  converted at panel px / 4; letter spacing and line heights per spec.
- Permanent left spine (clock, full date, weather, house mode with a
  10 px dot: accent fill Home, umber Night, hollow ring Away) over the
  2 px hairline datum at panel y 1040, spanning x 240..1605.
- Ledger in three pinned slots (HOME y 1100, DAISY y 1280, NEXT y 1600)
  that dissolve without reflow; the hairline itself fades when all three
  are empty. Right half of the frame stays open plaster at rest.
- Muse note as Newsreader italic marginalia at x 1950 with the 3 px
  slate-teal rule and MUSE attribution; Now Playing bottom-right, text
  only; Daily Brief as a rotation moment (45 s every 5 min, 07:00 to
  21:00, yields 60 s when Daisy or Next changes).
- One ink `#2B2724` in the specced opacity tiers, one accent `#2F4F5B`;
  zero text shadows, zero scrims (the Art face's MatteOverlay and
  ScrimView are hidden on this face, and the backdrop is decoded raw,
  not matte-processed, so the final asset renders as authored).
- Notifications per spec: top-right warm-paper veil `#EFEAE2` at 0.56
  with the 4 px slate bar, 500 ms in, 12 s hold (18 s doorbell), 1500 ms
  out, one visible, FIFO queue (depth 3, 60 s expiry), 30 s per-source
  cooldown, duplicate extension, doorbell preemption.
- Opacity-only motion with the consult's ease-calm curve; unchanged
  polls are a hard no-op (feed signature diff).
- Dusk warm ramp from Astoria sunset (NOAA approximation computed on
  device) to 0.16 by 30 minutes after sunset; nocturnal inversion on
  Night mode or 23:00 to 06:30 with the specced content suppression
  (ledger HOME and NEXT off, note off, brief rotation paused, Daisy
  only when away/out, condensed date + temperature line).

## Approximations (recorded, not silent)

- Newsreader is bundled as static optical-size instances fetched from
  Google Fonts (Light at opsz 72 for the clock, Regular at opsz 36 for
  the brief title, Regular at opsz 28 for Now Playing, Italic at opsz 28
  for the note) instead of one variable font with runtime axis control;
  Source Sans 3 as static 400/500/600. Same families, weights, and
  optical sizes the consult specifies.
- The clock minute change fades the whole clock row over 600 ms rather
  than crossfading individual digits (digits and meridiem are separate
  views in one row; tabular figures keep positions fixed).
- The nocturnal backdrop is the plaster asset under a 97 percent
  `#1B1816` overlay, not a separately generated dark plaster texture;
  the shipped asset file is untouched.
- Feed alerts are single strings: a category is split only from a short
  `Category:` prefix, and the subtext is the generated `Just now`. No
  category or fact is invented for unprefixed alerts.
- `nowPlaying` is one string in the schema: track/artist split on the
  first ` - `, and a leading `Source: ` prefix is dropped.
- Stale-feed suppression (45 min) applies to live URL feeds only; the
  bundled sample is exempt so demo mode stays legible.
- The consult's `wall_luminance_scale` calibration float was not added;
  the dusk ramp plus nocturnal inversion cover the dimming concern.
- The brief eyebrow is the literal `DAILY BRIEF` label; the feed's
  `brief.title` renders as the brief headline under it.

## Bugs found and fixed during verification

1. **Stranded notification queue**: the first feed can reach
   DashboardView through the feed-poll path before `onShow` marks the
   face active; the pump returned early and the alert sat in the queue
   forever (proven with temporary logcat instrumentation, removed
   before the final build). Fixed: the queue is pumped on show and on
   every feed landing while active.
2. **Full-height notification veil**: the accent bar was MATCH_PARENT
   height inside the wrap-content box, and FrameLayout measurement
   stretched the box to full height. Fixed: provisional fixed bar
   height, resized to the box's measured height after layout.
3. **NEXT time stranded**: the time was pinned at the two-line title
   position, leaving a gap under one-line titles. Title and time now
   flow inside the pinned slot.
4. **Note rule length**: the accent rule spanned the full zone budget;
   it now spans the note itself (estimated from its line count).
5. Dashboard weather condition is title-cased on this face only (the
   Art face keeps the WeatherClient sentence-case form it shipped with).

## Install and dream proof

- `install -r` Success over the tailnet lane; device read-back
  versionCode 6 / 0.3.0; `screensaver_components` unchanged
  (`com.jonathancruzate.museambient/.AmbientDreamService`); the
  Settings radio read-back (uiautomator) shows Dashboard checked.
- Real dream verified with the final binary: `mCurrentDreamName` =
  AmbientDreamService, `mWakefulness=Dreaming`; resting capture
  `~/workspace/shield/muse-ambient-v030-dashboard.png` (4:11 PM frame).
- Staged notification capture
  `~/workspace/shield/muse-ambient-v030-notification-staged.png`:
  taken in the real dream with a test build whose ONLY difference from
  the final build was one staged sample alert
  (`Doorbell: Front door bell rang`); the final sample was restored
  byte-identical afterward (home.json SHA-256
  `1254259d2c8a290fa15163c15cb1740a4312cb65fa51ee5775fc311c30492b9e`)
  and the final APK rebuilt and reinstalled before the resting capture.
- Every verification cycle restored `screen_off_timeout` to 300000 and
  re-read it; the device was left Awake with the screensaver intact.
  The tailnet forwarder died twice during verification (device
  offline); it was relaunched detached (setsid) and the timeout
  restore was re-verified after each drop. One attempt found a
  launcher app menu open (the room was using the Shield); verification
  paused rather than fighting the room for the screen.

## Visual judgment (screencaps, full-res crops inspected)

The resting face reads as printed wall text, not a UI: the Newsreader
clock is calm and architectural, the spine is disciplined, the
hairline is a whisper, the three ledger slots sit exactly on their
pins with no crowding, and the Muse note sits as true marginalia with
its slate rule. Ink on the plaster is high contrast at every occupied
position (primary values are effectively 7:1 or better against the
216 to 239 luminance backdrop). The right half is genuinely empty
plaster, which is the point: the screen defers to the room. The staged
notification is small, warm, and legible without reading as a system
alert. Honest soft spots: the mode dot is tiny by design and reads as
a punctuation mark rather than a status light at distance; the note
rule is the strongest vertical element on the face and earns it, but
it is the first thing a future polish pass should re-judge in the room.

## In-room Wyze check: blocked by the away-only gate (not bypassed)

Attempted through the Home Assistant loopback API while preparing the
check: `camera.camera` exists (state idle, STREAM supported) and
`sensor.house_mode` reads `home`; the snapshot request returned HTTP
500, which is the camera's away-only ffmpeg gate working as designed
(the stream only opens when the house mode is away). Jonathan offered
the camera for this check, but the gate is a standing mode rule and it
was not changed, so no in-room frame was captured and no camera-based
judgment is claimed. The glare / room-scale / TV-and-painting judgment
should be made from a Wyze frame on a future away-mode occasion, or
from the room directly.

## Deferred

- Brief rotation moment and Now Playing zone not captured on device:
  the sample's brief first rotates 5 minutes into a dream session
  (verification windows were shorter) and the sample has no
  `nowPlaying` value. Both code paths render through the same
  populate/fade machinery verified for the ledger and note.
- Nocturnal inversion not captured (needs Night mode or 23:00).
- In-room camera judgment, per the gate note above.

---

# Muse Ambient v0.3.1, Calibration Addendum (wall luminance scale)

Date: 2026-11-21. One-variable tuning pass, prompted by the first
in-room Wyze frame of the v0.3.0 dashboard
(`~/workspace/shield/wyze-tv-dashboard.png`, 16:48 EST, taken through
Home Assistant `camera.camera` after Jonathan manually overrode the
camera on): the content read beautifully, but the panel was by far the
brightest object in the room, the plaster rendering as a glowing
near-white lightbox against the mid-tone wall. That is risk #1 in the
Gemini dashboard consult, which had specced a `wall_luminance_scale`
calibration float (0.70-1.05) for exactly this.

## Starting implementation (what v0.3.0 actually had)

- The backdrop was decoded raw and rendered unscaled in
  `AmbientView.dashBg` (asset luminance 216-239). The consult's
  `wall_luminance_scale` was NOT implemented (recorded in the v0.3.0
  approximations above).
- The only brightness shaping on the face was inside DashboardView:
  the sunset dusk ramp (warm `#2A1F16` overlay, 0 to 0.16 over the
  30 minutes after an on-device NOAA sunset) and the nocturnal
  inversion (97 percent `#1B1816` overlay on Night mode or
  23:00-06:30). Neither addresses a late-afternoon lightbox.

## The change (one variable)

- New constant `WALL_LUMINANCE_SCALE` in `AmbientView`, applied as a
  linear RGB `ColorMatrix` color filter on the `dashBg` ImageView
  only, in `presentDashboard()`. The backdrop bitmap asset is
  untouched; dashboard ink, type, layout, dusk ramp, and night
  inversion are untouched; the Art face is untouched. The no-bitmap
  fallback color is the consult mean plaster `#DDD5C9` at the same
  scale. Version bumped to 0.3.1 / versionCode 7 (manifest, Gradle,
  build.sh output name).

## Scales tried, judged from the frames

Scales are rendered-pixel facts first, camera facts second:

- **1.00 (v0.3.0 baseline)**: Shield screencap open-plaster median RGB
  (231, 222, 210). Wyze frame panel-to-wall median luminance ratio
  3.14 (open plaster 232.2 vs wall right of TV 74.0).
- **0.80**: screencap median (185, 177, 168), a true 0.80 render of
  the baseline, filter proven in the pixels. Wyze ratio 2.98 (panel
  231.6 vs wall 77.7). Ink fully legible in the frame; banding no
  worse than baseline.
- **0.70** (test build, installed and framed, then rejected): Wyze
  ratio 2.97 (panel 232.0 vs wall 78.2), no further gain over 0.80,
  visibly stronger horizontal banding across the panel in the camera
  frame, and less ink-contrast headroom (rendered backdrop falls to
  roughly code 162, where the alpha-blended secondary ink's contrast
  drops well below the consult's derivation targets).

**Winner: 0.80.** The honest caveat, recorded rather than hidden: the
Wyze camera's auto-exposure normalizes to the panel (its panel median
sits at ~232 in all three frames), so the camera ratio understates
the real light reduction; the screencap proves the panel is now
emitting the 0.80 signal, in linear light roughly 60 percent of the
v0.3.0 plaster. Going below 0.80 bought nothing measurable in the
room and cost contrast, so the pass stopped there, inside the
three-scale budget.

## Build, install, dream proof

- Final APK: `Muse-Ambient-0.3.1.apk`, 1,476,196 bytes, versionCode
  7, versionName 0.3.1, SHA-256
  `268ab9d4c90b777965f60902bd1d30607dcd7b41b13f303ab096a0f22b96b86e`;
  badging confirms `provides-component:'dream'`; apksigner v2 true,
  v3 true. (Two same-version test builds preceded it during the A/B:
  the first 0.80 build, SHA `d36b68e0...`, differing only in a source
  comment, and the rejected 0.70 build, SHA `b9dd1796...`.)
- `install -r` Success over the tailnet lane; device read-back
  versionCode 7 / 0.3.1; `screensaver_components` unchanged.
- Real dream verified with the final binary for every A/B capture
  (`mCurrentDreamName` = AmbientDreamService, `mWakefulness=Dreaming`);
  `screen_off_timeout` restored to 300000 and re-read after each
  cycle, device left Awake.
- Frames: baseline `~/workspace/shield/wyze-tv-dashboard.png`;
  winner `~/workspace/shield/wyze-tv-dashboard-v031.png` (final
  binary, 16:55 EST); rejected 0.70
  `~/workspace/shield/wyze-tv-dashboard-v031-s070.png`; rendered-pixel
  proof `~/workspace/shield/muse-ambient-v031-dashboard.png`
  (Shield screencap at 0.80).
- The camera manual override flag
  (`~/homeassistant/config/camera_manual_override`) was left ON and
  the away-only gate was not modified; the frames were pulled through
  the HA loopback API (`GET /api/camera_proxy/camera.camera`).

## Deliberately left alone

The backdrop asset, every other design parameter (ink tiers, type
scale, layout, notification veil, dusk ramp curve, nocturnal
inversion), the Art face, and the camera gate. Still owed from
v0.3.0: the Brief rotation moment, a Now Playing frame, and the
nocturnal inversion capture.

## v0.3.2 addendum (2026-11-21) — unique Muse app icon + new TV banner

Icon-only release (versionCode 8, versionName 0.3.2). (1) Launcher icon is
now this app's own from the approved unique Muse app icon set
(`~/workspace/muse-app-logos/`): original Muse blue M on a light tile
plus a gold art badge, superseding the 2026-11-01 all-same-icon rule and
replacing the old vector star drawable as the launcher icon (manifest now
@mipmap/ic_launcher). Legacy mipmaps + round + adaptive icon (background
#F0F2F5, foreground at 76% centred). (2) New Android TV channel banner
(320x180, drawable + drawable-xhdpi banner.png, from the 640x360 master in
`~/workspace/muse-app-logos/banners/`). Verified: icon and banner
extracted from the built APK are pixel-identical to the approved
art/exports; apksigner verify passed in build. APK: Muse-Ambient-0.3.2.apk,
SHA-256 `9a6c5cc5944a72f12b7fa23c9be48df7bc6dd1f3846661d8da5d01f6249b0d79`. NOT installed: per this project's rule, never install
unattended (it changes what the living room TV shows); ready for a
deliberate install.

## v0.3.3 addendum (2026-11-21) — TV banner revision: no icon tile

Banner-only release (versionCode 9, versionName 0.3.3; manifest, Gradle
and build.sh aligned, Gradle had lagged at 0.3.1). At Jonathan's order
(2026-11-21 23:21 EST), the Android TV channel banner no longer shows
the app icon tile/board: the banner is a solid light background
(#F0F2F5) with the original Muse M and the gold art badge directly on
it, full app name in art, tagline just "Screensaver" (Jonathan's
follow-up order 2026-11-21 23:26 EST, shortened from "Art screensaver
for your TV"). banner.png (320x180, drawable + drawable-xhdpi) replaced
with the revised export from `~/workspace/muse-app-logos/banners/`
(banner-muse-ambient-320x180.png). Verified: banner extracted from the
built APK (both drawable entries) is pixel-identical to the new export
(0 differing px); apksigner verify passed. APK: Muse-Ambient-0.3.3.apk,
1,690,374 bytes, SHA-256
`e3d987ecd94fa67c810b56cd167429cdd03ae056dedc64560c72c28493b9ce6b`.
(The first 0.3.3 build, SHA-256 05d55aa9..., carried the pre-tagline-
change banner and was superseded the same night before any install.)
NOT installed: per this project's rule, never install unattended;
ready for a deliberate install.

**Installed 2026-11-21 23:36 EST** at Jonathan's direct order (overriding
this project's never-unattended rule for this one time): `adb install -r`
over v0.3.1 via the tailnet lane (127.0.0.1:5556). On-device read-back:
versionName 0.3.3 / versionCode 9; `screensaver_components` still
`com.jonathancruzate.museambient/.AmbientDreamService`. The installed
base.apk pulled back from the device is hash-identical to the staged APK
above, and its banner resources are pixel-identical (0 differing px) to
the revised no-tile export.

## v0.3.4 addendum (2026-11-21) — TV banner final layout: small logo + name on one line

Banner-only release (versionCode 10, versionName 0.3.4). From
Jonathan's photo of the live TV launcher (2026-11-21 23:37 EST), the
banner layout was finalized as: a SMALL logo (M + gold art badge) on
the left, the full app name "Muse Ambient" on the right on ONE line,
no tagline. banner.png (320x180, drawable + drawable-xhdpi) replaced
with the final export from `~/workspace/muse-app-logos/banners/`
(banner-muse-ambient-320x180.png). Verified: banner extracted from the
built APK (both drawable entries) is pixel-identical to the final
export (0 differing px); apksigner verify passed (v2 + v3); badging
shows versionCode 10 / 0.3.4 and `provides-component:'dream'`. APK:
Muse-Ambient-0.3.4.apk, 1,673,990 bytes, SHA-256
`854449a8cd4d4ef9e6562a5d1615b53bf8d2132a6880b8c7c7cb0fe17b65e086`.

Build circumstance, recorded honestly: the main source tree was
mid-v0.4.0 calendar work in another handling (DashboardView /
MuseFeed edits, 2026-11-22 04:40-04:44 UTC) and did not compile, so
0.3.4 was built in a side copy of the project with those two files
restored to their seeded v0.3.3 sources from this repo on GitHub and
the project's own debug keystore, so the signature matches the
installed lineage. The main tree's version files were left to the
v0.4.0 handling (they read 0.4.0 / versionCode 11).

**NOT installed — superseded on-device before it could land.** By the
time 0.3.4 was built, the Shield already ran Muse Ambient v0.4.0
(versionCode 11) from the parallel handling, so installing 0.3.4 would
have been a downgrade. No install was attempted. The final banner is
nonetheless live on the TV: the installed v0.4.0 base.apk was pulled
and both its banner entries are pixel-identical (0 differing px) to
the final export above (that build picked up the final banner from
this tree's res/), and `screensaver_components` still points at
`com.jonathancruzate.museambient/.AmbientDreamService` (read back
2026-11-21 23:52 EST).

## v0.4.0 addendum (2026-11-21) — the wall calendar, Dash-sourced

Version 0.4.0, **versionCode 11** (the brief said code 8; codes 8 and 9
were already taken by the v0.3.2 icon and v0.3.3 banner releases, code
10 was staged in the tree for an unbuilt 0.3.4, so 0.4.0 ships as 11).
APK: Muse-Ambient-0.4.0.apk, 1,673,990 bytes, SHA-256
`199da369505599f7efd8b4e302e95bf2a3c34c935302220a810114bdc2e8d214`,
debug-signed, apksigner v2/v3 true, badging shows
`provides-component:'dream'`. **Installed on the Shield** the same
night (`install -r` over v0.3.3; on-device read-back versionName 0.4.0 /
versionCode 11; `screensaver_components` unchanged).

**What it adds.** A month wall calendar on the dashboard's right side
(Jonathan: "Add a calendar to the right side so it takes up the empty
space like a virtual wall calendar"). Design by the Gemini calendar
consult (full text `~/workspace/shield/gemini-calendar-consult.md`):
block at panel x1920 y220, 1680x1240; Newsreader Light 104 month title
(no year); Source Sans Medium 44 tracked weekday headers over a single
2 px hairline; Newsreader Regular 68 day numerals, no cell gridlines;
row height 192 for 5-week months, 160 for 6-week; today in the accent
with a stepped weight over a 64x4 accent rule; event days carry up to
three 12 px dots (accent for today/future, ink 0.32 for past) and no
text under the legibility floors; adjacent-month padding cells are
bare plaster. The Muse note and Now Playing moved into a single-voice
footer register under the calendar (panel y1600): they never show
simultaneously, Now Playing preempts, and the note returns after a
20 s idle grace. Adaptations from the consult: the bundle has no
Newsreader Medium instance, so today's weight step is fakeBold on the
Regular instance; the month title's baseline compensates 44 px of
font-internal leading so its cap top lands exactly on the consult's
panel y248 (measured on device). During the Daily Brief rotation the
calendar fades out with the rest of the dynamic zones and returns
after.

**Data: Muse Dash is the calendar's source** (Jonathan's direction:
"I propose using muse dash as the screensaver feed source"). Dash's
own compiled data covers only today plus curated upcoming highlights,
not the full month, so the extension landed at the Dash compilation
layer as directed: `producer/compile_dash_calendar.py` now runs inside
every Muse Dash refresh (the `muse-dash-morning-refresh` cron body was
updated in place) and writes `producer/dash-calendar.json` from the
same `hatch_gws_cli +agenda --days 45` pull the Dash agenda uses,
curated (nightly Sleep block skipped, cross-calendar duplicates
collapsed, 4 events/day listed with the true total kept, all-day
flagged). The producer emits the feed's new `calendar` field from that
file only when its month is current and `compiledAt` is at most 36 h
old, carrying `compiledAt` through; the app overlays events only when
the field's month equals the device month, so a stale Dash degrades to
a plain grid. First compile: month 2026-11, 9 event days, 41 events.
`nextEvent` deliberately stays on the 15-minute calendar cache: Dash's
upcoming list is 3 curated highlights over 3 weeks, not the complete
agenda, and both lanes compile from the same Google Calendar, so the
TV and the Dash cannot disagree about the source of record. Weather is
not a feed field (the app reads it on-device), so Dash's basics
strings are not used. Schema: MUSE-FEED.md.

**Night check (owed since v0.3.x): verdict, the inversion works.**
Jonathan's 23:31 photo showed the panel bright; the framebuffer says
otherwise. Live dream captures on v0.3.3 (23:47-23:52 EST) measured
median RGB (32, 29, 26) in every sampled region: the deep warm
charcoal, exactly as designed; the photo's brightness is the phone
camera's auto-exposure lifting a dim panel in a dark room. This
release also adopts the consult's FALD suggestion and deepens the
night overlay from #1B1816 to #100E0D; the v0.4.0 night capture
measures (21, 20, 18). At night the calendar persists whispered
(parchment tiers, past dots hidden) while the house mode is Home; it
hides only on explicit house Night mode.

**Verification.** Dream triggered via the SYSTEM timeout pattern on
the installed build; `dumpsys dreams` names AmbientDreamService and
`mWakefulness=Dreaming`. The night capture
(`~/workspace/shield/muse-ambient-v040-night.png`) shows the real
November 2026 grid (5 weeks, starts Sunday the 1st), today the 21st in
the night accent with its rule, and dot counts matching the live
Dash-compiled feed exactly (22-28 capped at 3, 29 with 1, 30 with 3),
which also proves the feed URL pref survived `install -r` and the
whole compile-to-grid pipeline works end to end. The bundled sample
carries no calendar field, so the no-field path renders the plain
grid by construction. Art mode is unregressed: `AmbientView.java` is
byte-identical to the v0.3.3 build; this release touches only
`DashboardView.java` and `MuseFeed.java` (additive parse). Two gaps,
named: (1) the DAY palette of the calendar was not captured live,
because the Shield refuses `adb shell date` ("Operation not permitted")
and the house was in the night window; the geometry is
palette-independent and was verified at night, the day tiers are the
face's established ink/accent constants, and a day capture is a
two-minute morning follow-up (timeout pattern after 06:30). (2) The
Wyze in-room frame is unusable tonight: the camera's live view is
filled by fabric (it no longer frames the TV; frame timestamp current,
stream healthy), so in-room judgment rests on the framebuffer
captures.

## v0.4.1 addendum (2026-11-22) — TV banner adopts the Google TV template packaging

Banner-packaging-only release (versionCode 12) at Jonathan's order
("Adopt", 2026-11-22 00:15 EST), following the gap analysis against the
official Google TV Figma template (@tv, "TV App Icon Template
[Android TV & Google TV]"). The banner art is unchanged (small logo
left, "Muse Ambient" on one line); the resource structure is now the
template's:

- Legacy ladder: `banner.png` in drawable-mdpi (160x90), drawable-hdpi
  (240x135), drawable-xhdpi (320x180), drawable-xxhdpi (480x270) and
  drawable-xxxhdpi (640x360); the unqualified drawable copy is kept.
- Adaptive set: `drawable-anydpi-v26/banner.xml` (`<adaptive-icon>`
  with `@drawable/banner_background`, solid #F0F2F5, and
  `@drawable/banner_foreground`, logo + name on transparent, both in
  drawable-xxxhdpi). minSdk 26, so the adaptive banner resolves on the
  Shield (API 30); flat PNGs remain the fallback.

Gate check: the v0.4.0 tree as found compiled cleanly (full build.sh
run passed before the bump), so the bump proceeded in-tree. Only
banner res files, the version fields (manifest + build.gradle) and
build.sh's output APK name were touched; no in-progress source files
were edited.

Verification: every banner entry extracted from the built APK is
pixel-identical to its `~/workspace/muse-app-logos/banners/` export
(0 differing px, all sizes + both layers); aapt2 badging shows the
application banner resolving to `res/drawable-anydpi-v26/banner.xml`
and `provides-component:'dream'`; apksigner v2 + v3. APK:
Muse-Ambient-0.4.1.apk, 1,756,485 bytes, SHA-256
`24c3b4e68aaf4685f8767679f8ad160add74eb59c2d4cce1a52319ea1de36884`.

**Installed 2026-11-22**: `install -r` over v0.4.0 via the tailnet
lane. On-device read-back: versionName 0.4.1 / versionCode 12;
`screensaver_components` still points at
`com.jonathancruzate.museambient/.AmbientDreamService`; the installed
base.apk pulled back is hash-identical to the staged APK.
