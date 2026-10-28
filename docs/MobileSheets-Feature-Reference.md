# MobileSheets / MobileSheetsPro — Complete Feature Reference

A feature-by-feature analysis of the commercial **MobileSheets** app (by Zubersoft),
intended as a target/spec reference for BasicSheets. For every feature it states
**what it does** and **how it does it** (the user-facing mechanism, since the app is
closed-source).

> **Scope & accuracy note.** This is compiled from Zubersoft's official site/feature
> pages, the official manual, and the support forum (sources at the bottom). MobileSheets
> ships on Android, iOS/macOS and Windows and the apps differ slightly in wording and in
> a few capabilities; exact menu paths and option names also drift between versions.
> Treat specific UI paths as "approximately where this lives," not a guaranteed label.

---

## Table of contents

1. [Platforms, editions & licensing](#1-platforms-editions--licensing)
2. [Library & song management](#2-library--song-management)
3. [Importing & file handling](#3-importing--file-handling)
4. [The song editor & metadata](#4-the-song-editor--metadata)
5. [Display & view modes](#5-display--view-modes)
6. [Scaling, zoom, rotation, cropping & page order](#6-scaling-zoom-rotation-cropping--page-order)
7. [Page turning & hands-free control](#7-page-turning--hands-free-control)
8. [Annotations & the annotation editor](#8-annotations--the-annotation-editor)
9. [Bookmarks](#9-bookmarks)
10. [Link points](#10-link-points)
11. [Setlists](#11-setlists)
12. [Collections & groups](#12-collections--groups)
13. [Audio player](#13-audio-player)
14. [Metronome](#14-metronome)
15. [Smart buttons, the action box & performance mode](#15-smart-buttons-the-action-box--performance-mode)
16. [MIDI](#16-midi)
17. [ChordPro & text songs (chords, transposition)](#17-chordpro--text-songs-chords-transposition)
18. [Collaboration (leader/follower, book mode)](#18-collaboration-leaderfollower-book-mode)
19. [Library sync](#19-library-sync)
20. [Sharing, export & printing](#20-sharing-export--printing)
21. [Backup & restore](#21-backup--restore)
22. [Companion app](#22-companion-app)
23. [Settings & customization (summary)](#23-settings--customization-summary)
24. [Sources](#24-sources)

---

## 1. Platforms, editions & licensing

**What it does.** Runs as a sheet-music library/reader on tablets and desktops, with a
free tier and a paid tier.

**How it works.**
- Native apps for **Android, iOS, macOS and Windows**, sharing a common interface so a
  mixed-platform ensemble can collaborate. There is a dedicated **e-Ink build** for
  Android e-ink devices (e.g. BOOX tablets).
- Optimized for **7"+ tablets**; phones work but the UI is tablet-first.
- **Free version** has limited functionality (notably, library **sync is disabled**).
  The paid app is a **one-time purchase / perpetual license**. The Zubersoft (FastSpring)
  store uses **one license key per device**.
- Ships with **no sheet music** — the user supplies their own PDFs/images/text.

---

## 2. Library & song management

**What it does.** Replaces paper binders with a searchable, sortable catalog of "songs"
(a song = one or more score files plus metadata), designed to stay fast as the library
grows into the thousands.

**How it works.**
- The library is a **SQLite database**; file data and metadata are **cached** so
  browsing, searching and editing feel instant and scores open in seconds. (Trade-off:
  app startup time grows slowly with library size.)
- The **library screen** lists songs and offers tabbed/alternate views that pivot the
  same data by field — e.g. Songs, Artists, Albums, Genres, Composers, Collections,
  Setlists, Bookmarks. Picking an entry in a pivot filters the song list to it.
- **Filtering:** a search field plus configurable filters by group/field and by rating
  narrow the list live as you type. An **alphabet index** down the side jumps to a
  letter; **long-pressing a letter** lets you "spell" a title to jump straight to it.
  **Voice search** is supported.
- **Sorting:** ~**10 sort algorithms** (title, artist, album, etc.), with configurable
  display formatting of each row.
- **Multiple libraries:** you can keep separate libraries (e.g. per band or event) and
  switch between them in a few taps.

---

## 3. Importing & file handling

**What it does.** Gets external files into the library and turns them into songs,
without forcing you to duplicate files you already have organized on disk.

**How it works.**
- **Supported score formats:** PDF, image files (JPG/PNG/etc.), and **text / ChordPro**
  chord-lyric files. Audio files can be attached to songs (see Audio player). The app
  renders these formats; it is not a notation engine (no MusicXML engraving).
- **Import sources:** the device's **standard file picker**, plus a **built-in cloud
  browser** for **Dropbox, Google Drive and OneDrive**; other services go through the
  OS picker. The desktop **Companion app** can also push songs to a device.
- **Batch import** brings in many files at once. A single large PDF can be **split into
  several songs by page ranges**, and conversely a song can be built from **multiple
  files** (e.g. different instrument parts, or image + PDF), which you switch between
  while viewing.
- **Link vs. copy:** MobileSheets can **reference files in an existing folder on the
  device without copying** them into its own storage, so your external folder structure
  stays authoritative.

---

## 4. The song editor & metadata

**What it does.** Stores rich metadata per song for searching/filtering, and holds the
per-song configuration for display, audio, MIDI, metronome, etc.

**How it works.**
- **20+ metadata fields** are available; you enable only the ones you use. They include
  title, **artists, albums, composers, genres, collections**, keys, signatures,
  difficulty, duration, year, rating, source type, and a **renamable custom field**.
  Many fields are multi-valued (a song can carry several genres/collections/artists).
- The **song editor** is tabbed: separate pages for metadata/fields, **files** (add,
  reorder, set page ranges, per-file type), **display** settings, **audio**, **MIDI**,
  **metronome**, **bookmarks/link points**, etc. — i.e. most per-song behavior described
  elsewhere in this document is configured here.
- ChordPro files can **auto-populate metadata** from their directives (e.g. `{artist}`,
  `{tempo}`) when imported.

---

## 5. Display & view modes

**What it does.** Controls how pages are laid out on screen so the music is as large and
readable as possible for the situation (portrait tablet, landscape, continuous reading).

**How it works.** Display mode is chosen in a **display-mode dialog** and can be set
**per song** (with a global default):
- **Single page** — one page at a time, turning **horizontally**. In **landscape**, a
  single page is shown by default as **two enlarged halves** (top half, then bottom
  half) so portrait scores fill a landscape screen.
- **Two pages** — a left/right spread side by side (good on wide screens).
- **Half-page turning** — advances **half a page at a time** so the next music scrolls
  into view before you run out of the current system (three ways to get it: vertical-
  scroll mode with the half-page option on, the dedicated half-page display mode, or
  single-page landscape with half-page turns enabled).
- **Vertical scrolling** — pages stack and scroll continuously; pairs well with
  width-fitting in landscape for large notation.
- **Horizontal scrolling** — pages flow left/right; scroll and page-turn happen in the
  same mode.
- **Day / night / custom colors** — light or dark rendering, plus custom background
  color options.
- **Song overlay** — tapping the **center** of the page shows a transient overlay with
  shortcuts to other features; tapping again hides it. It never blocks the score unless
  opened.
- **Image caching** — up to **10 pages** are held in memory for instant turns; the cache
  shrinks automatically under memory pressure.

---

## 6. Scaling, zoom, rotation, cropping & page order

**What it does.** Makes each page fit and look right regardless of the source file's
margins, orientation or resolution.

**How it works.**
- **Page scaling / fit:** choose how pages stretch to fill the screen — e.g. stretch to
  the tablet's **width**, or a **full-screen** option that eliminates unused space.
  Pinch gestures zoom/pan within a page.
- **Rotation:** rotate an **individual page** or an **entire score** in 90° steps (for
  landscape scans or sideways pages).
- **Cropping / margins:**
  - **Automatic cropping** detects and removes white margins without altering the file.
  - **Batch cropping** applies crop to **every page** of a file at once.
  - A manual **cropping editor** lets you drag crop boundaries per page.
- **Page order & duplication (per file):**
  - **Reorder, duplicate, and remove** pages.
  - **Duplicating** a page is the standard trick for repeats (e.g. *D.S. al coda*): copy
    the pages so you only ever **turn forward** through a performance.
  - Each **duplicate is independent** — it can carry its own annotations, cropping,
    bookmarks and link points.
  - **Trimming** extracts just the pages you need from a large file.

---

## 7. Page turning & hands-free control

**What it does.** Lets a performer advance the music without lifting their hands from the
instrument, and lets a page "turn" mean more than +1 page (jumps, repeats, scrolls).

**How it works.**
- **Touch:** tap **screen-edge zones** (configurable) or **swipe** to go next/previous.
- **Foot pedals:** Bluetooth/USB pedals (AirTurn, PageFlip, generic BT foot switches) are
  supported — **up to six pedal switches**, each mapped to its own action under
  **Settings → Touch & Pedal Settings → Pedal Actions**. Actions include next/previous
  page, **scroll a set increment** (e.g. 30% per press) or **"scroll down or turn page at
  bottom"** (combines scrolling with turning), activate the **next link point** or a
  **smart button**, and start/stop audio/metronome/auto-scroll. The same action set is
  assignable to the edge-tap zones and to incoming MIDI.
- **Auto-scroll:** per-song scrolling with several **modes** — scroll part of a page then
  pause, scroll-and-pause after each page turn, scroll continuously to the end, or scroll
  the whole song over a **fixed total time**. Works best with vertical scrolling and can
  be started/stopped by touch, pedal or MIDI.
- **Link points** (see §10) let a single turn **jump** to a non-adjacent page to handle
  repeats and codas.
- **Face gestures (iOS):** hands-free turns via facial gestures such as opening the mouth
  or smiling.
- **MIDI:** incoming MIDI messages can also drive turns/actions (see §16).

---

## 8. Annotations & the annotation editor

**What it does.** Lets you mark up any score (PDF, image or text) with drawings, text and
musical symbols that are saved with the song.

**How it works.**
- **Entering/exiting the editor:** via the overlay/menu, or gestures — a **three-finger
  tap** enters/exits the editor and a **two-finger tap** switches tools. With a stylus,
  bringing the pen near the screen can auto-enter the editor, which can **auto-exit after
  a few seconds** of inactivity; a **stylus side-button** can be assigned a tool shortcut.
- **Tools:**
  - **Pen / freeform** drawing, with optional **stylus pressure sensitivity** for natural
    stroke width.
  - **Highlighter**.
  - **Eraser**, with modes — **"Drag Across"** removes only what the eraser touches,
    **"Drag box"** removes a selected region. The **"Combine freeform annotations"**
    setting controls whether consecutive strokes are one object or independent ones.
  - **Shapes** and **arrows**.
  - **Text** tool — resizable text boxes; with autosize on, resizing changes the **font
    size**; **line spacing** is adjustable.
  - **Music tools** — e.g. **crescendo/decrescendo** hairpins and a **piano-staff** tool.
  - **Nudge** tool for fine, pixel-level repositioning.
- **Stamps:** a large built-in **vector stamp library** (dynamics, articulations, repeats,
  etc.) that scales cleanly at any zoom, plus **custom stamps** you can add. Stamp
  **color and opacity** are adjustable to make a mark stand out.
- **Color / size / opacity** are set per tool.
- **Layers:** annotations can be placed on **separate layers**, each of which can be
  **shown or hidden** independently.
- **Favorites:** frequently used tool configurations are saved in a **favorites window**
  and recalled instantly.
- **Cut / copy / paste:** you can cut or copy part of a PDF page and paste it elsewhere in
  the same PDF.
- **Undo / redo** while editing; changes **save automatically** (so an accidental erase
  is committed immediately — a known gotcha).
- **Scope caveat:** annotations attach to the **song**, not to a setlist, so a song's
  markings appear in every setlist that contains it.
- **Sharing:** annotations can be **baked into an exported PDF** for people who don't use
  MobileSheets (see §20).

---

## 9. Bookmarks

**What it does.** Marks named positions inside scores and provides a library-wide index
of them.

**How it works.**
- A **bookmark** points at a page within a song and can be named.
- The **bookmarks window** for a song shows both **MobileSheets bookmarks** and any
  **bookmarks embedded in the PDF** itself.
- A dedicated **Bookmarks tab** on the library screen lists **every bookmark across the
  whole library** at once, so a bookmark can act like a mini-song you can jump to
  directly (useful for a single tune inside a large fake-book PDF).

---

## 10. Link points

**What it does.** Creates tappable "jump" targets on a page so a performance can navigate
non-sequentially (repeats, D.S., coda) with one action.

**How it works.**
- You place a **start** and **end** link point; triggering the start **jumps** the view
  to the end location (which may be on another page).
- The **end** link point briefly **lights up and fades** to draw the eye to the next
  measure to play.
- Link points can be triggered by **pedals**, so one pedal mapping handles both ordinary
  page turns and repeats/jumps.

---

## 11. Setlists

**What it does.** Groups songs into an **ordered** performance list you can page through
end-to-end as if it were one long document.

**How it works.**
- Build a setlist by adding songs in order; **page turning flows continuously** from the
  last page of one song into the first of the next.
- A **setlist editor** adds, removes and **reorders** songs; setlists can be **created,
  merged and deleted**.
- **Selection mode:** long-press a setlist to select it, then act on it (e.g. print or
  share).
- Setlists can be **shared** — and when two users share the same library, a setlist can
  be shared **without resending the PDFs** (just the ordering/reference).

---

## 12. Collections & groups

**What it does.** Two complementary ways to organize songs beyond setlists — tag-style
categories (collections) and curated, reorderable lists (groups).

**How it works.**
- **Collections** behave like a multi-valued **tag field** on a song (similar to genres):
  a song can belong to several collections, and the library screen offers a **Collections
  pivot/filter**. Collections are purely organizational — they do **not** change where
  files are stored, and filters can be **combined** (e.g. a collection AND a genre).
- **Groups** are edited in a dedicated **group editor** where you **add, remove and
  reorder** the songs in each group; filters from the main library screen carry over into
  the editor. (The key difference from a setlist is intent: groups organize the library;
  setlists are the ordered thing you actually perform.)

---

## 13. Audio player

**What it does.** Plays backing tracks or practice recordings attached to a score, in
time with reading.

**How it works.**
- Attach **one or more audio tracks** to a song (for practice or as backing tracks).
- Set **loop points** to repeat a section (A–B style looping).
- **Change tempo** and **shift pitch** up or down independently (practice slow without
  detuning, or match a singer's key).
- The player UI can be shown in **multiple sizes** and is reachable from the action box.

---

## 14. Metronome

**What it does.** Provides audible and/or visual tempo, and can drive the page while you
practice.

**How it works.**
- **Count-in** before playback; start/stop controls.
- **Sound options:** change the beat sound and **accent the first beat** of each measure.
- **Auto page turn:** advance the page automatically after a set **number of measures**.
- **Silent / visual-only** operation with selectable **display modes:** **LED** beats, a
  **circle that flashes and fades**, or a **rectangle that lights the screen edges**.
- The tempo can be **set per song** and can **sync over MIDI** (see §16).

---

## 15. Smart buttons, the action box & performance mode

**What it does.** Puts configurable on-screen controls and quick shortcuts where a
performer needs them, and a mode that prevents accidental taps during a gig.

**How it works.**
- **Smart buttons:** tappable buttons placed **on the score** at a chosen spot. Each runs
  one or more actions: **send MIDI** commands, **play an audio track from a set
  position**, **open a URL** in a browser, or **load a file in another app**. They can
  also fire at points **within** a song, not only at its start.
- **Action box:** tapping the **bottom-right corner** opens a small box with shortcuts to
  **performance mode, the metronome, the audio player and auto-scroll**. Its rightmost
  icon is configurable (show the **setlist**, **bookmarks**, or another window), and the
  box can be **moved to any corner or hidden**.
- **Performance mode:** disables the center-tap **song overlay** so you can't accidentally
  trigger features mid-performance.

---

## 16. MIDI

**What it does.** Integrates MobileSheets with keyboards/controllers so that selecting a
score and selecting an instrument sound stay in sync, and so hardware can drive the app
(and vice-versa).

**How it works.**
- **Load a score from MIDI:** a song can be set to **load when specific incoming MIDI
  commands** arrive; giving several songs the **same** command lets repeated presses
  **cycle** through them. Loading can be restricted to a **specific channel**.
- **Send MIDI on display:** a song can **send a list of MIDI commands when it opens**
  (e.g. to set a keyboard's patch/registration). Commands can be sent on specific
  channels.
- **Command types:** patch select, **control change**, **program change**, and
  **system-exclusive (SysEx)**; multiple commands can be chained with **pauses** between
  them for complex rigs. SysEx can carry **registration** data.
- **Trigger actions from MIDI:** incoming messages can start audio, start auto-scroll,
  switch scores, turn pages, etc. (same action set as pedals/tap zones).
- **Keyboard registration linking:** dedicated support to pair scores with keyboard
  registrations so loading one loads the other — e.g. **KORG** (`Pa4X/Pa5X/Pa700` via a
  KORG-number command) and **Yamaha Genos / SX600-900** (set up with a button on the MIDI
  page of the song editor).
- **Connections:** over **USB and Bluetooth** (plus **Wi-Fi MIDI on iOS**), with **port
  filtering** to limit which MIDI ports send/receive.
- **Tempo sync:** the metronome tempo can sync with external MIDI devices/apps (e.g.
  Soundbrenner) and **changes automatically** when a different score loads.

---

## 17. ChordPro & text songs (chords, transposition)

**What it does.** Treats chord/lyric files as *live* documents that reflow and transpose,
rather than fixed images.

**How it works.**
- **ChordPro** (`.cho`/`.pro`/`.crd`/`.chordpro`) is the reliable format: its syntax
  marks chords explicitly, so MobileSheets can **transpose** on the fly, keep **chords
  positioned above the lyrics** when the file is resized/reflowed, and honor structural
  and formatting directives — **section labels, repeating choruses, mid-file formatting
  changes**, `{image}`, `{chord}`, `{define}` (custom **chord diagrams**), and **font
  directives** (family/size/color for chords, chorus, footers, etc.).
- **Plain text** files also display, but the app must **guess** which lines are chords vs.
  lyrics; lines that mix the two, or a chord line carrying a repeat marker like `x4`, can
  fail to transpose. ChordPro avoids this.
- **Metadata directives** (`{title}`, `{artist}`, `{tempo}`, …) can populate the song's
  library fields on import.
- **Editing:** there is a **basic built-in editor** for small fixes; many users author in
  external tools (ChordSmith, SongPress) for preview/transposition.
- **Limitation:** chords inside **PDFs are images** and **can't be transposed** — the song
  must be in ChordPro/text to transpose.

---

## 18. Collaboration (leader/follower, book mode)

**What it does.** Keeps a group of players' tablets in sync during rehearsal/performance.

**How it works.**
- **Leader / follower:** one tablet is the **leader** and controls the **followers** —
  when the leader loads a score or setlist, every follower loads the same one.
  Connections run over **Wi-Fi** (no stated device limit) or **Bluetooth** (up to **seven**
  tablets). You can choose whether **page turns are synchronized** across all devices or
  whether each musician turns their **own** pages.
- **Book mode:** two tablets can be run as a **two-page spread** (like an open book) for a
  larger continuous reading surface.

---

## 19. Library sync

**What it does.** Keeps the libraries on several devices up to date while **preserving
each musician's personal data** (so syncing doesn't wipe your annotations).

**How it works.**
- Sync runs over **Wi-Fi** or through a **cloud folder**.
- You **choose which fields to synchronize**: new scores and shared library updates can
  propagate to everyone, while per-person fields such as **annotations, settings and
  metadata stay local** to each device.
- **Not in the free version.**
- **Caveat:** syncing *library files* by pointing MobileSheets at a cloud-sync folder
  (Google Drive/OneDrive doing the file copying) can cause **duplicate files or database
  issues** — the built-in sync is the supported path.

---

## 20. Sharing, export & printing

**What it does.** Gets songs/setlists out of the app to other people or to paper.

**How it works.**
- **Single-file export:** export a score or a whole setlist as **one file** to hand off.
- **PDF export with annotations:** export a score as a **PDF with annotations baked in**,
  so recipients who don't use MobileSheets can still see your markings.
- **Setlist sharing without PDFs:** when users share the **same library**, a setlist can
  be shared as just its ordering/references (no re-sending the score files).
- **Printing:** songs/setlists can be **printed** (reachable from setlist selection mode
  and elsewhere).

---

## 21. Backup & restore

**What it does.** Captures the entire library — files *and* database — into one portable
backup that can be restored on the same or another device.

**How it works.**
- Produces a **single backup file** (`.msb`) containing the songs **and all associated
  data: annotations, bookmarks, link points, metadata and settings**. Restoring rebuilds
  the library exactly, including annotations/bookmarks/link points.
- A **backup verification** step checks the archive's integrity.
- The **Companion app** can create the backup on a PC when the tablet itself lacks the
  free space for a backup file.

---

## 22. Companion app

**What it does.** A free **desktop (PC/Mac)** application for managing a MobileSheets
library with a mouse and keyboard instead of on the tablet.

**How it works.**
- **Create and edit songs** (metadata, files, page ranges) on the computer, then transfer
  them to the device.
- Acts as a **transfer channel** and a place to **back up/restore** the library (useful
  for bulk edits and for tablets short on storage).

---

## 23. Settings & customization (summary)

**What it does.** Exposes the per-app and per-song behavior that the features above rely
on.

**How it works (notable areas).**
- **Touch & Pedal Settings** — map edge-tap **zones** and **pedal** switches to actions;
  set **scroll increment** per press.
- **Display defaults** — default view mode, scaling, day/night, caching behavior.
- **Annotation settings** — pen smoothing, "Combine freeform annotations," stylus
  auto-edit and auto-exit timing, stylus-button tool.
- **MIDI settings** — port filtering, channels, tempo-sync source.
- **Gestures** — assign/disable two-/three-finger gestures; (iOS) face gestures.
- **Library settings** — which metadata fields are active, sort/format, multiple-library
  switching.
- Most **per-song** behavior (display, audio, MIDI, metronome, auto-scroll) lives on the
  relevant tab of the **song editor** and overrides the global default.

---

## 24. Sources

- MobileSheets — Features overview: https://www.zubersoft.com/mobilesheets/features
- MobileSheets — Display features: https://www.zubersoft.com/mobilesheets/features/display
- MobileSheets — Utilities features: https://www.zubersoft.com/mobilesheets/features/utilities
- MobileSheets — MIDI features: https://www.zubersoft.com/mobilesheets/features/midi
- MobileSheets — Collaboration features: https://www.zubersoft.com/mobilesheets/features/collaboration
- MobileSheets — Annotations features: https://www.zubersoft.com/mobilesheets/features/annotations
- Full manuals (PDF, per platform): https://www.zubersoft.com/mobilesheets/manual
- FAQ: https://www.zubersoft.com/mobilesheets/faq
- Support forum (feature/behavior details, e.g. pedal actions, eraser modes, ChordPro, sync caveats): https://www.zubersoft.com/mobilesheets/forum
