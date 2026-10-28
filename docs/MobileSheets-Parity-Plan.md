# BasicSheets → MobileSheets Parity Plan

Goal (from the user): make BasicSheets do everything MobileSheets does, with the same
functionality **and** the same design; where a feature already exists in BasicSheets,
adopt the MobileSheets behavior **and** keep BasicSheets' extra functionality on top.

This document is the roadmap for that. Pair it with
[MobileSheets-Feature-Reference.md](MobileSheets-Feature-Reference.md) (what each
MobileSheets feature does and how).

> **Reality check.** Full MobileSheets parity is a large, multi-month effort and several
> areas (cloud sync, MIDI, Bluetooth/USB foot pedals, device-to-device collaboration, a
> desktop companion app) involve platform-specific/native work that is non-trivial in a
> .NET MAUI Blazor app. This plan is therefore **phased**: each phase is independently
> shippable and we check in between phases.
>
> **Assets/IP.** We reproduce *layout and functionality* and a *similar visual language*;
> we do **not** copy MobileSheets' proprietary icons, artwork or branding — we use
> BasicSheets' own icons or new ones.

---

## 1. BasicSheets today (snapshot)

**Architecture.** .NET 9 MAUI Blazor, shared RCL (`BasicSheets.Shared`) hosting all
pages/services; Web host mirrors it. PDF rendering + annotations in `pdfViewer.js`
(~2,000 lines, pdfjs 3.11). **Storage = flat JSON files** (`sheets.json`,
`settings.json`, `bookmarks.json`, `viewer-buttons.json`). **Single library.**

**What exists:**
- **Library (Home):** card list (generic icon, not page thumbnails), live search + field
  filter (name/composer/part/genre/tags), sort (name/composer/date/difficulty),
  edit/delete, in-app update banner.
- **Song model:** one PDF per song; flat **single-valued** metadata — Name, Composer,
  Part (instrument), Genre, Year, Difficulty, Tags (CSV), Remarks, per-song PageMode.
- **Import:** single **PDF only** (≤30 MB) via OS file picker; name auto-filled.
- **Viewer:** display modes single / two-page / half-turn / vertical-scroll; fit
  width/screen/height/full; pinch-zoom, pan, inertia; slide/crossfade page transitions;
  page slider; auto-hide chrome; last-page memory; keep-awake; **timed** auto page-turn.
- **Annotations:** pen, highlighter (marker), eraser, text, move, music **stamps**
  (dynamics + symbols), crescendo/decrescendo hairpins, 6 colors, size, undo/redo/clear;
  stylus pressure; stamp picker.
- **Bookmarks:** own per-page bookmarks + global Bookmarks tab; long-press to add.
- **Viewer buttons:** buttons placed on a page with actions (jump / first / last /
  bookmark / go-home / toggle-edit) and visual jump-target markers — a BasicSheets
  take on link points + simple smart buttons.
- **Search page**, **Settings** (page mode, dark mode, hints, auto-hide, transitions,
  keep-awake, auto-turn, viewer bg, page numbers, pen width/color, default tool, remember
  page, sort, 5 configurable gestures, stylus auto-edit, stylus side-button).
- **Design:** clean teal/green card UI, bottom tab bar → left sidebar ≥1100px, light/dark,
  German UI.

## 2. BasicSheets extras to PRESERVE (don't lose these)

- Difficulty as a first-class field with colored badges.
- Fully **configurable multi-finger gesture system** (swipe-from-bottom, long-hold,
  2-finger double-tap, 3-finger left/right → assignable actions).
- **Stylus auto-edit** (pen proximity enters edit mode) + side-button → eraser.
- **Floating, draggable, collapsible** edit tool palette.
- Crescendo/decrescendo **hairpin** annotation tools.
- **In-app auto-update** service.
- The clean light/dark design system (at minimum as a selectable theme).

---

## 3. Gap analysis & phased roadmap

Legend: ✅ have · 🟡 partial · ❌ missing.

### Phase 0 — Foundation: data model & storage
*Everything below depends on this. Do first.*
| MobileSheets | BasicSheets | Work |
|---|---|---|
| SQLite library, scales to thousands | ❌ flat JSON | Move to **SQLite** (e.g. `sqlite-net`); migrate existing `sheets.json`. |
| Song = multiple files/parts, page ranges | ❌ 1 PDF/song | Song→many Files; per-file page ranges; file switcher. |
| 20+ multi-valued fields (artists, albums, composers, genres, collections, keys, signature, rating, duration, custom) | 🟡 flat single strings | Relational metadata tables; multi-valued; keep Difficulty/Tags/Remarks extras. |
| Multiple libraries | ❌ | Library switcher (later; schema-ready now). |

