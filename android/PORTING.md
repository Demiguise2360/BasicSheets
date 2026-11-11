# BasicSheets — native Android (Kotlin) rewrite

A ground-up rewrite of the .NET MAUI Blazor app as a **native Kotlin / Jetpack Compose**
Android app. Goal: **keep every user-facing feature identical**, but on a cleaner native stack.

> **Status: foundation only.** This is the start of a large, multi-stage port. What is here now is
> the project skeleton + data layer. The viewer, annotations, audio, etc. are still to come — see
> the roadmap below. The old MAUI app under the repo root is untouched and remains the reference.

## Important: how this gets verified

The person driving this works on a Windows/.NET machine without the Android SDK, so **the Android
app cannot be built or run in that chat environment** — every stage has to be compiled and tested
by **you, in Android Studio**, and issues fed back. Treat the code here as un-compiled until you
build it. The first `assembleDebug` will almost certainly surface version/import nits to fix.

## Opening & building

1. Open the `android/` folder in **Android Studio** (Ladybug / Koala or newer). Let it sync Gradle
   — it downloads Gradle 8.9 (from `gradle/wrapper/gradle-wrapper.properties`) and generates the
   wrapper automatically.
2. Build & run on a device/emulator (`Run app`), or from a terminal once Gradle is installed:
   `gradle wrapper` then `./gradlew assembleDebug`.
3. Release signing: copy your keystore details into `android/signing.local.properties` (git-ignored):
   ```
   storeFile=/absolute/path/basicsheets-release.keystore
   storePassword=...
   keyAlias=basicsheets
   keyPassword=...
   ```
   Then `./gradlew assembleRelease`. The `applicationId` is `ch.desgin.basicsheets` (same as the MAUI
   app) so an install updates it in place.

## Architecture

| Concern            | MAUI app                              | Native rewrite                                   |
|--------------------|---------------------------------------|--------------------------------------------------|
| UI shell           | Blazor (Razor) in a WebView           | **Jetpack Compose** (Material 3)                 |
| PDF viewer/canvas  | `pdfViewer.js` (pdf.js + HTML canvas) | custom **Android `View`** + `PdfRenderer`        |
| Data / library     | sqlite-net SQLite                     | **Room** (`ch.desgin.basicsheets.data`)          |
| Annotations        | JSON blobs in WebView `localStorage`  | `annotations` Room table (same JSON shape)       |
| Backing-track audio| HTML `<audio>`                        | **Media3 / ExoPlayer** (speed + pitch)           |
| Metronome / tuning | Web Audio (oscillator scheduler)      | **AudioTrack** (generated PCM click / sine)      |
| In-doc search      | pdf.js text layer                     | PDFBox-Android text extraction (search phase)    |

Min SDK 24, target/compile SDK 35. Package `ch.desgin.basicsheets`.

## Done so far (foundation)

- Gradle project (Compose, Room+KSP, Media3, Navigation), manifest, theme, launcher icon.
- **Room data layer** faithfully mirroring the MAUI models: `sheets`, `setlists`, `setlist_items`,
  `bookmarks`, `viewer_buttons` (link points), `collections`, `sheet_collections`, `sheet_files`,
  `crops`, `settings_kv`, plus a new `annotations` table (replaces the WebView `localStorage` blobs,
  same JSON shape). DAOs for each.
- App skeleton: `MainActivity` → Compose, placeholder library screen wired to Room.

## Roadmap (each stage is compiled + tested in Android Studio before moving on)

1. **Data layer** ✅ (entities + DAOs + database; repositories next).
2. **Library screen** — list, search, sort (name/composer/date/difficulty), filters, favorites,
   PDF import (SAF file picker → copy into `sheetsDir` → create `Sheet`), song editor/metadata,
   page-split into songs.
3. **PDF viewer core** — `PdfRenderer` page→bitmap with a page cache, pinch-zoom/pan, fit modes,
   tap-zone + swipe page turns, display-mode switch, keep-awake.
4. **Annotations** — pen/marker/stamp/text/hairpin/shape/eraser (+ box eraser), pressure &
   palm rejection via `MotionEvent` (stylus), undo/redo, per-page JSON persistence, backup/restore.
5. **Display modes** — single, two-page, **half-page** (the composite flow: next-page top on top,
   current-page bottom below, faint fold bar on whole pages, draggable solid bar), vertical &
   horizontal scroll with tiling for long docs.
6. **Metronome** (accent, subdivisions, beats, tap tempo, mute, auto page-turn), **audio player**
   (A/B loop, speed with pitch preserved), **tuning tone** (sustained sine, ± a semitone).
7. **Setlists** (continuous cross-song paging), **bookmarks**, **link points**, PDF outline,
   **in-document search** (needs the PDFBox dependency).
8. **Settings** (all the current toggles/selects), **gestures** (multi-finger, long-press, pedal
   keys), **night/warm/dim** rendering, **haptics**, auto-scroll, auto page-turn by time.
9. **Polish** — splash, app icon, crop editor, page manager (reorder/duplicate/hide), share
   page-as-image, screen-dim overlay.

## Open questions / decisions for you

- **Existing library data.** This starts with a fresh database. Your current songs, annotations,
  setlists and bookmarks live inside the MAUI app's private storage on your device and are **not**
  carried over automatically. If you want them migrated, that's a separate step (export from the
  device, import here) — tell me and I'll build an importer. Otherwise you re-import your PDFs.
- **In-document search** needs a text-extraction library (PDFBox-Android, ~a few MB). The dependency
  line is in `app/build.gradle.kts`, commented out until that phase.
- **Feature parity is iterative.** The hardest part is the viewer/annotation engine (~4,000 lines of
  canvas code in the MAUI app). Expect to build + test each stage and report what's off; exact
  behaviour (stylus pressure curves, gesture thresholds, the half-page flow) gets tuned against the
  real app on a device.