### Phase 1 — Library screen + organization + MobileSheets reskin
| MobileSheets | BasicSheets | Work |
|---|---|---|
| Top tab/pivot bar: Songs/Setlists/Artists/Albums/Genres/Collections/Composers/Bookmarks | 🟡 Songs + Bookmarks only | Add pivots; dense list rows; counts. |
| **Setlists** (ordered, continuous paging, editor, merge, share) | ❌ | New Setlist model + editor + continuous playback across songs. |
| **Collections** (tag-like) & **Groups** (curated editor) | ❌ | Collection field + Group editor. |
| Filtering, 10 sorts, alphabet jump, voice search | 🟡 search + 4 sorts | Add alphabet index, combinable filters, more sorts; (voice later). |
| Page-thumbnail previews | ❌ generic icon | Render first page as thumbnail. |
| MobileSheets look (dense, dark-first, top tabs, utilitarian) | ❌ teal cards | Reskin per the design decision. |

### Phase 2 — Import & file handling
| MobileSheets | BasicSheets | Work |
|---|---|---|
| PDF + **images** + **text/ChordPro** | ❌ PDF only | Image songs; text/ChordPro songs. |
| Split one PDF into songs by page range; batch import | ❌ | Page-range splitting + batch import UI. |
| Built-in **cloud browser** (Dropbox/Drive/OneDrive) | ❌ | Cloud import (platform-specific; scope later). |
| Link to existing folder without copying | ❌ copies to app storage | Optional "reference in place". |

### Phase 3 — Viewer parity
| MobileSheets | BasicSheets | Work |
|---|---|---|
| Single / two / half / **vertical & horizontal** scroll | 🟡 no horizontal | Add horizontal scroll. |
| **Smooth auto-scroll** (4 modes, per-song, start/stop by touch/pedal/MIDI) | 🟡 timed turn only | Real auto-scroll engine. |
| **Cropping** (auto / batch / manual editor) | ❌ | Crop model + editor; apply at render. |
| **Rotation** per page/score | ❌ | Rotate at render. |
| **Page reorder / duplicate / trim** (for repeats) | ❌ | Page-ops editor; per-copy annotations. |
| **Performance mode** + **corner action box** | 🟡 chrome + gestures | Action box (metronome/audio/scroll/setlist/bookmarks) + perf mode. |
| PDF-embedded bookmark reading | ❌ | Read PDF outline. |

### Phase 4 — Annotations parity
| MobileSheets | BasicSheets | Work |
|---|---|---|
| Pen/highlighter/eraser (modes)/text/stamps | ✅ (keep + hairpins) | Add eraser modes (drag-across/box). |
| **Shapes + arrows**, **nudge**, **image stamps** | ❌ | Add tools. |
| **Layers** (show/hide) | ❌ | Layer model + UI. |
| **Favorites** (saved tool configs) | ❌ | Favorites bar. |
| Export annotations baked into PDF | ❌ | PDF export w/ annotations. |

### Phase 5 — Performance utilities
| MobileSheets | BasicSheets | Work |
|---|---|---|
| **Metronome** (audio+visual modes, count-in, accent, auto page-turn) | ❌ | Metronome engine + UI; per-song tempo. |
| **Audio player** (tracks, **A-B loop**, tempo, pitch) | ❌ | Attach tracks; player; loop; tempo/pitch. |
| **Link points** (jump/repeat, highlight-fade) | 🟡 viewer buttons | Formalize into link points; keep button extras. |
| **Smart buttons** (MIDI / audio-from-position / URL / open file) | 🟡 nav actions only | Extend action set. |

### Phase 6 — Hardware & MIDI
| MobileSheets | BasicSheets | Work |
|---|---|---|
| **Bluetooth/USB foot pedals** (up to 6, mapped actions) | ❌ (gestures only) | HID/BT key capture + pedal-action mapping (reuse action set). |
| **MIDI**: load/send, triggers, KORG/Yamaha registration linking, tempo sync | ❌ | MIDI I/O (USB/BT); per-song send-on-load/load-on-receive. |

### Phase 7 — Sync, collaboration, sharing, backup
| MobileSheets | BasicSheets | Work |
|---|---|---|
| **Backup/restore** (single archive incl. annotations/bookmarks) | ❌ | Export/import full-library archive. |
| **Library sync** (WiFi/cloud, field-selective, keeps annotations) | ❌ | Sync engine (large). |
| **Collaboration** (leader/follower, book mode) | ❌ | Networked control (large). |
| **Sharing** (single-file export, setlist share, PDF export) + **printing** | ❌ | Export/share + print. |
| **Companion app** (desktop library manager) | ❌ | Separate desktop app (largest; likely out of scope initially). |

---

## 4. Cross-cutting decisions
- **Data layer:** SQLite is the right foundation for setlists/collections/rich metadata
  and sync. Recommended for Phase 0. (Alternative: keep JSON but relational — not advised.)
- **Design extent:** full MobileSheets reskin vs. keep BasicSheets' look vs. hybrid — see
  the question posed in chat.
- **Platform reach:** cloud import, MIDI, BT pedals, collaboration are per-platform
  (Android/iOS/Windows) and some may be Android-first.
- **Keep extras:** fold BasicSheets' gesture system, stylus features, hairpins, floating
  palette and auto-update into the MobileSheets-equivalent features rather than dropping
  them.

## 5. Recommended starting point
**Phase 0 + Phase 1** (data model → MobileSheets-style library with setlists/collections +
reskin). It unlocks the most other features and is where "same design" is most visible.
Then proceed down the phases, checking in between each.
