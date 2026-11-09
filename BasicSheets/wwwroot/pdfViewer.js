// ═══════════════════════════════════════════════════════════════════
//  pdfViewer.js – BasicSheets annotation engine
// ═══════════════════════════════════════════════════════════════════

// ── Canvas / PDF state ──────────────────────────────────────────────
let pdfDoc = null, pageNum = 1, currentPdfKey = "";
let _pdfTextCache = null;   // [page-1] = extracted page text, for in-document search; reset on doc load
let _hlTimer = null;        // search-highlight fade timer
let canvas = null, ctx = null;
let markerCanvas = null, markerCtx = null;
let noteCanvas = null, noteCtx = null;
let penCanvas = null, penCtx = null;

let zoom = 1;
let _baseZoom = 1;   // the page's current fit zoom (set by applyPageScaling); double-tap toggles around it
let panX = 0, panY = 0;
let baseWidth = 0, baseHeight = 0;

// ── Display mode tracking ────────────────────────────────────────────
let scrollMode = null;        // null | 'scroll-v' (legacy compat)
let halfSide   = null;        // null | 0 | 1 (legacy compat)
let _displayMode = 'single';  // 'single' | 'two' | 'half-turn' | 'scroll-v'
let _halfTurnTop = true;      // last half-turn position for resize

// ── Page scaling ────────────────────────────────────────────────────
let pageScaling = 'fit-screen';  // 'fit-screen' | 'fit-width' | 'fit-height' | 'full-screen'

// ── Edit state ──────────────────────────────────────────────────────
let editMode = false;
let tool     = "pen";
let penColor = "#000000";
let penSize  = 4;

// ── Per-tool memory ─────────────────────────────────────────────────
let toolColors = {};
let toolSizes  = {};

// ── Live drawing state ──────────────────────────────────────────────
let drawing      = false;
let lastX = 0,   lastY = 0;
let markerPoints = [];
let hairpinStart = null;
let liveHairpin  = null;
let liveStamp    = null;
let liveStroke   = null;   // currently-being-drawn pen/eraser stroke (vector)

// ── Vector annotations ──────────────────────────────────────────────
let penStrokes     = [];   // { color, size, eraser, points:[{x,y}] }
let markerAnnotations  = [];
let stampAnnotations   = [];
let textAnnotations    = [];
let hairpinAnnotations = [];
let shapeAnnotations   = [];        // line / arrow / rect / ellipse: {kind,x1,y1,x2,y2,color,size}
let shapeStart = null, liveShape = null;
let _shapeFill = false;              // fill rect/ellipse with a translucent wash (region highlight)
let _notesVisible = true;            // are the annotation layers shown? (hide/show toggle)

// ── Move tool ───────────────────────────────────────────────────────
let moveTgt    = null;
let moveStartCX = 0, moveStartCY = 0;

// ── Undo / redo ─────────────────────────────────────────────────────
let undoStack = [], redoStack = [];

// ── Refs ────────────────────────────────────────────────────────────
let dotNetRef = null, panelRef = null, panelDragObserver = null, _panelResizeBound = false;

// ── Pan / zoom input state ──────────────────────────────────────────
let lastDist = null;
let _suppressClick = false;
let _inertiaId = null, _velX = 0, _velY = 0;
let _touchDragging = false, _touchStartX = 0, _touchStartY = 0;
let _touchInitPanX = 0, _touchInitPanY = 0, _touchMoved = false;
let _lastTouchX = 0, _lastTouchY = 0, _lastTouchTime = 0;
let _panDragging = false, _panStartX = 0, _panStartY = 0;
let _mouseRawStartX = 0, _mouseRawStartY = 0;   // raw pointer-down position, for swipe-to-turn
let _wasPinching = false;
let _twoFingerTapStart = 0;
let _editSwipe  = null;     // active two-finger page-turn gesture while in edit mode
let _drawLocked = false;    // true from a 2nd finger landing until all fingers lift (blocks stray drawing)

// ── Active render task (cancel before re-rendering same canvas) ─────
let _activeRenderTask = null;
let _renderGen = 0;

function cancelActiveRender() {
    _renderGen++;
    if (_activeRenderTask) { try { _activeRenderTask.cancel(); } catch {} _activeRenderTask = null; }
}

// ── Slide animation state ───────────────────────────────────────────
let _slideDir = null;
let _slideSnapshot = null;

// ── Page preload cache ──────────────────────────────────────────────
const _pageCache = new Map();
const CACHE_AHEAD = 2, CACHE_BEHIND = 1;

// ── Symbol maps ─────────────────────────────────────────────────────
const STAMP_TOOLS = ["flat","sharp","natural","double-flat","double-sharp","fermata","trill","segno","coda",
    "accent","marcato","staccato","tenuto","down-bow","up-bow","breath","caesura","nav-dc","nav-ds","nav-fine","ped-down","ped-up",
    "dyn-ppp","dyn-pp","dyn-p","dyn-mp","dyn-mf","dyn-f","dyn-ff","dyn-fff","dyn-sf","dyn-sfz","dyn-fp"];
const SHAPE_TOOLS = ["line","arrow","rect","ellipse"];   // 2-point drag-to-draw vector shapes
const STAMP_SYM   = {
    flat:"♭", sharp:"♯", natural:"♮",
    "double-flat":"𝄫", "double-sharp":"𝄪",
    fermata:"𝄐", trill:"tr",
    segno:"𝄋", coda:"𝄌",
    accent:">", marcato:"^", staccato:"•", tenuto:"–",
    "down-bow":"⊓", "up-bow":"V", breath:"’", caesura:"//",
    "nav-dc":"D.C.", "nav-ds":"D.S.", "nav-fine":"Fine",
    "ped-down":"Ped.", "ped-up":"✱",
    "dyn-ppp":"ppp", "dyn-pp":"pp", "dyn-p":"p", "dyn-mp":"mp", "dyn-mf":"mf",
    "dyn-f":"f", "dyn-ff":"ff", "dyn-fff":"fff", "dyn-sf":"sf", "dyn-sfz":"sfz", "dyn-fp":"fp"
};


// ═══════════════════════════════════════════════════════════════════
//  PDF loading
// ═══════════════════════════════════════════════════════════════════

let imgEl = null, imgUrl = null, isImageDoc = false;

async function destroyCurrentPdf() {
    _pageCache.clear();
    _pdfTextCache = null;
    if (pdfDoc) {
        try { await pdfDoc.destroy(); } catch {}
        pdfDoc = null;
    }
    isImageDoc = false;
    try { if (imgUrl) { URL.revokeObjectURL(imgUrl); imgUrl = null; } } catch {}
    imgEl = null;
}

function _mimeForImage(fn) {
    const e = (fn.split('.').pop() || '').toLowerCase();
    return ({ jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png',
              gif: 'image/gif', bmp: 'image/bmp', webp: 'image/webp' })[e] || 'application/octet-stream';
}

// Load an image file as a single-"page" document. Reuses the same canvas + annotation
// machinery as PDFs via renderImage(), so zoom/pan/fit/annotations all work unchanged.
window.loadImageData = async function(filename, data) {
    await destroyCurrentPdf();
    currentPdfKey = filename;
    const blob = new Blob([data], { type: _mimeForImage(filename) });
    imgUrl = URL.createObjectURL(blob);
    imgEl = await new Promise((resolve, reject) => {
        const im = new Image();
        im.onload = () => resolve(im);
        im.onerror = () => reject(new Error("image load failed"));
        im.src = imgUrl;
    });
    isImageDoc = true;
    return 1;
};

window.loadPdfData = async function(filename, data) {
    await destroyCurrentPdf();
    currentPdfKey = filename;
    const lib = window["pdfjs-dist/build/pdf"];
    lib.GlobalWorkerOptions.workerSrc =
        "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js";
    pdfDoc = await lib.getDocument({ data }).promise;
    return pdfDoc.numPages;
};

window.loadPdf = async function(url) {
    await destroyCurrentPdf();
    currentPdfKey = url;
    const lib = window["pdfjs-dist/build/pdf"];
    lib.GlobalWorkerOptions.workerSrc =
        "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js";
    pdfDoc = await lib.getDocument(url).promise;
    return pdfDoc.numPages;
};

window.disposePdfViewer = async function() {
    await destroyCurrentPdf();
    penStrokes = []; markerAnnotations = []; stampAnnotations = [];
    textAnnotations = []; hairpinAnnotations = []; shapeAnnotations = [];
    undoStack = []; redoStack = [];
    baseWidth = 0; baseHeight = 0;
};

// ── In-document text search ────────────────────────────────────────
// Extract each page's text once (cached for the loaded doc), then return every page whose
// text contains the query, with a short snippet. Empty for scanned PDFs (no text layer).
// Build the per-page text cache once (shared by search + prewarm).
async function _ensurePdfTextCache() {
    if (_pdfTextCache || !pdfDoc) return;
    const cache = [];
    for (let p = 1; p <= pdfDoc.numPages; p++) {
        try {
            const page = await pdfDoc.getPage(p);
            const tc = await page.getTextContent();
            cache[p - 1] = tc.items.map(i => i.str).join(' ').replace(/\s+/g, ' ');
        } catch { cache[p - 1] = ''; }
    }
    if (!_pdfTextCache) _pdfTextCache = cache;   // first builder wins; a concurrent build is harmless
}

// Warm the text cache when the search box opens, so live search stays instant.
window.prewarmSearchCache = async function() { try { await _ensurePdfTextCache(); } catch {} };

// Diacritic-insensitive, LENGTH-PRESERVING normalize (ä→a, ö→o, ü→u …) so a query typed
// without umlauts still matches, and so match indices still line up with the original text
// (for snippets + highlight). Decomposing then stripping combining marks keeps each char 1:1.
function _normDia(s) {
    return String(s || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

window.searchPdf = async function(query) {
    if (!pdfDoc || !query) return [];
    const q = _normDia(query);
    if (!q) return [];
    await _ensurePdfTextCache();
    const results = [];
    for (let p = 1; p <= pdfDoc.numPages; p++) {
        const text = _pdfTextCache[p - 1] || '';
        const norm = _normDia(text);                     // diacritic-insensitive; same length as text, so indices align
        const idx = norm.indexOf(q);
        if (idx >= 0) {
            let count = 0, from = 0, k;
            while ((k = norm.indexOf(q, from)) >= 0) { count++; from = k + q.length; }   // occurrences on this page
            const start = Math.max(0, idx - 30);
            // Build the snippet in parts so the matched term's position is exact (the UI bolds it).
            const pre  = ((start > 0 ? '…' : '') + text.substring(start, idx)).replace(/^\s+/, '');
            const mid  = text.substring(idx, idx + q.length);
            const post = (text.substring(idx + q.length, idx + q.length + 45) + (idx + q.length + 45 < text.length ? '…' : '')).replace(/\s+$/, '');
            results.push({ page: p, snippet: pre + mid + post, count, matchStart: pre.length, matchLen: mid.length });
        }
    }
    return results;
};

// Briefly flash a highlight over the first occurrence of `query` on the current page.
// Single-page, unrotated pages only (the canvas holds the full page, so crop is fine);
// draws on btnCanvas and fades by redrawing the link-point markers. Best-effort.
window.highlightMatch = async function(query, pdfPage) {
    try {
        if (!pdfDoc || !btnCanvas || !btnCtx || !baseWidth) return false;
        const q = _normDia(query || '');
        if (q.length < 2) return false;
        const lib = window["pdfjs-dist/build/pdf"];

        // Paint every occurrence of q from a page's text onto btnCanvas, offset by
        // (xOff,yOff) into the composite canvas and clipped to the page box.
        const drawHits = (vp, tc, xOff, yOff, clipW, clipH) => {
            let n = 0;
            btnCtx.save();
            btnCtx.fillStyle = 'rgba(255, 210, 0, 0.40)';
            for (const item of tc.items) {
                if (!item.str || !_normDia(item.str).includes(q)) continue;
                const m = lib.Util.transform(vp.transform, item.transform);   // item box in viewport (canvas) px
                const hgt = Math.hypot(m[2], m[3]) || 14;
                let wdt = (item.width || 0) * vp.scale;
                if (wdt < 2) wdt = item.str.length * hgt * 0.5;
                const x = m[4], y = m[5] - hgt;
                if (x < -30 || y < -30 || x > clipW + 30 || y > clipH + 30) continue;
                const pad = Math.max(3, hgt * 0.18);
                btnCtx.fillRect(xOff + x - pad, yOff + y - pad, wdt + pad * 2, hgt + pad * 2);
                if (++n >= 60) break;                                         // cap, just in case
            }
            btnCtx.restore();
            return n;
        };

        let found = 0;
        if ((_displayMode === 'scroll-v' || _displayMode === 'scroll-h') && _scrollOrder) {
            // Scroll modes: btnCanvas spans the whole composite, so offset the target
            // page's hits to its slot. Works at any rotation (matches the render vp).
            const p = parseInt(pdfPage, 10);
            const idx = Number.isInteger(p) ? _scrollOrder.indexOf(p) : -1;
            if (idx < 0) return false;
            const page = await pdfDoc.getPage(p);
            const vp = page.getViewport({ scale: _scrollScale, rotation: pageRotation });
            const pageW = Math.ceil(vp.width), pageH = Math.ceil(vp.height);
            const xOff = _displayMode === 'scroll-v' ? Math.round((baseWidth - pageW) / 2) : (_scrollLefts[idx] || 0);
            const yOff = _displayMode === 'scroll-v' ? (_scrollTops[idx] || 0) : Math.round((baseHeight - pageH) / 2);
            found = drawHits(vp, await page.getTextContent(), xOff, yOff, pageW, pageH);
        } else if (_displayMode === 'two') {
            // Two-page: left page at x=0, right page at x=_twoLeftW+gap; offset to whichever holds the target.
            const p = parseInt(pdfPage, 10);
            const xOff = p === _twoLeftPdf ? 0 : (p === _twoRightPdf ? _twoLeftW + _twoGap : -1);
            if (xOff < 0) return false;
            const page = await pdfDoc.getPage(p);
            const vp = page.getViewport({ scale: 2, rotation: pageRotation });
            found = drawHits(vp, await page.getTextContent(), xOff, 0, Math.ceil(vp.width), Math.ceil(vp.height));
        } else if ((_displayMode === 'single' || _displayMode === 'half-turn') && (pageRotation % 360) === 0) {
            const page = await pdfDoc.getPage(pageNum);
            const vp = page.getViewport({ scale: 2, rotation: 0 });
            found = drawHits(vp, await page.getTextContent(), 0, 0, baseWidth, baseHeight);
        } else {
            return false;   // rotated single/half-turn, or unknown mode: no highlight (graceful)
        }

        if (!found) return false;
        clearTimeout(_hlTimer);
        _hlTimer = setTimeout(() => { try { _drawButtons(); } catch {} }, 2600);   // fade: redraw markers, clearing the highlight
        return true;
    } catch { return false; }
};


// ═══════════════════════════════════════════════════════════════════
//  Canvas init
// ═══════════════════════════════════════════════════════════════════

window.initCanvas = function(pdfId, markerId, noteId) {
    canvas       = document.getElementById(pdfId);     ctx       = canvas.getContext("2d");
    markerCanvas = document.getElementById(markerId);  markerCtx = markerCanvas.getContext("2d");
    noteCanvas   = document.getElementById(noteId);    noteCtx   = noteCanvas.getContext("2d");

    // Annotations start shown for every freshly-opened document.
    _notesVisible = true;
    markerCanvas.style.visibility = ""; noteCanvas.style.visibility = "";

    penCanvas = document.createElement("canvas");
    penCtx    = penCanvas.getContext("2d");

    // Reset cached viewer dimensions so the new DOM element gets sized
    _viewerW = 0; _viewerH = 0;
    baseWidth = 0; baseHeight = 0;
    zoom = 1; panX = 0; panY = 0;
    editMode = false;
    drawing = false;

    // Load per-tool memories from storage
    try {
        const rawColors = localStorage.getItem("bs_tool_colors");
        if (rawColors) toolColors = JSON.parse(rawColors);
    } catch {}
    try {
        const rawSizes = localStorage.getItem("bs_tool_sizes");
        if (rawSizes) toolSizes = JSON.parse(rawSizes);
    } catch {}

    teardownListeners();
    setupDrawing();
    setupInteraction();
};


// ═══════════════════════════════════════════════════════════════════
//  Rendering
// ═══════════════════════════════════════════════════════════════════

window.renderPage = async function(num, dispPage) {
    scrollMode = null; halfSide = null; _displayMode = 'single';
    _btnCurDisp = (dispPage != null ? dispPage : num);   // display page shown → places link markers
    _clearScrollTiles();
    cancelActiveRender();
    const gen = _renderGen;
    showSpinner();
    beginPageTransition(num);
    pageNum = num;

    const cached = _pageCache.get(num);
    let w, h;
    if (cached) {
        w = cached.width; h = cached.height;
        canvas.width = markerCanvas.width = noteCanvas.width  = w;
        canvas.height = markerCanvas.height = noteCanvas.height = h;
        penCanvas.width = w; penCanvas.height = h;
        ctx.drawImage(cached.canvas, 0, 0);
    } else {
        const page = await pdfDoc.getPage(num);
        if (gen !== _renderGen) return;
        const vp   = page.getViewport({ scale: 2, rotation: pageRotation });
        w = Math.ceil(vp.width); h = Math.ceil(vp.height);
        canvas.width = markerCanvas.width = noteCanvas.width  = w;
        canvas.height = markerCanvas.height = noteCanvas.height = h;
        penCanvas.width = w; penCanvas.height = h;
        ctx.fillStyle = "white";
        ctx.fillRect(0, 0, w, h);
        if (_activeRenderTask) { try { _activeRenderTask.cancel(); } catch {} _activeRenderTask = null; }
        const task = page.render({ canvasContext: ctx, viewport: vp });
        _activeRenderTask = task;
        try { await task.promise; } catch (e) { if (e?.name === 'RenderingCancelledException') return; throw e; }
        if (gen !== _renderGen) return;
        _activeRenderTask = null;
    }

    baseWidth = w; baseHeight = h;
    loadNotes();
    if (btnCanvas) { btnCanvas.width = baseWidth; btnCanvas.height = baseHeight; _drawButtons(); }
    applyPageScaling();
    endPageTransition();
    hideSpinner();
    preloadAround(num);
};

// Single-image render — mirrors renderPage's post-render sequence (baseWidth/baseHeight +
// loadNotes + applyPageScaling), so fit/zoom/pan/annotations behave exactly as for a PDF page.
window.renderImage = async function() {
    if (!imgEl) return;
    cancelActiveRender();
    scrollMode = null; halfSide = null; _displayMode = 'single';
    _clearScrollTiles();
    pageNum = 1;
    _btnCurDisp = 1;
    showSpinner();
    let iw = imgEl.naturalWidth || 1, ih = imgEl.naturalHeight || 1;
    const maxDim = 2600;
    const sc = Math.min(1, maxDim / Math.max(iw, ih));
    iw = Math.max(1, Math.round(iw * sc));
    ih = Math.max(1, Math.round(ih * sc));
    const swap = (pageRotation === 90 || pageRotation === 270);
    const cw = swap ? ih : iw, ch = swap ? iw : ih;
    canvas.width = markerCanvas.width = noteCanvas.width  = cw;
    canvas.height = markerCanvas.height = noteCanvas.height = ch;
    penCanvas.width = cw; penCanvas.height = ch;
    ctx.fillStyle = "white"; ctx.fillRect(0, 0, cw, ch);
    ctx.save();
    ctx.translate(cw / 2, ch / 2);
    ctx.rotate(pageRotation * Math.PI / 180);
    try { ctx.drawImage(imgEl, -iw / 2, -ih / 2, iw, ih); } catch (e) {}
    ctx.restore();
    baseWidth = cw; baseHeight = ch;
    loadNotes();
    if (btnCanvas) { btnCanvas.width = baseWidth; btnCanvas.height = baseHeight; _drawButtons(); }
    applyPageScaling();
    hideSpinner();
};

// Two-page layout, captured for search highlight: which PDF pages are shown + left page width.
let _twoLeftPdf = 0, _twoRightPdf = 0, _twoLeftW = 0, _twoGap = 24;
window.renderTwoPage = async function(num) {
    scrollMode = null; halfSide = null; _displayMode = 'two';
    _clearScrollTiles();
    cancelActiveRender();
    const gen = _renderGen;
    showSpinner();
    beginPageTransition(num);
    pageNum = num;
    // Map the display slot → PDF pages via the custom sequence (null = natural order).
    const _seq = _renderSeq();
    const _leftPdf  = _seq ? _seq[Math.max(1, Math.min(num, _seq.length)) - 1] : num;
    const _rightNum = num + 1;
    const _rightPdf = _seq ? (_rightNum <= _seq.length ? _seq[_rightNum - 1] : 0)
                           : (_rightNum <= pdfDoc.numPages ? _rightNum : 0);
    const p1 = await pdfDoc.getPage(_leftPdf);
    if (gen !== _renderGen) return;
    const v1 = p1.getViewport({ scale: 2, rotation: pageRotation });
    const t1 = makeCanvas(Math.ceil(v1.width), Math.ceil(v1.height));
    const c1 = t1.getContext("2d");
    c1.fillStyle = "white"; c1.fillRect(0, 0, t1.width, t1.height);
    await p1.render({ canvasContext: c1, viewport: v1 }).promise;
    if (gen !== _renderGen) return;

    let t2 = null;
    if (_rightPdf >= 1) {
        const p2 = await pdfDoc.getPage(_rightPdf);
        if (gen !== _renderGen) return;
        const v2 = p2.getViewport({ scale: 2, rotation: pageRotation });
        t2 = makeCanvas(Math.ceil(v2.width), Math.ceil(v2.height));
        const c2 = t2.getContext("2d");
        c2.fillStyle = "white"; c2.fillRect(0, 0, t2.width, t2.height);
        await p2.render({ canvasContext: c2, viewport: v2 }).promise;
        if (gen !== _renderGen) return;
    }

    const gap = 24;
    const tw = t2 ? t1.width + gap + t2.width : t1.width;
    const th = t2 ? Math.max(t1.height, t2.height) : t1.height;

    canvas.width = markerCanvas.width = noteCanvas.width  = tw;
    canvas.height = markerCanvas.height = noteCanvas.height = th;
    penCanvas.width = tw; penCanvas.height = th;

    ctx.fillStyle = "#1a1a1a"; ctx.fillRect(0, 0, tw, th);
    ctx.drawImage(t1, 0, 0);
    if (t2) ctx.drawImage(t2, t1.width + gap, 0);

    _twoLeftPdf = _leftPdf; _twoRightPdf = _rightPdf; _twoLeftW = t1.width; _twoGap = gap;
    _twoLeftDisp = num; _twoRightDisp = t2 ? num + 1 : 0;   // display-page numbers in each slot
    _twoLeftH = t1.height; _twoRightW = t2 ? t2.width : 0; _twoRightH = t2 ? t2.height : 0;
    baseWidth = tw; baseHeight = th;
    loadNotes();
    if (btnCanvas) { btnCanvas.width = baseWidth; btnCanvas.height = baseHeight; _drawButtons(); }
    applyPageScaling();
    endPageTransition();
    hideSpinner();
    preloadAround(num);
};


// ── Half-turn mode (Halbe Seite – like MobileSheets) ────────────────
// Renders FULL page at fit-to-width. Taps alternate between showing
// the top half and the bottom half of the same page.

window.renderHalfTurn = async function(num, topHalf, dispPage) {
    scrollMode = null; halfSide = null;
    _displayMode = 'half-turn';
    _clearScrollTiles();
    _halfTurnTop = topHalf;
    _btnCurDisp = (dispPage != null ? dispPage : num);   // the sheet shown (both halves share it)
    cancelActiveRender();
    const gen = _renderGen;
    showSpinner();
    beginPageTransition(num);

    // Same PDF page → just reposition, skip re-render
    if (pageNum === num && baseWidth > 0) {
        _applyHalfTurnFit(topHalf);
        endPageTransition();
        hideSpinner();
        return;
    }

    pageNum = num;
    const page = await pdfDoc.getPage(num);
    if (gen !== _renderGen) return;
    const vp   = page.getViewport({ scale: 2, rotation: pageRotation });
    const w = Math.ceil(vp.width), h = Math.ceil(vp.height);

    canvas.width = markerCanvas.width = noteCanvas.width  = w;
    canvas.height = markerCanvas.height = noteCanvas.height = h;
    penCanvas.width = w; penCanvas.height = h;

    ctx.fillStyle = "white"; ctx.fillRect(0, 0, w, h);
    if (_activeRenderTask) { try { _activeRenderTask.cancel(); } catch {} _activeRenderTask = null; }
    const task = page.render({ canvasContext: ctx, viewport: vp });
    _activeRenderTask = task;
    try { await task.promise; } catch (e) { if (e?.name === 'RenderingCancelledException') return; throw e; }
    if (gen !== _renderGen) return;
    _activeRenderTask = null;

    baseWidth = w; baseHeight = h;
    loadNotes();
    if (btnCanvas) { btnCanvas.width = baseWidth; btnCanvas.height = baseHeight; _drawButtons(); }
    _applyHalfTurnFit(topHalf);
    endPageTransition();
    hideSpinner();
};

// Position: fit page to screen width, then pan to show top or bottom half.
function _applyHalfTurnFit(topHalf) {
    if (!baseWidth || !baseHeight) return;
    const hdrH  = getHeaderHeight();
    const viewH = availViewH();

    // Always fit to width
    zoom = window.innerWidth / baseWidth;
    panX = 0;

    const scaledH = baseHeight * zoom;

    if (scaledH <= viewH) {
        // Page fits entirely — center it
        panY = hdrH + (viewH - scaledH) / 2;
    } else if (topHalf) {
        // Show top of page (align page-top to header)
        panY = hdrH;
    } else {
        // Show bottom half: align page bottom to viewport bottom
        panY = hdrH + viewH - scaledH;
    }
    applyView();
}


// ── Vertical scroll mode ────────────────────────────────────────────
// All pages rendered into one tall canvas. User scrolls freely.

// ── Custom page sequence (reorder / duplicate / hide) for scroll modes ──
// Array of 1-based PDF page numbers, or null = natural order. Blazor sets it
// from the song's PageOrder before a scroll re-render; renderAllV/H apply it.
let _pageSeq = null;
window.setPageSequence = function(arr) {
    _pageSeq = (Array.isArray(arr) && arr.length) ? arr.slice() : null;
};
function _renderSeq() {
    if (!_pageSeq || !_pageSeq.length || !pdfDoc) return null;
    const v = _pageSeq.filter(n => Number.isInteger(n) && n >= 1 && n <= pdfDoc.numPages);
    return v.length ? v : null;
}

// Per-render page offsets inside the composite scroll canvas (for scroll-to-page).
// _scrollOrder[i] = the PDF page drawn at display slot i; _scrollTops/_scrollLefts[i] = its canvas offset.
let _scrollOrder = null, _scrollTops = [], _scrollLefts = [];
window.scrollToSlot = function(idx) {
    if (!_scrollOrder || !_scrollOrder.length) return false;
    idx = Math.max(0, Math.min(_scrollOrder.length - 1, idx));
    if (_displayMode === 'scroll-v' && idx < _scrollTops.length) {
        panY = getHeaderHeight() - _scrollTops[idx] * zoom;   // slot top under the header
        clampPan(); applyView(); return true;
    }
    if (_displayMode === 'scroll-h' && idx < _scrollLefts.length) {
        panX = -_scrollLefts[idx] * zoom;                     // slot left at the left edge
        clampPan(); applyView(); return true;
    }
    return false;
};
window.scrollToPdfPage = function(p) {
    if (!_scrollOrder) return false;
    const idx = _scrollOrder.indexOf(p);        // first slot showing this PDF page
    return idx < 0 ? false : window.scrollToSlot(idx);
};
// Current slot (0-based) nearest at/above the top-left of the view, or -1.
window.getScrollSlot = function() {
    if (!_scrollOrder || !_scrollOrder.length) return -1;
    if (_displayMode === 'scroll-v') {
        const y = (getHeaderHeight() - panY) / zoom;
        let i = 0; for (let k = 0; k < _scrollTops.length; k++) { if (_scrollTops[k] <= y + 1) i = k; else break; }
        return i;
    }
    if (_displayMode === 'scroll-h') {
        const x = -panX / zoom;
        let i = 0; for (let k = 0; k < _scrollLefts.length; k++) { if (_scrollLefts[k] <= x + 1) i = k; else break; }
        return i;
    }
    return -1;
};

// Scroll composites are one canvas as tall/wide as every page stacked; keep it under
// the browser's max canvas size (Chromium ~65535, Firefox 32767) or it renders blank.
// Measure the stack at scale 2 and, only when it would overflow, shrink everything
// uniformly so long documents render (a little softer) instead of going blank.
const _MAX_SCROLL_DIM = 32000;
let _scrollScale = 2;
async function _scrollFitScale(order, gen, axis) {
    let span = 0;                                   // summed page heights (v) / widths (h) at scale 2
    for (const i of order) {
        const page = await pdfDoc.getPage(i);
        if (gen !== _renderGen) return null;        // superseded
        const vp = page.getViewport({ scale: 2, rotation: pageRotation });
        span += Math.ceil(axis === 'v' ? vp.height : vp.width);
    }
    const total = span + 24 * (order.length - 1);   // include the inter-page gaps
    return total > _MAX_SCROLL_DIM ? 2 * _MAX_SCROLL_DIM / total : 2;
}

// ── Virtualized PDF tiles (long vertical-scroll docs) ───────────────
// The overlay canvases (notes/highlight) stay single canvases, so the virtual coordinate
// space stays clamped to _MAX_SCROLL_DIM and everything that uses it (pan/zoom, _scrollTops,
// scroll-to, annotations, highlight, getPos) is UNCHANGED. Only the PDF layer is split into
// bounded, SUPERSAMPLED tiles rendered lazily near the viewport, so long docs read sharp.
let _scrollTiles = [];              // [{ el, ctx, y0, y1, rendered, busy }] — clamped-virtual Y bands
let _scrollPageW = [], _scrollPageH = [];   // clamped-virtual page sizes, parallel to _scrollOrder
let _scrollSuper = 1;              // tile supersample factor (renders pages above the clamped scale)
const _tilePageCache = new Map();  // pdfPage → full-scale rendered canvas (small LRU)
let _tileRefreshRaf = 0;

function _clearScrollTiles() {
    for (const t of _scrollTiles) { try { t.el.remove(); } catch {} }
    _scrollTiles = [];
    _tilePageCache.clear();
    if (canvas) canvas.style.display = '';     // restore the single PDF canvas for non-scroll modes
    if (_tileRefreshRaf) { cancelAnimationFrame(_tileRefreshRaf); _tileRefreshRaf = 0; }
}

async function _tilePage(pdfPage, gen) {       // full-scale render of a page, cached (small LRU)
    let c = _tilePageCache.get(pdfPage);
    if (c) return c;
    const page = await pdfDoc.getPage(pdfPage);
    if (gen !== _renderGen) return null;
    const vp = page.getViewport({ scale: 2, rotation: pageRotation });
    const w = Math.ceil(vp.width), h = Math.ceil(vp.height);
    c = makeCanvas(w, h);
    const cx = c.getContext('2d');
    cx.fillStyle = 'white'; cx.fillRect(0, 0, w, h);
    await page.render({ canvasContext: cx, viewport: vp }).promise;
    if (gen !== _renderGen) return null;
    _tilePageCache.set(pdfPage, c);
    if (_tilePageCache.size > 8) { const k = _tilePageCache.keys().next().value; _tilePageCache.delete(k); }
    return c;
}

function _buildScrollTilesV(clampScale) {
    const v = document.getElementById('viewer');
    if (!v) return;
    _scrollSuper = Math.max(1, 2 / clampScale);                       // render pages up to full scale 2
    const maxBand = Math.floor((_MAX_SCROLL_DIM - 1000) / _scrollSuper);  // keep tile internal px under the limit
    const TILE_H  = Math.max(1000, Math.min(maxBand, 8000));
    for (let y0 = 0; y0 < baseHeight; y0 += TILE_H) {
        const y1 = Math.min(baseHeight, y0 + TILE_H);
        const el = document.createElement('canvas');
        el.style.cssText = `position:absolute;left:0;top:${y0}px;width:${baseWidth}px;height:${y1 - y0}px;pointer-events:none`;
        el.width = 1; el.height = 1;                                  // empty until rendered (frees memory)
        v.insertBefore(el, v.firstChild);                            // behind the overlay canvases
        _scrollTiles.push({ el, ctx: el.getContext('2d'), y0, y1, rendered: false, busy: false });
    }
    canvas.style.display = 'none';                                   // the single PDF canvas is unused in tiled scroll
}

async function _renderTileV(t, gen) {           // blit every page overlapping this band, supersampled
    if (t.rendered || t.busy) return;
    t.busy = true;
    try {
        if (t.el.width < 2) { t.el.width = Math.round(baseWidth * _scrollSuper); t.el.height = Math.round((t.y1 - t.y0) * _scrollSuper); }
        const cx = t.ctx;
        cx.setTransform(1, 0, 0, 1, 0, 0);
        cx.fillStyle = '#2a2a2a'; cx.fillRect(0, 0, t.el.width, t.el.height);
        for (let i = 0; i < _scrollOrder.length; i++) {
            const pTop = _scrollTops[i], pH = _scrollPageH[i], pW = _scrollPageW[i];
            if (pTop + pH <= t.y0 || pTop >= t.y1) continue;          // no overlap with this band
            const img = await _tilePage(_scrollOrder[i], gen);
            if (gen !== _renderGen) { t.busy = false; return; }
            if (!img) continue;
            const dx = Math.round((baseWidth - pW) / 2) * _scrollSuper;
            const dy = Math.round(pTop - t.y0) * _scrollSuper;
            cx.drawImage(img, dx, dy, pW * _scrollSuper, pH * _scrollSuper);   // canvas clips pages that straddle the band
        }
        t.rendered = true;
    } catch {}
    t.busy = false;
}

function _buildScrollTilesH(clampScale) {
    const v = document.getElementById('viewer');
    if (!v) return;
    _scrollSuper = Math.max(1, 2 / clampScale);
    const maxBand = Math.floor((_MAX_SCROLL_DIM - 1000) / _scrollSuper);
    const TILE_W  = Math.max(1000, Math.min(maxBand, 8000));
    for (let x0 = 0; x0 < baseWidth; x0 += TILE_W) {
        const x1 = Math.min(baseWidth, x0 + TILE_W);
        const el = document.createElement('canvas');
        el.style.cssText = `position:absolute;top:0;left:${x0}px;width:${x1 - x0}px;height:${baseHeight}px;pointer-events:none`;
        el.width = 1; el.height = 1;
        v.insertBefore(el, v.firstChild);
        _scrollTiles.push({ el, ctx: el.getContext('2d'), x0, x1, rendered: false, busy: false });
    }
    canvas.style.display = 'none';
}

async function _renderTileH(t, gen) {           // blit every page overlapping this X band, supersampled
    if (t.rendered || t.busy) return;
    t.busy = true;
    try {
        if (t.el.width < 2) { t.el.width = Math.round((t.x1 - t.x0) * _scrollSuper); t.el.height = Math.round(baseHeight * _scrollSuper); }
        const cx = t.ctx;
        cx.setTransform(1, 0, 0, 1, 0, 0);
        cx.fillStyle = '#2a2a2a'; cx.fillRect(0, 0, t.el.width, t.el.height);
        for (let i = 0; i < _scrollOrder.length; i++) {
            const pLeft = _scrollLefts[i], pW = _scrollPageW[i], pH = _scrollPageH[i];
            if (pLeft + pW <= t.x0 || pLeft >= t.x1) continue;       // no overlap with this band
            const img = await _tilePage(_scrollOrder[i], gen);
            if (gen !== _renderGen) { t.busy = false; return; }
            if (!img) continue;
            const dx = Math.round(pLeft - t.x0) * _scrollSuper;
            const dy = Math.round((baseHeight - pH) / 2) * _scrollSuper;
            cx.drawImage(img, dx, dy, pW * _scrollSuper, pH * _scrollSuper);
        }
        t.rendered = true;
    } catch {}
    t.busy = false;
}

function _refreshScrollTiles() {                // render tiles near the viewport; free far ones
    if (!_scrollTiles.length) return;
    const gen = _renderGen;
    if (_displayMode === 'scroll-v') {
        const viewTop = (getHeaderHeight() - panY) / zoom;           // clamped-virtual Y at the top of the view
        const viewBot = viewTop + availViewH() / zoom;
        const buf = Math.max(400, viewBot - viewTop);                // ~one screenful of buffer each way
        for (const t of _scrollTiles) {
            const near = t.y1 >= viewTop - buf && t.y0 <= viewBot + buf;
            if (near) { _renderTileV(t, gen); }
            else if (t.rendered && (t.y1 < viewTop - buf * 2 || t.y0 > viewBot + buf * 2)) {
                t.rendered = false; t.el.width = 1; t.el.height = 1; // free a far tile's backing store
            }
        }
    } else if (_displayMode === 'scroll-h') {
        const viewLeft  = -panX / zoom;                              // clamped-virtual X at the left of the view
        const viewRight = viewLeft + window.innerWidth / zoom;
        const buf = Math.max(400, viewRight - viewLeft);
        for (const t of _scrollTiles) {
            const near = t.x1 >= viewLeft - buf && t.x0 <= viewRight + buf;
            if (near) { _renderTileH(t, gen); }
            else if (t.rendered && (t.x1 < viewLeft - buf * 2 || t.x0 > viewRight + buf * 2)) {
                t.rendered = false; t.el.width = 1; t.el.height = 1;
            }
        }
    }
}
function _scheduleTileRefresh() {
    if (_tileRefreshRaf) return;
    _tileRefreshRaf = requestAnimationFrame(() => { _tileRefreshRaf = 0; try { _refreshScrollTiles(); } catch {} });
}

window.renderAllV = async function() {
    _displayMode = 'scroll-v';
    scrollMode = "scroll-v"; halfSide = null;
    cancelActiveRender();
    _clearScrollTiles();
    const gen = _renderGen;
    showSpinner();
    beginPageTransition(null);
    pageNum = -1;
    const _order = _renderSeq() || Array.from({ length: pdfDoc.numPages }, (_, k) => k + 1);
    const scale = await _scrollFitScale(_order, gen, 'v');
    if (scale == null) return;                       // render superseded during measurement
    _scrollScale = scale;
    const gap = Math.round(24 * scale / 2);          // gap shrinks with the pages

    // Layout pass: page sizes at the (clamped) coordinate scale — positions only, no pixels yet.
    _scrollOrder = _order.slice(); _scrollTops = []; _scrollLefts = []; _scrollPageW = []; _scrollPageH = [];
    let maxW = 0;
    for (const i of _order) {
        const page = await pdfDoc.getPage(i);
        if (gen !== _renderGen) return;
        const vp = page.getViewport({ scale, rotation: pageRotation });
        const w = Math.ceil(vp.width), h = Math.ceil(vp.height);
        _scrollPageW.push(w); _scrollPageH.push(h); if (w > maxW) maxW = w;
    }
    let y = 0;
    for (let i = 0; i < _scrollPageH.length; i++) { _scrollTops.push(y); y += _scrollPageH[i] + gap; }
    const totalH = y - gap;
    baseWidth = maxW; baseHeight = totalH;

    // Overlays (notes/highlight) are single canvases at the clamped size in BOTH paths.
    markerCanvas.width = noteCanvas.width = maxW; markerCanvas.height = noteCanvas.height = totalH;
    penCanvas.width = maxW; penCanvas.height = totalH;

    if (scale >= 2) {
        // Fits whole — one composite PDF canvas at full scale (the proven path).
        canvas.style.display = '';
        canvas.width = maxW; canvas.height = totalH;
        ctx.fillStyle = "#2a2a2a"; ctx.fillRect(0, 0, maxW, totalH);
        for (let i = 0; i < _order.length; i++) {
            const page = await pdfDoc.getPage(_order[i]);
            if (gen !== _renderGen) return;
            const vp = page.getViewport({ scale, rotation: pageRotation });
            const tmp = makeCanvas(Math.ceil(vp.width), Math.ceil(vp.height));
            const tc = tmp.getContext("2d"); tc.fillStyle = "white"; tc.fillRect(0, 0, tmp.width, tmp.height);
            await page.render({ canvasContext: tc, viewport: vp }).promise;
            if (gen !== _renderGen) return;
            ctx.drawImage(tmp, Math.round((maxW - tmp.width) / 2), _scrollTops[i]);
        }
    } else {
        // Long doc — virtualized PDF tiles (supersampled, lazy); overlays stay clamped.
        canvas.width = 1; canvas.height = 1;         // free the unused single PDF canvas
        _buildScrollTilesV(scale);
    }

    loadNotes();
    if (btnCanvas) { btnCanvas.width = baseWidth; btnCanvas.height = baseHeight; _drawButtons(); }
    fitToWidth();                                    // sets zoom/pan
    if (scale < 2) _refreshScrollTiles();            // render the initially-visible tiles
    endPageTransition();
    hideSpinner();
};


// ── Horizontal scroll mode ──────────────────────────────────────────
// All pages rendered side by side into one wide canvas. User scrolls freely.

window.renderAllH = async function() {
    _displayMode = 'scroll-h';
    scrollMode = "scroll-h"; halfSide = null;
    cancelActiveRender();
    _clearScrollTiles();                             // horizontal scroll keeps the single-composite path; drop any vertical tiles
    const gen = _renderGen;
    showSpinner();
    beginPageTransition(null);
    pageNum = -1;
    const _order = _renderSeq() || Array.from({ length: pdfDoc.numPages }, (_, k) => k + 1);
    const scale = await _scrollFitScale(_order, gen, 'h');
    if (scale == null) return;                       // render superseded during measurement
    _scrollScale = scale;
    const gap = Math.round(24 * scale / 2);          // gap shrinks with the pages

    // Layout pass: page sizes at the (clamped) coordinate scale — positions only, no pixels yet.
    _scrollOrder = _order.slice(); _scrollTops = []; _scrollLefts = []; _scrollPageW = []; _scrollPageH = [];
    let maxH = 0;
    for (const i of _order) {
        const page = await pdfDoc.getPage(i);
        if (gen !== _renderGen) return;
        const vp = page.getViewport({ scale, rotation: pageRotation });
        const w = Math.ceil(vp.width), h = Math.ceil(vp.height);
        _scrollPageW.push(w); _scrollPageH.push(h); if (h > maxH) maxH = h;
    }
    let x = 0;
    for (let i = 0; i < _scrollPageW.length; i++) { _scrollLefts.push(x); x += _scrollPageW[i] + gap; }
    const totalW = x - gap;
    baseWidth = totalW; baseHeight = maxH;

    // Overlays (notes/highlight) are single canvases at the clamped size in BOTH paths.
    markerCanvas.width = noteCanvas.width = totalW; markerCanvas.height = noteCanvas.height = maxH;
    penCanvas.width = totalW; penCanvas.height = maxH;

    if (scale >= 2) {
        // Fits whole — one composite PDF canvas at full scale (the proven path).
        canvas.style.display = '';
        canvas.width = totalW; canvas.height = maxH;
        ctx.fillStyle = "#2a2a2a"; ctx.fillRect(0, 0, totalW, maxH);
        for (let i = 0; i < _order.length; i++) {
            const page = await pdfDoc.getPage(_order[i]);
            if (gen !== _renderGen) return;
            const vp = page.getViewport({ scale, rotation: pageRotation });
            const tmp = makeCanvas(Math.ceil(vp.width), Math.ceil(vp.height));
            const tc = tmp.getContext("2d"); tc.fillStyle = "white"; tc.fillRect(0, 0, tmp.width, tmp.height);
            await page.render({ canvasContext: tc, viewport: vp }).promise;
            if (gen !== _renderGen) return;
            ctx.drawImage(tmp, _scrollLefts[i], Math.round((maxH - tmp.height) / 2));
        }
    } else {
        // Long doc — virtualized PDF tiles (supersampled, lazy); overlays stay clamped.
        canvas.width = 1; canvas.height = 1;         // free the unused single PDF canvas
        _buildScrollTilesH(scale);
    }

    loadNotes();
    if (btnCanvas) { btnCanvas.width = baseWidth; btnCanvas.height = baseHeight; _drawButtons(); }
    fitToHeight();                                   // sets zoom/pan
    if (scale < 2) _refreshScrollTiles();            // render the initially-visible tiles
    endPageTransition();
    hideSpinner();
};

function makeCanvas(w, h) {
    return Object.assign(document.createElement("canvas"), { width: w, height: h });
}


// ═══════════════════════════════════════════════════════════════════
//  View — CSS transform-based (GPU-accelerated, MobileSheets-style)
// ═══════════════════════════════════════════════════════════════════

const DRAG_THRESHOLD   = 10;

let _viewerW = 0, _viewerH = 0;
let _chromeTop = 0, _chromeBottom = 0;   // px insets for the visible top header / bottom slider
let _chromeTopVis = false, _chromeBotVis = false;

function getHeaderHeight() {
    return _chromeTop;
}
function availViewH() {
    return window.innerHeight - _chromeTop - _chromeBottom;
}

// ── Crop: an effective content box within the page, framed to fill the screen ──
let cropRect = null;   // { l, t, r, b } fractions of base dims, or null
let cropEditing = false;   // true while the manual crop editor is open (suppresses pan/zoom/gestures)
let pageRotation = 0;      // display rotation in degrees (0 | 90 | 180 | 270), applied to every render
function _cropActive() { return cropRect && _displayMode === 'single'; }
function _cw()  { return _cropActive() ? (cropRect.r - cropRect.l) * baseWidth  : baseWidth; }
function _ch()  { return _cropActive() ? (cropRect.b - cropRect.t) * baseHeight : baseHeight; }
function _cox() { return _cropActive() ? cropRect.l * baseWidth  : 0; }
function _coy() { return _cropActive() ? cropRect.t * baseHeight : 0; }

function getFitZoom() {
    if (!baseWidth || !baseHeight) return 1;
    if (_cropActive()) return window.innerWidth / _cw();
    if (_displayMode === 'scroll-h') return availViewH() / baseHeight;   // fit height, scroll sideways
    if (_displayMode === 'scroll-v' || _displayMode === 'half-turn')
        return window.innerWidth / baseWidth;
    const viewW = window.innerWidth;
    const viewH = availViewH();
    return Math.min(viewW / baseWidth, viewH / baseHeight);
}

function isZoomed() {
    return zoom > getFitZoom() * 1.02;
}

function shouldPan() {
    const vw = window.innerWidth;
    return _cw() * zoom > vw + 2 || _ch() * zoom > availViewH() + 2;
}

function clampZoom(z) {
    const fit = getFitZoom();
    return Math.max(fit, Math.min(fit * 5, z));
}

function applyView() {
    const v = document.getElementById("viewer");
    if (!v) return;
    if (_viewerW !== baseWidth || _viewerH !== baseHeight) {
        v.style.width  = baseWidth  + "px";
        v.style.height = baseHeight + "px";
        _viewerW = baseWidth;
        _viewerH = baseHeight;
    }
    v.style.transform = `translate(${Math.round(panX)}px, ${Math.round(panY)}px) scale(${zoom})`;
    if (_scrollTiles.length) _scheduleTileRefresh();   // lazily (re)render PDF tiles near the viewport
}

function fitToWidth() {
    const hdrH = getHeaderHeight();
    zoom = getFitZoom();
    const ch    = _ch() * zoom;
    const viewH = availViewH();
    panX = -_cox() * zoom;
    panY = (ch <= viewH ? hdrH + (viewH - ch) / 2 : hdrH) - _coy() * zoom;
    applyView();
}

function fitToScreen() {
    const hdrH  = getHeaderHeight();
    const viewW = window.innerWidth;
    const viewH = availViewH();
    zoom = Math.min(viewW / _cw(), viewH / _ch());
    const cw = _cw() * zoom, ch = _ch() * zoom;
    panX = (viewW - cw) / 2 - _cox() * zoom;
    panY = hdrH + (viewH - ch) / 2 - _coy() * zoom;
    applyView();
}

function fitToHeight() {
    const hdrH  = getHeaderHeight();
    const viewH = availViewH();
    zoom = viewH / _ch();
    const cw = _cw() * zoom;
    panX = (cw <= window.innerWidth ? (window.innerWidth - cw) / 2 : 0) - _cox() * zoom;
    panY = hdrH - _coy() * zoom;
    applyView();
}

function fitFullScreen() {
    const hdrH  = getHeaderHeight();
    const viewW = window.innerWidth;
    const viewH = availViewH();
    zoom = Math.max(viewW / _cw(), viewH / _ch());
    const cw = _cw() * zoom, ch = _ch() * zoom;
    panX = (viewW - cw) / 2 - _cox() * zoom;
    panY = hdrH + (viewH - ch) / 2 - _coy() * zoom;
    applyView();
}

function applyPageScaling() {
    if (!baseWidth || !baseHeight) return;
    switch (pageScaling) {
        case 'fit-width':    fitToWidth();   break;
        case 'fit-height':   fitToHeight();  break;
        case 'full-screen':  fitFullScreen(); break;
        default:             fitToScreen();  break;
    }
    _baseZoom = zoom;
}

window.setPageScaling = function(mode) {
    pageScaling = mode || 'fit-screen';
    // Only apply to single/two page modes — scroll and half-turn have their own fit
    if (_displayMode === 'single' || _displayMode === 'two') {
        applyPageScaling();
    }
};

// ── Crop control ────────────────────────────────────────────────────
// Set the crop for the page about to render (applied by the render's own
// applyPageScaling). Clear/too-small/full-page => no crop.
window.setCrop = function(l, t, r, b) {
    if (l == null || t == null || r == null || b == null) { cropRect = null; return; }
    l = Math.max(0, Math.min(1, l)); t = Math.max(0, Math.min(1, t));
    r = Math.max(0, Math.min(1, r)); b = Math.max(0, Math.min(1, b));
    if (r - l < 0.05 || b - t < 0.05 || (l < 0.002 && t < 0.002 && r > 0.998 && b > 0.998))
        cropRect = null;
    else
        cropRect = { l, t, r, b };
};
window.clearCrop = function() { cropRect = null; };
window.refit     = function() { try { applyPageScaling(); } catch (e) {} };

// Manual crop editor: clear any crop, show the whole page fit-to-screen, freeze
// pan/zoom/gestures, and return the page's on-screen rect [x, y, w, h] so the
// Blazor overlay can map screen coords <-> page fractions.
window.beginCropEdit = function() {
    cropRect = null;
    const prev = pageScaling;
    pageScaling = 'fit-screen';
    applyPageScaling();
    pageScaling = prev;
    cropEditing = true;
    return [panX, panY, baseWidth * zoom, baseHeight * zoom];
};
window.endCropEdit = function() { cropEditing = false; };

// Display rotation (clockwise, multiples of 90). Render functions read pageRotation.
// Clears the page cache so cached (old-rotation) pages aren't served after a change.
window.setRotation = function(deg) {
    pageRotation = ((Math.round((deg || 0) / 90) * 90) % 360 + 360) % 360;
    try { _pageCache.clear(); } catch (e) {}
};

// Detect the content bounding box of the current page (non-white region), as
// [l, t, r, b] fractions, or null when there's nothing to crop.
window.autoDetectCrop = function() {
    try {
        if (!canvas || !baseWidth || !baseHeight) return null;
        const sw = Math.min(canvas.width, 400);
        const sh = Math.max(1, Math.round(canvas.height * sw / canvas.width));
        const tmp = makeCanvas(sw, sh);
        const tctx = tmp.getContext("2d", { willReadFrequently: true });
        tctx.drawImage(canvas, 0, 0, sw, sh);
        const img = tctx.getImageData(0, 0, sw, sh);
        return _detectContentBox(img.data, sw, sh, 245, 0.015);
    } catch (e) { return null; }
};

// Batch: render each page small and detect its content box. Returns an array where
// element i is the [l,t,r,b] crop for page i+1, or [] when there's nothing to crop;
// null if aborted. Renders to throwaway canvases, so the on-screen page is untouched.
window.autoDetectCropAll = async function() {
    try {
        if (!pdfDoc) return null;
        const gen = _renderGen;
        const out = [];
        for (let i = 1; i <= pdfDoc.numPages; i++) {
            const page = await pdfDoc.getPage(i);
            if (gen !== _renderGen) return null;   // navigated away → abort
            const vp1 = page.getViewport({ scale: 1 });
            const scale = Math.min(2, 500 / vp1.width);
            const vp = page.getViewport({ scale });
            const w = Math.ceil(vp.width), h = Math.ceil(vp.height);
            const tmp = makeCanvas(w, h);
            const tctx = tmp.getContext("2d", { willReadFrequently: true });
            tctx.fillStyle = "white"; tctx.fillRect(0, 0, w, h);
            await page.render({ canvasContext: tctx, viewport: vp }).promise;
            if (gen !== _renderGen) return null;
            const img = tctx.getImageData(0, 0, w, h);
            const box = _detectContentBox(img.data, w, h, 245, 0.015);
            out.push(box || []);
        }
        return out;
    } catch (e) { return null; }
};

// Pure bounding-box scan (kept separate so it can be unit-tested).
function _detectContentBox(data, sw, sh, thresh, padFrac) {
    let minX = sw, minY = sh, maxX = -1, maxY = -1;
    for (let y = 0; y < sh; y++) {
        for (let x = 0; x < sw; x++) {
            const i = (y * sw + x) * 4;
            if (data[i + 3] <= 10) continue;   // transparent = background
            const lum = data[i] * 0.299 + data[i + 1] * 0.587 + data[i + 2] * 0.114;
            if (lum < thresh) {
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
    }
    if (maxX < minX || maxY < minY) return null;          // blank page
    const padX = sw * padFrac, padY = sh * padFrac;
    const l = Math.max(0, (minX - padX) / sw);
    const t = Math.max(0, (minY - padY) / sh);
    const r = Math.min(1, (maxX + 1 + padX) / sw);
    const b = Math.min(1, (maxY + 1 + padY) / sh);
    if (r - l > 0.98 && b - t > 0.98) return null;        // essentially full page
    return [l, t, r, b];
}

function clampPan() {
    const vw = window.innerWidth;
    const w  = _cw() * zoom, h = _ch() * zoom;
    const hdrH  = getHeaderHeight();
    const viewH = availViewH();
    const ox = _cox() * zoom, oy = _coy() * zoom;

    if (w <= vw) panX = (vw - w) / 2 - ox;
    else panX = Math.min(-ox, Math.max(vw - w - ox, panX));

    if (h <= viewH) {
        panY = hdrH + (viewH - h) / 2 - oy;
    } else if (halfSide !== null) {
        panY = Math.max(hdrH - h / 2 - oy, Math.min(hdrH - oy, panY));
    } else {
        panY = Math.min(hdrH - oy, Math.max(hdrH + viewH - h - oy, panY));
    }
}

function smoothZoomTo(targetZoom, cx, cy) {
    if (cx === undefined) cx = window.innerWidth / 2;
    if (cy === undefined) cy = window.innerHeight / 2;
    stopInertia();

    const sZ = zoom, sPX = panX, sPY = panY;
    const ratio = targetZoom / sZ;
    let ePX = cx - (cx - sPX) * ratio;
    let ePY = cy - (cy - sPY) * ratio;

    const vw = window.innerWidth;
    const w = _cw() * targetZoom, h = _ch() * targetZoom;
    const hdrH = getHeaderHeight(), viewH = availViewH();
    const ox = _cox() * targetZoom, oy = _coy() * targetZoom;

    if (w <= vw) ePX = (vw - w) / 2 - ox;
    else ePX = Math.min(-ox, Math.max(vw - w - ox, ePX));
    if (h <= viewH) ePY = hdrH + (viewH - h) / 2 - oy;
    else if (halfSide !== null) ePY = Math.max(hdrH - h / 2 - oy, Math.min(hdrH - oy, ePY));
    else ePY = Math.min(hdrH - oy, Math.max(hdrH + viewH - h - oy, ePY));

    const dur = 250, t0 = performance.now();
    (function step(now) {
        let t = Math.min(1, (now - t0) / dur);
        t = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
        zoom = sZ  + (targetZoom - sZ) * t;
        panX = sPX + (ePX - sPX) * t;
        panY = sPY + (ePY - sPY) * t;
        applyView();
        if (t < 1) requestAnimationFrame(step);
    })(performance.now());
}

// Double-tap zoom: toggle between fit-to-width and a zoomed-in view centred on the tap point.
window.toggleZoomAt = function(sx, sy) {
    if (!baseWidth) return;
    // single/two use the page-scaling fit (_baseZoom, set by applyPageScaling); half-turn and
    // scroll fits aren't set there, but getFitZoom() returns their correct fit — so use it.
    const base = (_displayMode === 'single' || _displayMode === 'two') ? _baseZoom : getFitZoom();
    if (zoom > base * 1.15) {
        smoothZoomTo(base, window.innerWidth / 2, window.innerHeight / 2);   // zoomed in → back to fit
    } else {
        smoothZoomTo(Math.min(base * 2.5, 5), sx, sy);   // zoom in, centred on the tapped point
    }
};

window.getWindowWidth = () => window.innerWidth;

// Composite the current page — PDF + highlights + annotations (pen/stamps/text/hairpins/
// shapes) — into a PNG data URL. Excludes the link-point overlay (navigation UI). The PDF
// canvas is already opaque white, but we fill white first as a safety net. Returns null if
// nothing suitable is rendered.
window.exportPagePng = function() {
    try {
        if (!canvas || !canvas.width || !canvas.height) return null;
        const w = canvas.width, h = canvas.height;
        if (w < 2 || h < 2) return null;
        const out = makeCanvas(w, h);
        const o = out.getContext('2d');
        o.fillStyle = '#ffffff'; o.fillRect(0, 0, w, h);
        o.drawImage(canvas, 0, 0);
        if (markerCanvas && markerCanvas.width) o.drawImage(markerCanvas, 0, 0);
        if (noteCanvas && noteCanvas.width)     o.drawImage(noteCanvas, 0, 0);
        return out.toDataURL('image/png');
    } catch (e) { return null; }
};

// Web fallback: trigger a browser download of a data URL (MAUI uses the native share service).
window.downloadDataUrl = function(dataUrl, filename) {
    try {
        const a = document.createElement('a');
        a.href = dataUrl; a.download = filename || 'page.png';
        document.body.appendChild(a); a.click(); a.remove();
        return true;
    } catch (e) { return false; }
};

// ── Freehand-notes backup / restore ─────────────────────────────────
// Pen/marker/stamp/text/hairpin/shape annotations live ONLY in this browser's
// localStorage, under bsv2_<pdfKey>_p<N> / _scroll-v / _scroll-h. Bookmarks,
// link points and tempo are in the DB; these notes have no other copy, so a
// cleared cache or a new device loses them. The helpers below let the user keep
// a copy in a file and bring it back.
const _BSV2_PREFIX = 'bsv2_';

window.countAnnotationKeys = function() {
    let n = 0;
    try {
        for (let i = 0; i < localStorage.length; i++) {
            const k = localStorage.key(i);
            if (k && k.indexOf(_BSV2_PREFIX) === 0) n++;
        }
    } catch (e) {}
    return n;
};

// Returns a JSON string { v, app, exported, count, data:{ key:value } } holding
// every bsv2_ entry, or null if there are none / on error.
window.exportAnnotationsJson = function() {
    try {
        const data = {};
        let n = 0;
        for (let i = 0; i < localStorage.length; i++) {
            const k = localStorage.key(i);
            if (k && k.indexOf(_BSV2_PREFIX) === 0) {
                const v = localStorage.getItem(k);
                if (v != null) { data[k] = v; n++; }
            }
        }
        if (n === 0) return null;
        return JSON.stringify({ v: 1, app: 'BasicSheets', exported: new Date().toISOString(), count: n, data: data });
    } catch (e) { return null; }
};

// Writes back every bsv2_ entry from a backup JSON string (additive/overwrite:
// documents not present in the file keep their current notes). Returns the number
// of entries written, or -1 if the text is not a valid backup.
window.importAnnotationsJson = function(json) {
    try {
        const obj = JSON.parse(json);
        const data = obj && obj.data;
        if (!data || typeof data !== 'object') return -1;
        let n = 0;
        for (const k in data) {
            if (k.indexOf(_BSV2_PREFIX) !== 0) continue;   // never write outside our namespace
            const v = data[k];
            if (typeof v === 'string') { try { localStorage.setItem(k, v); n++; } catch (e) {} }
        }
        return n;
    } catch (e) { return -1; }
};

// Opens the OS file picker (works in the MAUI WebView and on the web host), reads
// the chosen .json and imports it. Resolves to the number of entries written
// (0 if cancelled, -1 on a bad file). Pure JS, so no native file service needed.
window.importAnnotationsPick = function() {
    return new Promise(function(resolve) {
        try {
            const inp = document.createElement('input');
            inp.type = 'file';
            inp.accept = 'application/json,.json';
            inp.style.position = 'fixed'; inp.style.left = '-9999px';
            let settled = false;
            const done = function(val) {
                if (settled) return;
                settled = true;
                try { inp.remove(); } catch (e) {}
                resolve(val);
            };
            inp.onchange = function() {
                const f = inp.files && inp.files[0];
                if (!f) return done(0);
                const r = new FileReader();
                r.onload  = function() { done(window.importAnnotationsJson(String(r.result || ''))); };
                r.onerror = function() { done(-1); };
                r.readAsText(f);
            };
            // Cancel detection: if the dialog is dismissed, onchange never fires;
            // when focus returns and no file was chosen, resolve 0.
            window.addEventListener('focus', function onF() {
                window.removeEventListener('focus', onF);
                setTimeout(function() { if (!settled && (!inp.files || !inp.files.length)) done(0); }, 500);
            }, { once: true });
            document.body.appendChild(inp);
            inp.click();
        } catch (e) { resolve(-1); }
    });
};

// ── PDF embedded outline (table of contents) ────────────────────────
// Flattens the PDF's own bookmark tree to [{title, page, level}] with
// 1-based page numbers. Dest-less nodes get page 0 (Blazor filters them out).
window.getPdfOutline = async function() {
    if (!pdfDoc || typeof pdfDoc.getOutline !== 'function') return [];
    let outline;
    try { outline = await pdfDoc.getOutline(); } catch { return []; }
    if (!outline || !outline.length) return [];
    const out = [];
    async function resolvePage(dest) {
        try {
            let d = dest;
            if (typeof d === 'string') d = await pdfDoc.getDestination(d);
            if (!Array.isArray(d) || !d.length || d[0] == null || typeof d[0] !== 'object') return 0;
            const idx = await pdfDoc.getPageIndex(d[0]);
            return (idx | 0) + 1;
        } catch { return 0; }
    }
    async function walk(nodes, level) {
        for (const n of nodes) {
            let page = 0;
            if (n && n.dest != null) page = await resolvePage(n.dest);
            out.push({ title: ((n && n.title) || '').trim() || '—', page: page, level: level });
            if (n && n.items && n.items.length) await walk(n.items, level + 1);
        }
    }
    try { await walk(outline, 0); } catch {}
    return out;
};


// ═══════════════════════════════════════════════════════════════════
//  Interaction — unified touch + mouse (MobileSheets-style)
// ═══════════════════════════════════════════════════════════════════

let _currentBg = null;
let _clickCapture = null;

// Keyboard / page-turner-pedal navigation. Most Bluetooth foot pedals (AirTurn,
// PageFlip, …) send key events — map them to gestures. The map is user-remappable
// (localStorage 'bs_keymap'); a capture mode binds the next key to a chosen action.
const DEFAULT_KEYMAP = {
    'ArrowRight': 'next_page', 'ArrowDown': 'next_page', 'PageDown': 'next_page', ' ': 'next_page', 'Spacebar': 'next_page',
    'ArrowLeft': 'prev_page', 'ArrowUp': 'prev_page', 'PageUp': 'prev_page',
    'Home': 'first_page', 'End': 'last_page',
    'b': 'add_bookmark', 'B': 'add_bookmark', 'm': 'toggle_metronome', 'M': 'toggle_metronome', 'p': 'toggle_audio', 'P': 'toggle_audio',
    '/': 'open_search', 'f': 'open_search', 'F': 'open_search',
    'g': 'open_grid', 'G': 'open_grid',
    '.': 'next_match', ',': 'prev_match',
    'l': 'toggle_lock', 'L': 'toggle_lock'
};
let _keyMap = null, _captureAction = null;
function _loadKeyMap() {
    try { const raw = localStorage.getItem('bs_keymap'); if (raw) { const m = JSON.parse(raw); if (m && typeof m === 'object') return Object.assign({}, DEFAULT_KEYMAP, m); } } catch {}   // merge so new default keys reach existing users
    return Object.assign({}, DEFAULT_KEYMAP);
}
function _saveKeyMap() { try { localStorage.setItem('bs_keymap', JSON.stringify(_keyMap)); } catch {} }
window.getKeyMap     = function() { if (!_keyMap) _keyMap = _loadKeyMap(); try { return JSON.stringify(_keyMap); } catch { return '{}'; } };
window.setKeyCapture = function(action) { _captureAction = action || null; };
window.resetKeyMap   = function() { _keyMap = Object.assign({}, DEFAULT_KEYMAP); try { localStorage.removeItem('bs_keymap'); } catch {} };

function onKeyNav(e) {
    if (e.ctrlKey || e.metaKey || e.altKey) return;        // leave browser/OS shortcuts alone
    const t = e.target;
    if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable)) return;
    if (!_keyMap) _keyMap = _loadKeyMap();
    if (_captureAction) {                                  // settings "assign a key" mode
        e.preventDefault();
        if (e.key !== 'Escape') { _keyMap[e.key] = _captureAction; _saveKeyMap(); }
        const a = (e.key !== 'Escape') ? _captureAction : '';
        _captureAction = null;
        if (dotNetRef) dotNetRef.invokeMethodAsync('OnKeyCaptured', e.key, a).catch(() => {});
        return;
    }
    if (editMode || cropEditing) return;                   // don't hijack while annotating / cropping
    const action = _keyMap[e.key];
    if (!action) return;
    e.preventDefault();
    if (dotNetRef) dotNetRef.invokeMethodAsync('OnGesture', action).catch(() => {});
}
// Bound globally (covers canvas AND text/ChordPro songs); remove-then-add is idempotent.
window.initKeyNav    = function() { document.removeEventListener('keydown', onKeyNav); document.addEventListener('keydown', onKeyNav); };
window.disposeKeyNav = function() { document.removeEventListener('keydown', onKeyNav); };

function teardownListeners() {
    if (_currentBg) {
        if (_clickCapture) _currentBg.removeEventListener('click', _clickCapture, true);
        _currentBg.removeEventListener('touchstart',  onTouchStart);
        _currentBg.removeEventListener('touchmove',   onTouchMove);
        _currentBg.removeEventListener('touchend',    onTouchEnd);
        _currentBg.removeEventListener('touchcancel', onTouchEnd);
        _currentBg = null;
    }
    document.removeEventListener('wheel', onWheelZoom);
    document.removeEventListener('mousedown', onMouseDown);
    document.removeEventListener('mousemove', onMouseMove);
    document.removeEventListener('mouseup',   onMouseUp);
    window.removeEventListener('resize', onViewResize);
}

function setupInteraction() {
    const bg = document.querySelector('.vbg');
    if (!bg) return;
    _currentBg = bg;

    _clickCapture = function(e) {
        if (_suppressClick) { e.stopPropagation(); _suppressClick = false; }
    };
    bg.addEventListener('click', _clickCapture, true);

    bg.addEventListener('touchstart',  onTouchStart,  { passive: true });
    bg.addEventListener('touchmove',   onTouchMove,   { passive: false });
    bg.addEventListener('touchend',    onTouchEnd,    { passive: true });
    bg.addEventListener('touchcancel', onTouchEnd,    { passive: true });

    document.addEventListener('wheel', onWheelZoom, { passive: false });

    document.addEventListener('mousedown', onMouseDown);
    document.addEventListener('mousemove', onMouseMove);
    document.addEventListener('mouseup',   onMouseUp);

    window.addEventListener('resize', onViewResize);
}

// ── Touch ──────────────────────────────────────────────────────────

function onTouchStart(e) {
    if (cropEditing) return;
    if (editMode) { _editSwipeStart(e); return; }   // edit mode: one finger draws, two fingers turn pages
    stopInertia();
    if (e.touches.length === 1) {
        _touchDragging = true;
        _touchMoved    = false;
        _touchStartX   = e.touches[0].clientX;
        _touchStartY   = e.touches[0].clientY;
        _touchInitPanX = panX;
        _touchInitPanY = panY;
        _velX = _velY  = 0;
        _lastTouchX    = _touchStartX;
        _lastTouchY    = _touchStartY;
        _lastTouchTime = Date.now();
    }
    if (e.touches.length >= 2) {
        _wasPinching = false;
        _twoFingerTapStart = Date.now();
    }
    lastDist = null;
}

function onTouchMove(e) {
    if (cropEditing) return;
    if (editMode) { _editSwipeMove(e); return; }

    if (e.touches.length === 2) {
        e.preventDefault();
        _touchDragging = false;
        _wasPinching = true;
        _autoScrollInterrupt();
        const a = e.touches[0], b = e.touches[1];
        const dist = Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY);
        const cx = (a.clientX + b.clientX) / 2, cy = (a.clientY + b.clientY) / 2;
        if (lastDist && lastDist > 0) {
            const oz = zoom;
            zoom = clampZoom(zoom * (dist / lastDist));
            panX = cx - (cx - panX) * (zoom / oz);
            panY = cy - (cy - panY) * (zoom / oz);
            clampPan(); applyView();
        }
        lastDist = dist;
        _suppressClick = true;
        return;
    }

    if (e.touches.length === 1 && _touchDragging) {
        const dx = e.touches[0].clientX - _touchStartX;
        const dy = e.touches[0].clientY - _touchStartY;
        if (!_touchMoved && Math.hypot(dx, dy) > DRAG_THRESHOLD) _touchMoved = true;
        if (_touchMoved) {
            _suppressClick = true;
            _autoScrollInterrupt();
            if (shouldPan()) {
                e.preventDefault();
                panX = _touchInitPanX + dx;
                panY = _touchInitPanY + dy;
                clampPan(); applyView();

                const now = Date.now(), dt = now - _lastTouchTime;
                if (dt > 0 && dt < 100) {
                    _velX = (e.touches[0].clientX - _lastTouchX) / dt * 16;
                    _velY = (e.touches[0].clientY - _lastTouchY) / dt * 16;
                }
                _lastTouchX    = e.touches[0].clientX;
                _lastTouchY    = e.touches[0].clientY;
                _lastTouchTime = now;
            }
        }
    }
}

// A fast horizontal swipe turns the page — but only in paged modes and only when the page
// can't pan horizontally (at fit). When zoomed in, a horizontal drag pans instead, so the
// gesture never fights panning. Returns true if it turned a page.
function _maybeSwipeTurn(dx, dy) {
    if (_displayMode !== 'single' && _displayMode !== 'two' && _displayMode !== 'half-turn') return false;
    if (_cw() * zoom > window.innerWidth + 2) return false;                 // pannable horizontally → it's a pan
    if (Math.abs(dx) < 55 || Math.abs(dx) < Math.abs(dy) * 1.4) return false;
    _suppressClick = true;
    if (dotNetRef) dotNetRef.invokeMethodAsync('OnGesture', dx < 0 ? 'next_page' : 'prev_page');
    return true;
}

// ── Two-finger swipe to turn pages while in EDIT mode ────────────────
// One finger draws; the moment a second finger lands we drop the stroke the first finger
// began and start a page-turn candidate. If the two fingers then slide sideways together
// (not a pinch), the page turns. Drawing stays locked until every finger lifts, so the
// gesture never leaves a stray mark.
function _cancelActiveDraw() {
    drawing = false;
    liveStroke = null;
    markerPoints = [];
    hairpinStart = null; liveHairpin = null;
    shapeStart = null; liveShape = null;
    liveStamp = null;
    moveTgt = null;
    try { redrawPenLayer(); redrawVectorLayer(); } catch (e) {}
}
function _editSwipeStart(e) {
    if (!e.touches || e.touches.length !== 2) return;
    _cancelActiveDraw();
    _drawLocked = true;
    const a = e.touches[0], b = e.touches[1];
    const cx = (a.clientX + b.clientX) / 2, cy = (a.clientY + b.clientY) / 2;
    _editSwipe = { sx: cx, sy: cy, lx: cx, ly: cy,
                   d0: Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY), maxDD: 0 };
}
function _editSwipeMove(e) {
    if (!_editSwipe || !e.touches || e.touches.length !== 2) return;
    e.preventDefault();
    const a = e.touches[0], b = e.touches[1];
    _editSwipe.lx = (a.clientX + b.clientX) / 2;
    _editSwipe.ly = (a.clientY + b.clientY) / 2;
    const d = Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY);
    _editSwipe.maxDD = Math.max(_editSwipe.maxDD, Math.abs(d - _editSwipe.d0));   // track pinch amount
}
function _editSwipeEnd(e) {
    if (_editSwipe && e.touches.length < 2) {
        const s = _editSwipe; _editSwipe = null;
        const dx = s.lx - s.sx, dy = s.ly - s.sy;
        // mostly-horizontal slide and NOT a pinch → turn the page
        if (s.maxDD < 60 && Math.abs(dx) > 55 && Math.abs(dx) > Math.abs(dy) * 1.4) {
            _suppressClick = true;
            if (dotNetRef) dotNetRef.invokeMethodAsync('OnGesture', dx < 0 ? 'next_page' : 'prev_page');
        }
    }
    if (e.touches.length === 0) _drawLocked = false;   // all fingers up → drawing allowed again
}

function onTouchEnd(e) {
    if (cropEditing) return;
    if (editMode) { _editSwipeEnd(e); return; }
    if (e.touches.length === 0) {
        if (_wasPinching === false && _twoFingerTapStart > 0 && !lastDist) {
            const elapsed = Date.now() - _twoFingerTapStart;
            if (elapsed < 300) {
                _twoFingerTapStart = 0;
                _suppressClick = true;
                applyPageScaling();
                return;
            }
        }
        _twoFingerTapStart = 0;
        if (_touchDragging && _touchMoved && !lastDist && e.changedTouches && e.changedTouches.length) {
            const dx = e.changedTouches[0].clientX - _touchStartX;
            const dy = e.changedTouches[0].clientY - _touchStartY;
            if (_maybeSwipeTurn(dx, dy)) { _touchDragging = false; return; }
        }
        if (lastDist) {
            const fit = getFitZoom();
            if (zoom < fit * 1.08 && zoom > fit * 0.85) smoothZoomTo(fit);
            lastDist = null;
        }
        if (_touchDragging && _touchMoved && shouldPan()) startInertia();
        _touchDragging = false;
    } else {
        lastDist = null;
    }
}

// ── Inertia ────────────────────────────────────────────────────────

function startInertia() {
    if (Math.abs(_velX) < 0.5 && Math.abs(_velY) < 0.5) return;
    (function step() {
        _velX *= 0.94; _velY *= 0.94;
        if (Math.abs(_velX) < 0.1 && Math.abs(_velY) < 0.1) { _inertiaId = null; return; }
        panX += _velX; panY += _velY;
        clampPan(); applyView();
        _inertiaId = requestAnimationFrame(step);
    })();
}

function stopInertia() {
    if (_inertiaId) { cancelAnimationFrame(_inertiaId); _inertiaId = null; }
    _velX = _velY = 0;
}

// ── Auto-scroll (smooth continuous vertical scroll) ─────────────────
// Distinct from the timed "auto page turn": this glides panY downward at a
// constant screen-px/sec until the content bottom, then asks Blazor
// (OnAutoScrollEnd) whether to advance a page and resume, or stop. A user
// pan/zoom pauses it (OnAutoScrollState false).
let _autoScrollId = null;
let _autoScrollSpeed = 40;      // screen px per second
let _autoScrollLast = 0;
let _autoScrollMoved = false;
try { const _s = parseFloat(localStorage.getItem('bs_autoscroll_speed')); if (!isNaN(_s) && _s > 0) _autoScrollSpeed = _s; } catch {}

window.isAutoScrolling    = function() { return _autoScrollId !== null; };
window.getAutoScrollSpeed = function() { return _autoScrollSpeed; };
window.setAutoScrollSpeed = function(pxPerSec) {
    const v = Number(pxPerSec);
    if (!isNaN(v) && v > 0) _autoScrollSpeed = Math.max(4, Math.min(300, v));
    try { localStorage.setItem('bs_autoscroll_speed', String(_autoScrollSpeed)); } catch {}
    return _autoScrollSpeed;
};
// Total vertical scroll distance (screen px) in scroll-v — for timed auto-scroll.
window.getScrollRange = function() {
    if (_displayMode !== 'scroll-v' || !baseHeight) return 0;
    return Math.max(0, baseHeight * zoom - availViewH());
};

window.startAutoScroll = function(pxPerSec) {
    if (pxPerSec != null) window.setAutoScrollSpeed(pxPerSec);
    if (_autoScrollId !== null) return true;           // already running
    if (editMode || cropEditing) return false;
    stopInertia();
    _autoScrollMoved = false;
    _autoScrollLast  = performance.now();
    _autoScrollId    = requestAnimationFrame(_autoScrollStep);
    return true;
};
window.stopAutoScroll = function() { _autoScrollStop(); };

function _autoScrollStop() {
    if (_autoScrollId !== null) { cancelAnimationFrame(_autoScrollId); _autoScrollId = null; }
}

function _autoScrollStep(now) {
    if (_autoScrollId === null) return;
    if (editMode || cropEditing) { _autoScrollStop(); _autoScrollNotify(false); return; }
    const dt = Math.min(0.1, (now - _autoScrollLast) / 1000);
    _autoScrollLast = now;
    if (dt <= 0) { _autoScrollId = requestAnimationFrame(_autoScrollStep); return; }  // skip zero-length frames
    const before = panY;
    panY -= _autoScrollSpeed * dt;     // scroll down = content up = panY decreasing
    clampPan(); applyView();
    if (panY < before - 0.01) {        // moved → keep going
        _autoScrollMoved = true;
        _autoScrollId = requestAnimationFrame(_autoScrollStep);
    } else {                           // clamp blocked it → bottom reached (or nothing to scroll)
        _autoScrollReachedEnd();
    }
}

function _autoScrollNotify(running) {
    if (dotNetRef) dotNetRef.invokeMethodAsync('OnAutoScrollState', running).catch(function() {});
}
function _autoScrollReachedEnd() {
    const moved = _autoScrollMoved;
    _autoScrollStop();
    if (dotNetRef) dotNetRef.invokeMethodAsync('OnAutoScrollEnd', moved).catch(function() { _autoScrollNotify(false); });
    else _autoScrollNotify(false);
}
function _autoScrollInterrupt() {
    if (_autoScrollId !== null) { _autoScrollStop(); _autoScrollNotify(false); }
}

// ── Mouse ──────────────────────────────────────────────────────────

function onWheelZoom(e) {
    if (!canvas || editMode || cropEditing) return;
    e.preventDefault();
    _autoScrollInterrupt();
    const oz = zoom;
    zoom = clampZoom(zoom * (e.deltaY < 0 ? 1.12 : 0.88));
    panX = e.clientX - (e.clientX - panX) * (zoom / oz);
    panY = e.clientY - (e.clientY - panY) * (zoom / oz);
    clampPan(); applyView();
}

function onMouseDown(e) {
    if (editMode || cropEditing || e.button !== 0) return;
    _panDragging = true;
    _panStartX = e.clientX - panX; _panStartY = e.clientY - panY;
    _mouseRawStartX = e.clientX; _mouseRawStartY = e.clientY;
    document.body.style.cursor = 'grabbing';
}

function onMouseMove(e) {
    if (!_panDragging) return;
    _autoScrollInterrupt();
    panX = e.clientX - _panStartX; panY = e.clientY - _panStartY;
    clampPan(); applyView();
}

function onMouseUp(e) {
    if (!_panDragging) return;
    _panDragging = false;
    document.body.style.cursor = editMode ? 'crosshair' : 'grab';
    if (e) {
        const dx = e.clientX - _mouseRawStartX, dy = e.clientY - _mouseRawStartY;
        _maybeSwipeTurn(dx, dy);
    }
}

// ── Resize ─────────────────────────────────────────────────────────

function measureChrome() {
    const hdr = document.getElementById('vhdr');
    const bot = document.getElementById('vbottom');
    _chromeTop    = (_chromeTopVis && hdr) ? hdr.offsetHeight : 0;
    _chromeBottom = (_chromeBotVis && bot) ? bot.offsetHeight : 0;
}

function onViewResize() {
    if (!baseWidth) return;
    measureChrome();
    if (_displayMode === 'scroll-v') { fitToWidth(); return; }
    if (_displayMode === 'half-turn') { _applyHalfTurnFit(_halfTurnTop); return; }
    if (!isZoomed()) applyPageScaling();
    else { clampPan(); applyView(); }
}

// Top header / bottom slider visibility → reserve that space so the sheet fits the
// area that remains instead of being hidden behind the bars.
window.updateChromeInsets = function(topVisible, bottomVisible) {
    _chromeTopVis = !!topVisible;
    _chromeBotVis = !!bottomVisible;
    if (baseWidth) onViewResize();
};


// ═══════════════════════════════════════════════════════════════════
//  Edit mode / tool controls
// ═══════════════════════════════════════════════════════════════════

window.setEditMode = function(on) {
    editMode = on;
    if (!on) {
        liveStamp = null; liveStroke = null;
        stylusAutoEnteredEdit = false;
        // Block stylus auto-re-entry until the pen physically leaves and approaches
        // again. Otherwise tapping "X" with the pen still hovering would instantly
        // snap back into edit mode (you could never get out with the pen).
        stylusReentryBlocked = true;
    }
    noteCanvas.style.pointerEvents = on ? "auto" : "none";
    document.body.style.cursor     = on ? "crosshair" : "grab";
};

// setTool: sets tool, restores saved color + size, returns saved color (or null)
window.setTool = function(t) {
    tool = t;
    liveStamp = null;
    const savedColor = toolColors[t] || null;
    if (savedColor) penColor = savedColor;
    const savedSize = toolSizes[t] || null;
    if (savedSize) penSize = savedSize;
    return savedColor;
};

// getToolSize: returns the saved size for a tool (or null)
window.getToolSize = function(t) {
    return toolSizes[t] || null;
};

// setToolDirect: push tool + color + size from C# — no return value, null-safe
window.setToolDirect = function(t, color, size) {
    tool = t;
    liveStamp = null;
    _penBtnEraser = false;   // an explicit tool choice clears the pen-button eraser override
    penColor = color;
    penSize  = size;
    toolColors[t] = color;
    toolSizes[t]  = size;
    try { localStorage.setItem("bs_tool_colors", JSON.stringify(toolColors)); } catch {}
    try { localStorage.setItem("bs_tool_sizes",  JSON.stringify(toolSizes));  } catch {}
};

// getToolStates: returns all tool colors + sizes as a JSON string (string? is null-safe in C#)
window.getToolStates = function() {
    try { return JSON.stringify({ colors: toolColors, sizes: toolSizes }); } catch { return null; }
};

// setColor: saves color for current tool + persists
window.setColor = function(c) {
    penColor = c;
    toolColors[tool] = c;
    try { localStorage.setItem("bs_tool_colors", JSON.stringify(toolColors)); } catch {}
};

// setPenSize: sets pen size (temporary, used by C# via ActiveSize)
window.setPenSize = s => { penSize = s; };

// setToolSize: sets pen size for current tool + persists per-tool
window.setToolSize = function(s) {
    penSize = s;
    toolSizes[tool] = s;
    try { localStorage.setItem("bs_tool_sizes", JSON.stringify(toolSizes)); } catch {}
};

window.setPdfDotNetRef = r => { dotNetRef = r; };


// ═══════════════════════════════════════════════════════════════════
//  Panel drag — 2D, saves {top,left} to localStorage
// ═══════════════════════════════════════════════════════════════════

window.initPanelDrag = function(ref) {
    panelRef = ref;
    if (panelDragObserver) panelDragObserver.disconnect();
    tryAttachPanelDrag();
    panelDragObserver = new MutationObserver(tryAttachPanelDrag);
    panelDragObserver.observe(document.body, { childList: true, subtree: true });
    if (!_panelResizeBound) {
        _panelResizeBound = true;
        window.addEventListener('resize', () => {
            if (document.getElementById("spanel")) window.clampPanelIntoView();
        });
    }
};

function tryAttachPanelDrag() {
    const handle = document.getElementById("spanel-drag");
    if (!handle || handle._dragAttached) return;
    handle._dragAttached = true;

    let draggingPanel = false, moved = false;
    let startX = 0, startY = 0, startTop = 120, startLeft = 0;
    const TAP_SLOP = 10;   // movement below this = a tap (toggle), above = a drag (move)

    handle.addEventListener("pointerdown", e => {
        e.preventDefault(); e.stopPropagation();
        draggingPanel = true; moved = false;
        handle.setPointerCapture(e.pointerId);
        const panel = document.getElementById("spanel");
        startTop  = parseFloat(panel?.style.top)  || 120;
        startLeft = parseFloat(panel?.style.left) || (window.innerWidth - 264);
        startX = e.clientX; startY = e.clientY;
    });
    handle.addEventListener("pointermove", e => {
        if (!draggingPanel) return;
        const panel = document.getElementById("spanel");
        if (!panel) return;
        if (!moved && Math.hypot(e.clientX - startX, e.clientY - startY) < TAP_SLOP) return;
        moved = true;
        // Clamp against the panel's actual size so no edge can leave the screen.
        const maxLeft = Math.max(0, window.innerWidth  - panel.offsetWidth);
        const maxTop  = Math.max(0, window.innerHeight - panel.offsetHeight);
        const top  = Math.max(0, Math.min(maxTop,  startTop  + (e.clientY - startY)));
        const left = Math.max(0, Math.min(maxLeft, startLeft + (e.clientX - startX)));
        panel.style.top  = top  + "px";
        panel.style.left = left + "px";
    });
    handle.addEventListener("pointerup", () => {
        if (!draggingPanel) return;
        draggingPanel = false;
        if (moved) {
            persistPanelPos();                 // it was a drag → save the new position
        } else if (panelRef) {
            // it was a tap → collapse / expand
            panelRef.invokeMethodAsync("TogglePanelCollapsedFromJs").catch(() => {});
        }
    });
    handle.addEventListener("pointercancel", () => { draggingPanel = false; });

    // Pull into view as soon as the panel appears (handles a saved position from a
    // bigger window or a different orientation).
    requestAnimationFrame(() => window.clampPanelIntoView());
}

function persistPanelPos() {
    const panel = document.getElementById("spanel");
    if (!panel) return;
    const top  = Math.round(parseFloat(panel.style.top)  || 0);
    const left = Math.round(parseFloat(panel.style.left) || 0);
    try { localStorage.setItem("bs_panel_pos", JSON.stringify({ top, left })); } catch {}
    if (panelRef) panelRef.invokeMethodAsync("UpdatePanelPos", top, left).catch(() => {});
}

// Keep the panel fully inside the viewport (after drag, resize, or collapse/expand).
window.clampPanelIntoView = function() {
    const panel = document.getElementById("spanel");
    if (!panel) return;
    const maxLeft = Math.max(0, window.innerWidth  - panel.offsetWidth);
    const maxTop  = Math.max(0, window.innerHeight - panel.offsetHeight);
    const top  = Math.max(0, Math.min(maxTop,  parseFloat(panel.style.top)  || 0));
    const left = Math.max(0, Math.min(maxLeft, parseFloat(panel.style.left) || 0));
    panel.style.top  = top  + "px";
    panel.style.left = left + "px";
    persistPanelPos();
};

function clampPanelPos(p) {
    const maxLeft = Math.max(0, window.innerWidth  - 120);
    const maxTop  = Math.max(0, window.innerHeight - 80);
    return {
        top:  Math.max(0, Math.min(maxTop,  p.top)),
        left: Math.max(0, Math.min(maxLeft, p.left))
    };
}
window.loadPanelPos = function() {
    try {
        const raw = localStorage.getItem("bs_panel_pos");
        if (raw) {
            const parsed = JSON.parse(raw);
            if (parsed && typeof parsed === 'object' &&
                typeof parsed.top === 'number' && typeof parsed.left === 'number')
                return clampPanelPos(parsed);
        }
    } catch {}
    const legacyTop = parseFloat(localStorage.getItem("bs_panel_top") || "120") || 120;
    return clampPanelPos({ top: legacyTop, left: window.innerWidth - 264 });
};
window.loadPanelTop  = () => window.loadPanelPos().top;
window.loadPanelLeft = () => window.loadPanelPos().left;

window.loadPanelCollapsed = () => { try { return localStorage.getItem("bs_panel_collapsed") === "1"; } catch { return false; } };
window.savePanelCollapsed = v => { try { localStorage.setItem("bs_panel_collapsed", v ? "1" : "0"); } catch {} };


// ═══════════════════════════════════════════════════════════════════
//  Corner action box (FAB) — draggable, saves {top,left} to localStorage
// ═══════════════════════════════════════════════════════════════════

let fabRef = null, fabDragObserver = null, _fabResizeBound = false;

window.initFabDrag = function(ref) {
    fabRef = ref;
    if (fabDragObserver) fabDragObserver.disconnect();
    tryAttachFabDrag();
    fabDragObserver = new MutationObserver(tryAttachFabDrag);
    fabDragObserver.observe(document.body, { childList: true, subtree: true });
    if (!_fabResizeBound) {
        _fabResizeBound = true;
        window.addEventListener('resize', () => { if (document.getElementById("vfab")) clampFabIntoView(); });
    }
};

function clampFabTo(p, fab) {
    const maxLeft = Math.max(0, window.innerWidth  - fab.offsetWidth);
    const maxTop  = Math.max(0, window.innerHeight - fab.offsetHeight);
    return { top: Math.max(0, Math.min(maxTop, p.top)), left: Math.max(0, Math.min(maxLeft, p.left)) };
}
function loadFabPosRaw() {
    try {
        const raw = localStorage.getItem("bs_fab_pos");
        if (raw) { const p = JSON.parse(raw); if (p && typeof p.top === 'number' && typeof p.left === 'number') return p; }
    } catch {}
    return null;
}
function applyFabPos(fab) {
    const pos = loadFabPosRaw();
    if (!pos) return;                 // never dragged → keep the CSS default (bottom-right)
    const c = clampFabTo(pos, fab);
    fab.style.left = c.left + "px"; fab.style.top = c.top + "px";
    fab.style.right = "auto"; fab.style.bottom = "auto";
}
function persistFabPos(fab) {
    const top  = Math.round(parseFloat(fab.style.top)  || 0);
    const left = Math.round(parseFloat(fab.style.left) || 0);
    try { localStorage.setItem("bs_fab_pos", JSON.stringify({ top, left })); } catch {}
}
function clampFabIntoView() {
    const fab = document.getElementById("vfab");
    if (!fab || !loadFabPosRaw()) return;
    const c = clampFabTo({ top: parseFloat(fab.style.top) || 0, left: parseFloat(fab.style.left) || 0 }, fab);
    fab.style.left = c.left + "px"; fab.style.top = c.top + "px";
    fab.style.right = "auto"; fab.style.bottom = "auto";
    persistFabPos(fab);
}

function tryAttachFabDrag() {
    const fab = document.getElementById("vfab");
    if (!fab || fab._dragAttached) return;
    fab._dragAttached = true;
    applyFabPos(fab);                 // re-apply a saved position whenever the FAB reappears

    let dragging = false, moved = false;
    let startX = 0, startY = 0, startTop = 0, startLeft = 0;
    const TAP_SLOP = 10;              // movement below this = a tap (open/close), above = a drag

    fab.addEventListener("pointerdown", e => {
        e.preventDefault(); e.stopPropagation();
        dragging = true; moved = false;
        try { fab.setPointerCapture(e.pointerId); } catch {}
        const r = fab.getBoundingClientRect();
        startTop = r.top; startLeft = r.left;
        startX = e.clientX; startY = e.clientY;
    });
    fab.addEventListener("pointermove", e => {
        if (!dragging) return;
        if (!moved && Math.hypot(e.clientX - startX, e.clientY - startY) < TAP_SLOP) return;
        moved = true;
        const c = clampFabTo({ top: startTop + (e.clientY - startY), left: startLeft + (e.clientX - startX) }, fab);
        fab.style.left = c.left + "px"; fab.style.top = c.top + "px";
        fab.style.right = "auto"; fab.style.bottom = "auto";
    });
    fab.addEventListener("pointerup", () => {
        if (!dragging) return;
        dragging = false;
        if (moved) persistFabPos(fab);
        else if (fabRef) fabRef.invokeMethodAsync("ToggleActionBoxFromJs").catch(() => {});
    });
    fab.addEventListener("pointercancel", () => { dragging = false; });
}


// ═══════════════════════════════════════════════════════════════════
//  Color persistence
// ═══════════════════════════════════════════════════════════════════

window.loadColors           = () => { try { return localStorage.getItem("bs_colors_v2");  } catch { return null; } };
window.saveColors           = json => { try { localStorage.setItem("bs_colors_v2", json); } catch {} };
window.loadLegacyUserColors = () => { try { return localStorage.getItem("bs_user_colors"); } catch { return null; } };
window.loadUserColors = window.loadColors;
window.saveUserColors = window.saveColors;


// ── Orientation helper ─────────────────────────────────────────────
window.isLandscape = () => window.innerWidth > window.innerHeight;


// ═══════════════════════════════════════════════════════════════════
//  Metronome — Web Audio click + visual pulse (lookahead scheduling)
// ═══════════════════════════════════════════════════════════════════
// Entirely self-contained: does not touch the viewer render / annotation /
// navigation paths. Precise audio is scheduled on the Web Audio clock; the
// visual pulse rides a setTimeout per beat (cosmetic).

let _metroCtx = null, _metroTimer = null;
let _metroNextNote = 0, _metroBeat = 0;
let _metroBpm = 100, _metroBeats = 4, _metroRunning = false;
let _metroMuted = false, _metroAccent = true, _metroSubdiv = 1;
const _METRO_LOOKAHEAD = 25;     // ms between scheduler ticks
const _METRO_AHEAD = 0.12;       // sec of audio scheduled ahead of the clock

function _metroScheduleClick(time, isDown, isMain) {
    const ctx = _metroCtx; if (!ctx || _metroMuted) return;   // muted = visual pulse only, no sound
    const main = isMain !== false;                            // sub-division clicks are higher + quieter
    const osc = ctx.createOscillator(), gain = ctx.createGain();
    osc.frequency.value = isDown ? 1600 : (main ? 1000 : 1300);
    const peak = isDown ? 0.7 : (main ? 0.45 : 0.22);
    gain.gain.setValueAtTime(0.0001, time);
    gain.gain.exponentialRampToValueAtTime(peak, time + 0.001);
    gain.gain.exponentialRampToValueAtTime(0.0001, time + 0.05);
    osc.connect(gain); gain.connect(ctx.destination);
    osc.start(time); osc.stop(time + 0.06);
}
function _metroPulse(isDown) {
    const el = document.getElementById('vmetro-dot'); if (!el) return;
    el.classList.remove('metro-on', 'metro-down');
    void el.offsetWidth;             // restart the CSS animation
    el.classList.add(isDown ? 'metro-down' : 'metro-on');
}
function _metroScheduler() {
    const ctx = _metroCtx; if (!ctx) return;
    while (_metroNextNote < ctx.currentTime + _METRO_AHEAD) {
        const isDown = _metroAccent && (_metroBeat % _metroBeats) === 0;
        _metroScheduleClick(_metroNextNote, isDown, true);
        if (_metroSubdiv > 1) {                               // extra clicks within the beat (8ths / triplets / 16ths)
            const step = (60.0 / _metroBpm) / _metroSubdiv;
            for (let k = 1; k < _metroSubdiv; k++) _metroScheduleClick(_metroNextNote + k * step, false, false);
        }
        const delayMs = Math.max(0, (_metroNextNote - ctx.currentTime) * 1000);
        setTimeout(() => { if (_metroRunning) _metroPulse(isDown); }, delayMs);
        _metroNextNote += 60.0 / _metroBpm;
        _metroBeat++;
    }
}
window.startMetronome = function(bpm, beats) {
    if (bpm)   _metroBpm   = Math.max(20, Math.min(300, bpm | 0));
    if (beats) _metroBeats = Math.max(1, Math.min(16, beats | 0));
    if (_metroRunning) return true;
    try {
        if (!_metroCtx) _metroCtx = new (window.AudioContext || window.webkitAudioContext)();
        if (_metroCtx.state === 'suspended') _metroCtx.resume();
    } catch (e) { return false; }
    _metroRunning = true; _metroBeat = 0;
    _metroNextNote = _metroCtx.currentTime + 0.06;
    _metroTimer = setInterval(_metroScheduler, _METRO_LOOKAHEAD);
    return true;
};
window.stopMetronome = function() {
    _metroRunning = false;
    if (_metroTimer) { clearInterval(_metroTimer); _metroTimer = null; }
};
window.setMetronomeBpm   = function(bpm)   { _metroBpm   = Math.max(20, Math.min(300, bpm | 0)); return _metroBpm; };
window.setMetronomeBeats = function(beats) { _metroBeats = Math.max(1, Math.min(16, beats | 0)); return _metroBeats; };
window.isMetronomeRunning = function() { return _metroRunning; };
window.setMetronomeMuted  = function(on) { _metroMuted  = !!on; };
window.setMetronomeAccent = function(on) { _metroAccent = (on !== false); };
window.setMetronomeSubdiv = function(n) { _metroSubdiv = Math.max(1, Math.min(4, (n | 0) || 1)); };


// ═══════════════════════════════════════════════════════════════════
//  Audio backing-track playback (self-contained; no viewer coupling)
// ═══════════════════════════════════════════════════════════════════
let _audioEl = null, _audioUrl = null;
function _audio() { if (!_audioEl) _audioEl = document.getElementById('vaudio'); return _audioEl; }

window.loadAudioData = function(bytes, mime) {
    const el = _audio(); if (!el) return false;
    try {
        if (_audioUrl) { try { URL.revokeObjectURL(_audioUrl); } catch {} _audioUrl = null; }
        _audioUrl = URL.createObjectURL(new Blob([bytes], { type: mime || 'audio/mpeg' }));
        el.src = _audioUrl;
        if (!el._bsHooked) {
            el._bsHooked = true;
            const report = () => {
                if (_loopB != null && el.currentTime >= _loopB - 0.03) { try { el.currentTime = _loopA || 0; } catch {} }   // A–B practice loop
                if (dotNetRef) dotNetRef.invokeMethodAsync('OnAudioTime', el.currentTime || 0, isFinite(el.duration) ? el.duration : 0).catch(() => {});
            };
            el.addEventListener('timeupdate', report);
            el.addEventListener('loadedmetadata', report);
            el.addEventListener('ended', () => { if (dotNetRef) dotNetRef.invokeMethodAsync('OnAudioEnded').catch(() => {}); });
        }
        el.load();
        return true;
    } catch (e) { return false; }
};
window.playAudio  = function() { const el = _audio(); if (el) { const p = el.play(); if (p && p.catch) p.catch(() => {}); } };
window.pauseAudio = function() { const el = _audio(); if (el) el.pause(); };
window.seekAudio  = function(sec) { const el = _audio(); if (el && isFinite(sec)) { try { el.currentTime = Math.max(0, sec); } catch {} } };
window.setAudioRate = function(r) {
    const el = _audio(); if (!el) return;
    try {
        el.playbackRate = Math.max(0.25, Math.min(4, r || 1));
        el.preservesPitch = true; el.mozPreservesPitch = true; el.webkitPreservesPitch = true;   // keep pitch when slowed
    } catch {}
};
let _loopA = null, _loopB = null;   // audio A–B practice loop (seconds), or null
window.setAudioLoop   = function(a, b) { _loopA = (a != null && a >= 0) ? a : null; _loopB = (b != null && _loopA != null && b > _loopA) ? b : null; };
window.clearAudioLoop = function() { _loopA = null; _loopB = null; };
window.saveAudioPos = function(key, sec) { try { localStorage.setItem('bs_audiopos_' + key, String(sec || 0)); } catch {} };
window.loadAudioPos = function(key) { try { const v = parseFloat(localStorage.getItem('bs_audiopos_' + key)); return isNaN(v) ? 0 : v; } catch { return 0; } };
window.unloadAudio = function() {
    const el = _audio(); if (el) { try { el.pause(); } catch {} el.removeAttribute('src'); try { el.load(); } catch {} }
    if (_audioUrl) { try { URL.revokeObjectURL(_audioUrl); } catch {} _audioUrl = null; }
};


// ── Page transition animation ──────────────────────────────────────
let animateTransitions = false;

window.setAnimateTransitions = function(on) { animateTransitions = !!on; };

function beginPageTransition(newPage) {
    const v = document.getElementById("viewer");
    if (!v) return;

    _slideDir = null;
    _slideSnapshot = null;

    // Only single-page mode gets the horizontal slide. In two-page / half-turn the
    // whole spread would slide sideways (both pages move at once), which looks wrong —
    // those modes use a simple cross-fade instead.
    if (animateTransitions && _displayMode === 'single' &&
        pageNum > 0 && newPage != null && newPage !== pageNum && baseWidth > 0) {
        _slideDir = newPage > pageNum ? 'forward' : 'backward';
        _slideSnapshot = makeCanvas(baseWidth, baseHeight);
        const sc = _slideSnapshot.getContext('2d');
        sc.drawImage(canvas, 0, 0);
        sc.drawImage(markerCanvas, 0, 0);
        sc.drawImage(noteCanvas, 0, 0);
    } else if (animateTransitions) {
        v.style.transition = "none";
        v.style.opacity = "0";
    }
}

function endPageTransition() {
    const v = document.getElementById("viewer");
    if (!v) return;

    if (_slideDir && _slideSnapshot) {
        const overlay = document.createElement('div');
        overlay.className = 'slide-overlay';
        overlay.style.cssText = 'position:absolute;top:0;left:0;width:' + _slideSnapshot.width +
            'px;height:' + _slideSnapshot.height + 'px;transform-origin:0 0;will-change:transform;z-index:10;pointer-events:none;';
        overlay.style.transform = 'translate(' + Math.round(panX) + 'px,' + Math.round(panY) + 'px) scale(' + zoom + ')';
        _slideSnapshot.style.cssText = 'width:100%;height:100%;display:block;box-shadow:0 4px 24px rgba(0,0,0,0.45);';
        overlay.appendChild(_slideSnapshot);

        const bg = document.querySelector('.vbg');
        if (bg) bg.appendChild(overlay);

        const offset = window.innerWidth;
        const startX = _slideDir === 'forward' ? panX + offset : panX - offset;
        v.style.transition = 'none';
        v.style.opacity = '1';
        v.style.transform = 'translate(' + Math.round(startX) + 'px,' + Math.round(panY) + 'px) scale(' + zoom + ')';

        requestAnimationFrame(() => {
            const ease = 'cubic-bezier(0.25, 0.46, 0.45, 0.94)';
            overlay.style.transition = 'transform 0.35s ' + ease + ', opacity 0.35s ' + ease;
            const outX = _slideDir === 'forward' ? panX - offset : panX + offset;
            overlay.style.transform = 'translate(' + Math.round(outX) + 'px,' + Math.round(panY) + 'px) scale(' + zoom + ')';
            overlay.style.opacity = '0';

            v.style.transition = 'transform 0.35s ' + ease;
            applyView();

            setTimeout(() => {
                overlay.remove();
                v.style.transition = '';
                _slideDir = null;
                _slideSnapshot = null;
            }, 380);
        });
        return;
    }

    if (!animateTransitions) return;
    requestAnimationFrame(() => {
        v.style.transition = "opacity 0.22s ease";
        v.style.opacity = "1";
        setTimeout(() => { if (v) v.style.transition = ""; }, 280);
    });
}


// ── Last-page persistence ──────────────────────────────────────────
window.saveLastPage = function(pdfKey, page) {
    try { localStorage.setItem("bs_last_" + pdfKey, String(page)); } catch {}
};
window.loadLastPage = function(pdfKey) {
    try { return parseInt(localStorage.getItem("bs_last_" + pdfKey) || "1") || 1; }
    catch { return 1; }
};

// ── Recently opened (most-recent-first list of file names, per device) ──
window.pushRecent = function(file) {
    if (!file) return;
    try {
        let list = JSON.parse(localStorage.getItem("bs_recent") || "[]");
        if (!Array.isArray(list)) list = [];
        list = list.filter(f => f !== file);   // drop any earlier entry for this song
        list.unshift(file);                     // newest first
        if (list.length > 30) list = list.slice(0, 30);
        localStorage.setItem("bs_recent", JSON.stringify(list));
    } catch {}
};
window.getRecent = function() {
    try {
        const list = JSON.parse(localStorage.getItem("bs_recent") || "[]");
        return Array.isArray(list) ? list : [];
    } catch { return []; }
};

// ── Recent search terms (most-recent-first, per device) ──
window.pushRecentSearch = function(term) {
    term = (term || '').trim();
    if (term.length < 2) return;
    try {
        let list = JSON.parse(localStorage.getItem("bs_recent_search") || "[]");
        if (!Array.isArray(list)) list = [];
        list = list.filter(t => t.toLowerCase() !== term.toLowerCase());   // drop earlier duplicate (case-insensitive)
        list.unshift(term);                                                 // newest first
        if (list.length > 8) list = list.slice(0, 8);
        localStorage.setItem("bs_recent_search", JSON.stringify(list));
    } catch {}
};
window.getRecentSearches = function() {
    try {
        const list = JSON.parse(localStorage.getItem("bs_recent_search") || "[]");
        return Array.isArray(list) ? list : [];
    } catch { return []; }
};
window.clearRecentSearches = function() {
    try { localStorage.removeItem("bs_recent_search"); } catch {}
};


// ── Screen wake lock ───────────────────────────────────────────────
window.setWakeLock = async function(on) {
    try {
        if (on && navigator.wakeLock) {
            if (!window._wakeLock || window._wakeLock.released) {
                window._wakeLock = await navigator.wakeLock.request("screen");
            }
        } else if (window._wakeLock && !window._wakeLock.released) {
            await window._wakeLock.release();
            window._wakeLock = null;
        }
    } catch (e) {
        console.log("WakeLock:", e.message);
    }
};


// ═══════════════════════════════════════════════════════════════════
//  Drawing event wiring
// ═══════════════════════════════════════════════════════════════════

function setupDrawing() {
    noteCanvas.addEventListener("mousedown",  startDraw);
    noteCanvas.addEventListener("mousemove",  onDrawMove);
    noteCanvas.addEventListener("mouseup",    endDraw);
    noteCanvas.addEventListener("mouseleave", endDraw);
    noteCanvas.addEventListener("touchstart", startDraw,  { passive: false });
    noteCanvas.addEventListener("touchmove",  onDrawMove, { passive: false });
    noteCanvas.addEventListener("touchend",   endDraw);
}

function getPos(e) {
    const rect = noteCanvas.getBoundingClientRect();
    const cx = e.touches ? e.touches[0].clientX : e.clientX;
    const cy = e.touches ? e.touches[0].clientY : e.clientY;
    return {
        x: (cx - rect.left) * (noteCanvas.width  / rect.width),
        y: (cy - rect.top)  * (noteCanvas.height / rect.height)
    };
}


// ── startDraw ────────────────────────────────────────────────────────
function startDraw(e) {
    if (!editMode) return;
    if (e.touches && e.touches.length > 1) return;
    _drawLocked = false;   // a fresh single-finger / mouse press always begins a new draw
    // Palm rejection: while a pen is the active instrument, ignore finger/palm touches
    if (palmRejectOn && e.touches && _lastDownType !== 'pen' && _penActive()) return;
    e.preventDefault();
    const p = getPos(e);
    lastX = p.x; lastY = p.y;

    if (tool === "move") {
        moveTgt = findMoveTarget(p.x, p.y);
        if (moveTgt) { moveStartCX = p.x; moveStartCY = p.y; drawing = true; }
        return;
    }

    if (STAMP_TOOLS.includes(tool)) {
        drawing = true;
        liveStamp = { type: tool, x: p.x, y: p.y, color: penColor, size: penSize };
        redrawVectorLayer();
        return;
    }

    if (tool === "text") {
        if (dotNetRef) {
            const sx = e.touches ? e.touches[0].clientX : e.clientX;
            const sy = e.touches ? e.touches[0].clientY : e.clientY;
            const hit = _findTextAt(p.x, p.y);   // tapped an existing text note? → edit it
            if (hit) dotNetRef.invokeMethodAsync("ShowTextInput", hit.x, hit.y, sx, sy, hit.index, hit.text);
            else     dotNetRef.invokeMethodAsync("ShowTextInput", p.x, p.y, sx, sy, -1, "");
        }
        return;
    }

    if (tool === "crescendo" || tool === "decrescendo") {
        drawing = true;
        hairpinStart = { x: p.x, y: p.y };
        liveHairpin  = null;
        return;
    }

    if (SHAPE_TOOLS.includes(tool)) {
        drawing = true;
        shapeStart = { x: p.x, y: p.y };
        liveShape  = null;
        return;
    }

    if (tool === "marker") {
        drawing = true;
        markerPoints = [p];
        return;
    }

    // Pen / Eraser — begin a new vector stroke
    _strokePressure = penPressureOn && _lastDownType === 'pen' && tool !== "eraser";
    drawing = true;
    liveStroke = { color: penColor, size: penSize, eraser: tool === "eraser",
                   points: [ _strokePressure ? { x: p.x, y: p.y, p: _penPressure } : p ] };
}


// ── onDrawMove ───────────────────────────────────────────────────────
function onDrawMove(e) {
    if (!editMode) return;
    if (e.touches && e.touches.length > 1) return;
    if (_drawLocked) return;   // a two-finger page-turn is in progress → don't draw
    e.preventDefault();
    const p = getPos(e);

    if (STAMP_TOOLS.includes(tool)) {
        liveStamp = { type: tool, x: p.x, y: p.y, color: penColor, size: penSize };
        redrawVectorLayer();
        if (!drawing) return;
        lastX = p.x; lastY = p.y; return;
    }

    if (!drawing) return;

    if (tool === "move" && moveTgt) {
        const dx = p.x - moveStartCX, dy = p.y - moveStartCY;
        if (moveTgt.type === "stamp") {
            stampAnnotations[moveTgt.index].x = moveTgt.origX + dx;
            stampAnnotations[moveTgt.index].y = moveTgt.origY + dy;
        } else if (moveTgt.type === "text") {
            textAnnotations[moveTgt.index].x = moveTgt.origX + dx;
            textAnnotations[moveTgt.index].y = moveTgt.origY + dy;
        } else if (moveTgt.type === "hairpin") {
            const o = moveTgt.orig;
            hairpinAnnotations[moveTgt.index].x1 = o.x1 + dx;
            hairpinAnnotations[moveTgt.index].y1 = o.y1 + dy;
            hairpinAnnotations[moveTgt.index].x2 = o.x2 + dx;
            hairpinAnnotations[moveTgt.index].y2 = o.y2 + dy;
        } else if (moveTgt.type === "shape") {
            const o = moveTgt.orig;
            shapeAnnotations[moveTgt.index].x1 = o.x1 + dx;
            shapeAnnotations[moveTgt.index].y1 = o.y1 + dy;
            shapeAnnotations[moveTgt.index].x2 = o.x2 + dx;
            shapeAnnotations[moveTgt.index].y2 = o.y2 + dy;
        }
        redrawVectorLayer();
        lastX = p.x; lastY = p.y; return;
    }

    if ((tool === "crescendo" || tool === "decrescendo") && hairpinStart) {
        // Hairpins are always horizontal: keep the end on the start baseline (ignore vertical drag).
        liveHairpin = { x1: hairpinStart.x, y1: hairpinStart.y, x2: p.x, y2: hairpinStart.y,
                        color: penColor, size: penSize, isCrsc: tool === "crescendo" };
        redrawVectorLayer();
        lastX = p.x; lastY = p.y; return;
    }

    if (SHAPE_TOOLS.includes(tool) && shapeStart) {
        let ex = p.x, ey = p.y;                            // snapped endpoint
        const adx = Math.abs(ex - shapeStart.x), ady = Math.abs(ey - shapeStart.y);
        const m = Math.max(adx, ady);
        if (m > 10) {
            if (tool === "line" || tool === "arrow") {
                const deg = Math.atan2(ady, adx) * 180 / Math.PI;   // 0 (horizontal) … 90 (vertical)
                if (deg < 8)       ey = shapeStart.y;               // near-horizontal → flatten
                else if (deg > 82) ex = shapeStart.x;               // near-vertical → straighten
            } else if (Math.abs(adx - ady) < m * 0.12) {           // rect/ellipse near-square → snap equal
                ex = shapeStart.x + Math.sign(ex - shapeStart.x) * m;
                ey = shapeStart.y + Math.sign(ey - shapeStart.y) * m;
            }
        }
        liveShape = { kind: tool, x1: shapeStart.x, y1: shapeStart.y, x2: ex, y2: ey,
                      color: penColor, size: penSize,
                      fill: (_shapeFill && (tool === "rect" || tool === "ellipse")) };
        redrawVectorLayer();
        lastX = p.x; lastY = p.y; return;
    }

    if (tool === "marker") {
        markerPoints.push(p);
        redrawMarkers(markerPoints);
        lastX = p.x; lastY = p.y; return;
    }

    // Pen / Eraser — accumulate points and update live preview
    if (liveStroke) {
        liveStroke.points.push(_strokePressure ? { x: p.x, y: p.y, p: _penPressure } : p);
        if (liveStroke.eraser) eraseNearby(p.x, p.y, penSize * 5);
        redrawPenLayer();
        redrawVectorLayer();
    }
    lastX = p.x; lastY = p.y;
}


// ── endDraw ──────────────────────────────────────────────────────────
function endDraw(e) {
    if (_drawLocked) return;   // stroke was cancelled for a two-finger page-turn → commit nothing
    const isLeave = e && e.type === "mouseleave";

    if (isLeave) {
        if (liveStamp) { liveStamp = null; redrawVectorLayer(); }
        if (!drawing) return;
    }

    if (STAMP_TOOLS.includes(tool)) {
        if (drawing && liveStamp) {
            stampAnnotations.push({ ...liveStamp });
            liveStamp = null;
            redrawVectorLayer();
            saveNotes();
        }
        drawing = false;
        return;
    }

    if (!drawing) return;
    drawing = false;

    if (tool === "move" && moveTgt) {
        moveTgt = null; saveNotes(); return;
    }

    if ((tool === "crescendo" || tool === "decrescendo") && liveHairpin) {
        hairpinAnnotations.push({ ...liveHairpin });
        liveHairpin = null; hairpinStart = null;
        redrawVectorLayer(); saveNotes(); return;
    }
    hairpinStart = null; liveHairpin = null;

    if (SHAPE_TOOLS.includes(tool) && liveShape) {
        // ignore an accidental zero-size tap
        if (Math.hypot(liveShape.x2 - liveShape.x1, liveShape.y2 - liveShape.y1) > 3)
            shapeAnnotations.push({ ...liveShape });
        liveShape = null; shapeStart = null;
        redrawVectorLayer(); saveNotes(); return;
    }
    shapeStart = null; liveShape = null;

    if (tool === "marker" && markerPoints.length > 1) {
        markerAnnotations.push({ color: penColor, size: penSize * 3, points: [...markerPoints] });
        markerPoints = [];
        redrawMarkers(null); saveNotes(); return;
    }
    markerPoints = [];

    // Pen / Eraser — commit stroke to penStrokes
    if (liveStroke) {
        if (liveStroke.points.length === 1) {
            // Single tap: duplicate point to make a visible dot
            liveStroke.points.push({ ...liveStroke.points[0] });
        }
        if (liveStroke.points.length >= 2) {
            penStrokes.push(liveStroke);
        }
        liveStroke = null;
        redrawPenLayer();
        redrawVectorLayer();
        saveNotes();
    }
}


// ═══════════════════════════════════════════════════════════════════
//  Pen layer — vector stroke replay (fixes eraser in all WebViews)
// ═══════════════════════════════════════════════════════════════════

// Stroke a smooth path through pts using quadratic Béziers via segment midpoints, so fast
// scribbles read as curves instead of faceted polylines. Uses the ctx's current style/width.
function _strokeSmoothPath(ctx, pts) {
    const n = pts.length;
    if (n < 2) return;
    ctx.beginPath();
    ctx.moveTo(pts[0].x, pts[0].y);
    if (n === 2) { ctx.lineTo(pts[1].x, pts[1].y); ctx.stroke(); return; }
    for (let i = 1; i < n - 1; i++) {
        const mx = (pts[i].x + pts[i + 1].x) / 2, my = (pts[i].y + pts[i + 1].y) / 2;
        ctx.quadraticCurveTo(pts[i].x, pts[i].y, mx, my);
    }
    ctx.quadraticCurveTo(pts[n - 2].x, pts[n - 2].y, pts[n - 1].x, pts[n - 1].y);
    ctx.stroke();
}

function redrawPenLayer() {
    penCtx.clearRect(0, 0, penCanvas.width, penCanvas.height);
    const all = liveStroke ? [...penStrokes, liveStroke] : penStrokes;
    for (const s of all) {
        if (!s.points || s.points.length < 2) continue;
        penCtx.save();
        penCtx.lineCap = penCtx.lineJoin = "round";
        penCtx.globalAlpha = 1;
        // Pressure-sensitive pen strokes (points carry `p`) draw segment-by-segment so the
        // width tracks the pen pressure; everything else keeps the single fast polyline.
        const varied = !s.eraser && s.points.some(pt => typeof pt.p === "number");
        if (s.eraser) {
            penCtx.globalCompositeOperation = "destination-out";
            penCtx.strokeStyle = "rgba(0,0,0,1)";
            penCtx.lineWidth   = s.size * 5;
        } else {
            penCtx.globalCompositeOperation = "source-over";
            penCtx.strokeStyle = s.color;
            penCtx.lineWidth   = s.size;
        }
        if (varied) {
            for (let i = 1; i < s.points.length; i++) {
                const a = s.points[i - 1], b = s.points[i];
                const pr = typeof b.p === "number" ? b.p : (typeof a.p === "number" ? a.p : 0.5);
                penCtx.lineWidth = Math.max(0.5, s.size * (0.35 + 0.85 * pr));
                penCtx.beginPath();
                penCtx.moveTo(a.x, a.y);
                penCtx.lineTo(b.x, b.y);
                penCtx.stroke();
            }
        } else {
            _strokeSmoothPath(penCtx, s.points);
        }
        penCtx.restore();
    }
}


// ═══════════════════════════════════════════════════════════════════
//  Marker layer
// ═══════════════════════════════════════════════════════════════════

function redrawMarkers(livePoints) {
    markerCtx.clearRect(0, 0, markerCanvas.width, markerCanvas.height);
    const tmp = makeCanvas(markerCanvas.width, markerCanvas.height);
    const tc  = tmp.getContext("2d");
    tc.lineCap = tc.lineJoin = "round";

    for (const m of markerAnnotations) {
        if (!m.points || m.points.length < 2) continue;
        tc.strokeStyle = m.color;
        tc.lineWidth   = m.size || 15;
        tc.globalAlpha = 1; tc.globalCompositeOperation = "source-over";
        _strokeSmoothPath(tc, m.points);
    }
    if (livePoints && livePoints.length > 1) {
        tc.strokeStyle = penColor;
        tc.lineWidth   = penSize * 3;
        tc.globalAlpha = 1; tc.globalCompositeOperation = "source-over";
        _strokeSmoothPath(tc, livePoints);
    }
    markerCtx.globalAlpha = 0.35;
    markerCtx.drawImage(tmp, 0, 0);
    markerCtx.globalAlpha = 1;
}


// ═══════════════════════════════════════════════════════════════════
//  Vector annotation layer
// ═══════════════════════════════════════════════════════════════════

function redrawVectorLayer() {
    noteCtx.clearRect(0, 0, noteCanvas.width, noteCanvas.height);
    noteCtx.globalAlpha = 1; noteCtx.globalCompositeOperation = "source-over";

    noteCtx.drawImage(penCanvas, 0, 0);

    for (const s of stampAnnotations) drawStampShape(noteCtx, s, 1.0);
    if (liveStamp) drawStampShape(noteCtx, liveStamp, 0.65);

    for (const t of textAnnotations) {
        noteCtx.save();
        noteCtx.font = `${t.fontSize}px sans-serif`;
        noteCtx.fillStyle = t.color; noteCtx.textBaseline = "top";
        noteCtx.fillText(t.text, t.x, t.y);
        noteCtx.restore();
    }

    for (const h of hairpinAnnotations) drawHairpinShape(h);
    if (liveHairpin) drawHairpinShape(liveHairpin);

    for (const s of shapeAnnotations) drawShapeShape(s, 1);
    if (liveShape) drawShapeShape(liveShape, 0.75);
}

function drawStampShape(ctx2d, s, alpha) {
    ctx2d.save();
    ctx2d.globalAlpha = alpha;
    ctx2d.fillStyle = s.color;
    const isTextMark = typeof s.type === "string" && (s.type.startsWith("dyn-") || s.type.startsWith("nav-") || s.type.startsWith("ped-"));
    ctx2d.font = `${isTextMark ? "italic bold" : "bold"} ${s.size * 5 + 15}px "FreeSerif","Noto Serif",serif`;
    ctx2d.textAlign = "center"; ctx2d.textBaseline = "middle";
    ctx2d.fillText(STAMP_SYM[s.type] || s.type, s.x, s.y);
    ctx2d.restore();
}

function drawHairpinShape(h) {
    const sp = h.size * 8;
    noteCtx.save();
    noteCtx.strokeStyle = h.color;
    noteCtx.lineWidth   = Math.max(1.5, h.size * 0.8);
    noteCtx.lineCap     = "round";
    noteCtx.globalAlpha = 1; noteCtx.globalCompositeOperation = "source-over";
    noteCtx.beginPath();
    if (h.isCrsc) {
        noteCtx.moveTo(h.x1, h.y1); noteCtx.lineTo(h.x2, h.y2 - sp);
        noteCtx.moveTo(h.x1, h.y1); noteCtx.lineTo(h.x2, h.y2 + sp);
    } else {
        noteCtx.moveTo(h.x1, h.y1 - sp); noteCtx.lineTo(h.x2, h.y2);
        noteCtx.moveTo(h.x1, h.y1 + sp); noteCtx.lineTo(h.x2, h.y2);
    }
    noteCtx.stroke(); noteCtx.restore();
}

// Line / arrow / rectangle / ellipse — a 2-point drag shape (x1,y1)→(x2,y2).
function drawShapeShape(s, alpha) {
    noteCtx.save();
    const baseAlpha = alpha == null ? 1 : alpha;
    noteCtx.globalAlpha = baseAlpha;
    noteCtx.strokeStyle = s.color;
    noteCtx.lineWidth   = Math.max(1.5, s.size * 0.9);
    noteCtx.lineCap     = "round";
    noteCtx.lineJoin    = "round";
    noteCtx.globalCompositeOperation = "source-over";
    const x1 = s.x1, y1 = s.y1, x2 = s.x2, y2 = s.y2;
    if (s.kind === "rect") {
        const rx = Math.min(x1, x2), ry = Math.min(y1, y2), rw = Math.abs(x2 - x1), rh = Math.abs(y2 - y1);
        if (s.fill) { noteCtx.globalAlpha = baseAlpha * 0.25; noteCtx.fillStyle = s.color; noteCtx.fillRect(rx, ry, rw, rh); noteCtx.globalAlpha = baseAlpha; }
        noteCtx.strokeRect(rx, ry, rw, rh);
    } else if (s.kind === "ellipse") {
        noteCtx.beginPath();
        noteCtx.ellipse((x1 + x2) / 2, (y1 + y2) / 2,
                        Math.max(1, Math.abs(x2 - x1) / 2), Math.max(1, Math.abs(y2 - y1) / 2),
                        0, 0, Math.PI * 2);
        if (s.fill) { noteCtx.globalAlpha = baseAlpha * 0.25; noteCtx.fillStyle = s.color; noteCtx.fill(); noteCtx.globalAlpha = baseAlpha; }
        noteCtx.stroke();
    } else {   // line or arrow
        noteCtx.beginPath();
        noteCtx.moveTo(x1, y1); noteCtx.lineTo(x2, y2); noteCtx.stroke();
        if (s.kind === "arrow") {
            const ang = Math.atan2(y2 - y1, x2 - x1);
            const head = Math.max(10, s.size * 3.2);
            noteCtx.beginPath();
            noteCtx.moveTo(x2, y2);
            noteCtx.lineTo(x2 - head * Math.cos(ang - Math.PI / 7), y2 - head * Math.sin(ang - Math.PI / 7));
            noteCtx.moveTo(x2, y2);
            noteCtx.lineTo(x2 - head * Math.cos(ang + Math.PI / 7), y2 - head * Math.sin(ang + Math.PI / 7));
            noteCtx.stroke();
        }
    }
    noteCtx.restore();
}

function redrawAll() {
    redrawMarkers(null);
    redrawVectorLayer();
}


// ═══════════════════════════════════════════════════════════════════
//  Eraser — remove nearby annotations of all types
// ═══════════════════════════════════════════════════════════════════

function eraseNearby(ex, ey, radius) {
    let changed = false;
    const r2 = radius * radius;

    // Remove marker annotations whose path passes near the eraser
    for (let i = markerAnnotations.length - 1; i >= 0; i--) {
        const m = markerAnnotations[i];
        if (!m.points) continue;
        for (const pt of m.points) {
            if ((pt.x - ex) * (pt.x - ex) + (pt.y - ey) * (pt.y - ey) < r2) {
                markerAnnotations.splice(i, 1);
                changed = true;
                break;
            }
        }
    }

    // Remove stamp annotations near the eraser
    for (let i = stampAnnotations.length - 1; i >= 0; i--) {
        const s = stampAnnotations[i];
        if ((s.x - ex) * (s.x - ex) + (s.y - ey) * (s.y - ey) < r2) {
            stampAnnotations.splice(i, 1);
            changed = true;
        }
    }

    // Remove text annotations near the eraser
    for (let i = textAnnotations.length - 1; i >= 0; i--) {
        const t = textAnnotations[i];
        if ((t.x - ex) * (t.x - ex) + (t.y - ey) * (t.y - ey) < r2) {
            textAnnotations.splice(i, 1);
            changed = true;
        }
    }

    // Remove hairpin annotations near the eraser
    for (let i = hairpinAnnotations.length - 1; i >= 0; i--) {
        const h = hairpinAnnotations[i];
        if (distToSegment2(ex, ey, h.x1, h.y1, h.x2, h.y2) < r2) {
            hairpinAnnotations.splice(i, 1);
            changed = true;
        }
    }

    // Remove shape annotations whose outline passes near the eraser
    for (let i = shapeAnnotations.length - 1; i >= 0; i--) {
        if (_shapeNearEraser(shapeAnnotations[i], ex, ey, r2)) {
            shapeAnnotations.splice(i, 1);
            changed = true;
        }
    }

    if (changed) redrawMarkers(null);
}

// Is (ex,ey) within √r2 of a shape's drawn outline? Line/arrow: the segment.
// Rect/ellipse: any of the 4 bounding-box edges (close enough for erasing).
function _shapeNearEraser(s, ex, ey, r2) {
    if (s.kind === "line" || s.kind === "arrow")
        return distToSegment2(ex, ey, s.x1, s.y1, s.x2, s.y2) < r2;
    const xa = Math.min(s.x1, s.x2), xb = Math.max(s.x1, s.x2);
    const ya = Math.min(s.y1, s.y2), yb = Math.max(s.y1, s.y2);
    return distToSegment2(ex, ey, xa, ya, xb, ya) < r2 || distToSegment2(ex, ey, xb, ya, xb, yb) < r2 ||
           distToSegment2(ex, ey, xb, yb, xa, yb) < r2 || distToSegment2(ex, ey, xa, yb, xa, ya) < r2;
}

function distToSegment2(px, py, x1, y1, x2, y2) {
    const dx = x2 - x1, dy = y2 - y1;
    const len2 = dx * dx + dy * dy;
    if (len2 === 0) return (px - x1) * (px - x1) + (py - y1) * (py - y1);
    let t = ((px - x1) * dx + (py - y1) * dy) / len2;
    t = Math.max(0, Math.min(1, t));
    const cx = x1 + t * dx, cy = y1 + t * dy;
    return (px - cx) * (px - cx) + (py - cy) * (py - cy);
}


// ═══════════════════════════════════════════════════════════════════
//  Move tool — hit detection
// ═══════════════════════════════════════════════════════════════════

function findMoveTarget(cx, cy) {
    const THRESH = 50;
    let best = null, bestDist = THRESH;

    for (let i = stampAnnotations.length - 1; i >= 0; i--) {
        const s = stampAnnotations[i];
        const d = Math.hypot(s.x - cx, s.y - cy);
        if (d < bestDist) { bestDist = d; best = { type: "stamp", index: i, origX: s.x, origY: s.y }; }
    }
    for (let i = textAnnotations.length - 1; i >= 0; i--) {
        const t = textAnnotations[i];
        const d = Math.hypot(t.x - cx, t.y - cy);
        if (d < bestDist) { bestDist = d; best = { type: "text", index: i, origX: t.x, origY: t.y }; }
    }
    for (let i = hairpinAnnotations.length - 1; i >= 0; i--) {
        const h  = hairpinAnnotations[i];
        const mx = (h.x1 + h.x2) / 2, my = (h.y1 + h.y2) / 2;
        const d  = Math.hypot(mx - cx, my - cy);
        if (d < bestDist) {
            bestDist = d;
            best = { type: "hairpin", index: i, orig: { x1: h.x1, y1: h.y1, x2: h.x2, y2: h.y2 } };
        }
    }
    for (let i = shapeAnnotations.length - 1; i >= 0; i--) {
        const s  = shapeAnnotations[i];
        const mx = (s.x1 + s.x2) / 2, my = (s.y1 + s.y2) / 2;
        const d  = Math.hypot(mx - cx, my - cy);
        if (d < bestDist) {
            bestDist = d;
            best = { type: "shape", index: i, orig: { x1: s.x1, y1: s.y1, x2: s.x2, y2: s.y2 } };
        }
    }
    return best;
}


// ═══════════════════════════════════════════════════════════════════
//  Text annotation
// ═══════════════════════════════════════════════════════════════════

window.drawTextOnCanvas = function(x, y, text, color, fontSize) {
    textAnnotations.push({ text, x, y, color, fontSize });
    redrawVectorLayer();
    saveNotes();
};

// Hit-test existing text notes (drawn top-left at t.x,t.y). Returns {index,text,x,y} or null.
function _findTextAt(px, py) {
    for (let i = textAnnotations.length - 1; i >= 0; i--) {
        const t = textAnnotations[i];
        const w = Math.max(20, (t.text || '').length * (t.fontSize || 20) * 0.6);
        const h = (t.fontSize || 20) * 1.3;
        if (px >= t.x - 8 && px <= t.x + w + 8 && py >= t.y - 8 && py <= t.y + h + 8)
            return { index: i, text: t.text, x: t.x, y: t.y };
    }
    return null;
}

// Update an existing text note in place; clearing the text deletes it.
window.updateTextAnnotation = function(index, text, color, fontSize) {
    if (index < 0 || index >= textAnnotations.length) return;
    if (!text || !text.trim()) {
        textAnnotations.splice(index, 1);               // cleared → remove the note
    } else {
        const t = textAnnotations[index];
        t.text = text; t.color = color; t.fontSize = fontSize;
    }
    redrawVectorLayer();
    saveNotes();
};


// ═══════════════════════════════════════════════════════════════════
//  Storage key  (halfSide removed — full page is rendered in half mode)
// ═══════════════════════════════════════════════════════════════════

function storageKey() {
    if (scrollMode) return "bsv2_" + currentPdfKey + "_" + scrollMode;
    return "bsv2_" + currentPdfKey + "_p" + pageNum;
}


// ═══════════════════════════════════════════════════════════════════
//  Save / load / undo / clear
// ═══════════════════════════════════════════════════════════════════

function getFullState() {
    return {
        penStrokes:  JSON.parse(JSON.stringify(penStrokes)),
        markerData:  JSON.parse(JSON.stringify(markerAnnotations)),
        stampData:   JSON.parse(JSON.stringify(stampAnnotations)),
        textData:    JSON.parse(JSON.stringify(textAnnotations)),
        hairpinData: JSON.parse(JSON.stringify(hairpinAnnotations)),
        shapeData:   JSON.parse(JSON.stringify(shapeAnnotations)),
        w: noteCanvas.width, h: noteCanvas.height
    };
}

// Which PDF pages of the current doc have saved annotations (the single/two/half
// keys bsv2_<key>_p<N>). Used for the page-grid "annotated" markers. Scroll composites
// (one key, not per page) are skipped.
function _rawHasAnnotations(raw) {
    if (raw.startsWith("data:image")) return true;                        // legacy pen image
    try {
        const d = JSON.parse(raw);
        if (!d || typeof d !== 'object') return false;
        return !!((d.penStrokes && d.penStrokes.length) || (d.markerData && d.markerData.length) ||
                  (d.stampData && d.stampData.length) || (d.textData && d.textData.length) ||
                  (d.hairpinData && d.hairpinData.length) || (d.shapeData && d.shapeData.length));
    } catch { return false; }
}
window.getAnnotatedPages = function() {
    const prefix = "bsv2_" + currentPdfKey + "_p";
    const set = new Set();
    try {
        for (let i = 0; i < localStorage.length; i++) {
            const key = localStorage.key(i);
            if (!key || !key.startsWith(prefix)) continue;
            const m = key.slice(prefix.length).match(/^(\d+)/);           // page number (plain _pN or legacy _pN_hH)
            if (!m) continue;
            const raw = localStorage.getItem(key);
            if (raw && _rawHasAnnotations(raw)) set.add(parseInt(m[1], 10));
        }
    } catch {}
    return [...set];
};

// ── Rotation-canonical note storage ─────────────────────────────────
// Annotations are persisted in rotation-0 ("canonical") page coordinates so they
// stay anchored to the page content across rotations. Active only in single-page
// style modes at a non-zero rotation; at rotation 0 it is the identity, so existing
// data and behaviour are byte-for-byte unchanged.
function _notesCanonActive() {
    return pageRotation !== 0 && (_displayMode === 'single' || _displayMode === 'half-turn');
}
function _canonDims() {
    const swap = (pageRotation === 90 || pageRotation === 270);
    return { W0: swap ? baseHeight : baseWidth, H0: swap ? baseWidth : baseHeight };
}
function _toCanon(x, y, W0, H0) {   // current-rotation canvas → canonical (rotation 0)
    switch (pageRotation) {
        case 90:  return { x: y,      y: H0 - x };
        case 180: return { x: W0 - x, y: H0 - y };
        case 270: return { x: W0 - y, y: x };
        default:  return { x, y };
    }
}
function _fromCanon(x, y, W0, H0) { // canonical (rotation 0) → current-rotation canvas
    switch (pageRotation) {
        case 90:  return { x: H0 - y, y: x };
        case 180: return { x: W0 - x, y: H0 - y };
        case 270: return { x: y,      y: W0 - x };
        default:  return { x, y };
    }
}
function _mapNotePoints(st, mapFn) {
    const mapArr = (arr) => {
        for (const o of arr || []) {
            if (o.points) { for (const p of o.points) { const r = mapFn(p.x, p.y); p.x = r.x; p.y = r.y; } }
            else if (typeof o.x === 'number' && typeof o.y === 'number') { const r = mapFn(o.x, o.y); o.x = r.x; o.y = r.y; }
            else if (typeof o.x1 === 'number') { const a = mapFn(o.x1, o.y1), b = mapFn(o.x2, o.y2); o.x1 = a.x; o.y1 = a.y; o.x2 = b.x; o.y2 = b.y; }
        }
    };
    mapArr(st.penStrokes); mapArr(st.markerData); mapArr(st.stampData);
    mapArr(st.textData);   mapArr(st.hairpinData); mapArr(st.shapeData);
    return st;
}

function saveNotes() { persistNotes(); pushUndo(); }

function persistNotes() {
    try {
        let st = getFullState();
        if (_notesCanonActive()) {
            const d = _canonDims();
            st = _mapNotePoints(st, (x, y) => _toCanon(x, y, d.W0, d.H0));
            st.w = d.W0; st.h = d.H0;
        }
        localStorage.setItem(storageKey(), JSON.stringify(st));
    } catch {}
}

function loadNotes() {
    penCtx.clearRect(0, 0, penCanvas.width, penCanvas.height);
    noteCtx.clearRect(0, 0, noteCanvas.width, noteCanvas.height);
    markerCtx.clearRect(0, 0, markerCanvas.width, markerCanvas.height);
    penStrokes = [];
    markerAnnotations  = []; stampAnnotations   = [];
    textAnnotations    = []; hairpinAnnotations = []; shapeAnnotations = [];
    undoStack = []; redoStack = [];

    let raw;
    try { raw = localStorage.getItem(storageKey()); } catch { pushUndo(); return; }

    // Legacy key fallbacks
    if (!raw && !scrollMode) {
        try { raw = localStorage.getItem("notes_" + currentPdfKey + "_p" + pageNum); } catch {}
        // Old half-page keys
        if (!raw && halfSide !== null) {
            try { raw = localStorage.getItem("bsv2_" + currentPdfKey + "_p" + pageNum + "_h" + halfSide); } catch {}
            if (!raw) {
                const otherSide = halfSide === 0 ? 1 : 0;
                try { raw = localStorage.getItem("bsv2_" + currentPdfKey + "_p" + pageNum + "_h" + otherSide); } catch {}
            }
        }
    }
    if (!raw) { pushUndo(); return; }

    // Very old format: raw data URL
    if (raw.startsWith("data:image")) {
        const img = new Image();
        img.onload = () => { penCtx.drawImage(img, 0, 0); redrawVectorLayer(); pushUndo(); };
        img.src = raw; return;
    }

    let data;
    try { data = JSON.parse(raw); } catch { pushUndo(); return; }

    if (_notesCanonActive()) {
        const d = _canonDims();
        data = _mapNotePoints(data, (x, y) => _fromCanon(x, y, d.W0, d.H0));
    }

    if (data.markerData)  markerAnnotations  = data.markerData;
    if (data.stampData)   stampAnnotations   = data.stampData;
    if (data.textData)    textAnnotations    = data.textData;
    if (data.hairpinData) hairpinAnnotations = data.hairpinData;
    if (data.shapeData)   shapeAnnotations   = data.shapeData;

    if (data.penStrokes) {
        penStrokes = data.penStrokes;
        redrawPenLayer();
        redrawAll();
        pushUndo();
    } else {
        // Legacy: bitmap pen layer
        const penSrc = data.penImg || data.noteImg || data.img;
        if (penSrc && penSrc.length > 6 && !penSrc.endsWith(",")) {
            const img = new Image();
            img.onload  = () => { penCtx.drawImage(img, 0, 0); redrawAll(); pushUndo(); };
            img.onerror = ()  => { redrawAll(); pushUndo(); };
            img.src = penSrc;
        } else {
            redrawAll(); pushUndo();
        }
    }
}

window.clearNotes = function() {
    penCtx.clearRect(0, 0, penCanvas.width, penCanvas.height);
    markerCtx.clearRect(0, 0, markerCanvas.width, markerCanvas.height);
    noteCtx.clearRect(0, 0, noteCanvas.width, noteCanvas.height);
    penStrokes = [];
    markerAnnotations  = []; stampAnnotations   = [];
    textAnnotations    = []; hairpinAnnotations = []; shapeAnnotations = [];
    saveNotes();
};

// Does the current page have any live annotations? Used to confirm before clearing.
window.hasPageNotes = function() {
    return !!(penStrokes.length || markerAnnotations.length || stampAnnotations.length ||
              textAnnotations.length || hairpinAnnotations.length || shapeAnnotations.length);
};

// Hide / show all annotation layers (pen, marker, stamps, text, hairpins, shapes)
// without deleting them — the PDF page and link-point markers stay visible.
window.setNotesVisible = function(v) {
    _notesVisible = !!v;
    const vis = _notesVisible ? "" : "hidden";
    if (markerCanvas) markerCanvas.style.visibility = vis;
    if (noteCanvas)   noteCanvas.style.visibility   = vis;
};

// Fill rect/ellipse shapes with a translucent wash (region highlight) when on.
window.setShapeFill = function(v) { _shapeFill = !!v; };

function pushUndo() {
    const s = JSON.stringify(getFullState());
    if (undoStack.length && undoStack[undoStack.length - 1] === s) return;
    undoStack.push(s);
    if (undoStack.length > 30) undoStack.shift();
    redoStack = [];
}

window.undoNote = function() {
    if (undoStack.length <= 1) return;
    redoStack.push(undoStack.pop());
    applyUndoState(JSON.parse(undoStack[undoStack.length - 1]));
};

window.redoNote = function() {
    if (!redoStack.length) return;
    const s = JSON.parse(redoStack.pop());
    undoStack.push(JSON.stringify(s));
    applyUndoState(s);
};

async function applyUndoState(s) {
    penStrokes         = s.penStrokes  || [];
    markerAnnotations  = s.markerData  || [];
    stampAnnotations   = s.stampData   || [];
    textAnnotations    = s.textData    || [];
    hairpinAnnotations = s.hairpinData || [];
    shapeAnnotations   = s.shapeData   || [];

    penCtx.clearRect(0, 0, penCanvas.width, penCanvas.height);

    if (penStrokes.length > 0) {
        redrawPenLayer();
    } else {
        // Legacy bitmap fallback in undo history
        const src = s.penImg || s.noteImg || s.img;
        if (src && src.length > 6 && !src.endsWith(",")) {
            await new Promise(res => {
                const img = new Image();
                img.onload = () => { penCtx.drawImage(img, 0, 0); res(); };
                img.onerror = res;
                img.src = src;
            });
        }
    }

    redrawAll();
    persistNotes();
}


// ═══════════════════════════════════════════════════════════════════
//  Gesture system
// ═══════════════════════════════════════════════════════════════════

let gestureConfig = {};
let longPressTimer = null;
let longPressTriggered = false;
let _gesturesBound = false;
let threeFingSwipeStartX = 0, threeFingSwipeStartY = 0;
let swipeFromBottomY = 0;

window.initGestures = function(config) {
    gestureConfig = config || {};
    if (_gesturesBound) return;   // bind the document listeners ONCE — re-binding leaked duplicate
    _gesturesBound = true;        // long-press timers that fired add_bookmark in a loop
    setupGestureListeners();
};

window.updateGestureConfig = function(config) {
    gestureConfig = config || {};
};

window.fitToWidthPublic = function() {
    if (_displayMode === 'scroll-v') fitToWidth();
    else if (_displayMode === 'half-turn') _applyHalfTurnFit(_halfTurnTop);
    else applyPageScaling();
};

function setupGestureListeners() {
    // Long press
    document.addEventListener('touchstart', function(e) {
        if (editMode) return;
        if (e.touches.length === 1) {
            longPressTriggered = false;
            longPressTimer = setTimeout(function() {
                longPressTriggered = true;
                fireGesture('longpress');
            }, 600);
        } else {
            clearTimeout(longPressTimer);
        }
    }, { passive: true });

    document.addEventListener('touchmove', function(e) {
        if (longPressTimer && e.touches.length === 1) {
            clearTimeout(longPressTimer);
            longPressTimer = null;
        }
    }, { passive: true });

    document.addEventListener('touchend', function(e) {
        clearTimeout(longPressTimer);
        longPressTimer = null;
    }, { passive: true });

    // 2-finger single tap (fires on lift if no pinch occurred)
    document.addEventListener('touchend', function(e) {
        if (editMode) return;
        if (e.touches.length === 0 && e.changedTouches.length >= 2 && !_wasPinching) {
            fireGesture('2finger_dbltap');
        }
    }, { passive: true });

    // 3-finger swipe
    document.addEventListener('touchstart', function(e) {
        if (editMode || e.touches.length !== 3) return;
        threeFingSwipeStartX = (e.touches[0].clientX + e.touches[1].clientX + e.touches[2].clientX) / 3;
        threeFingSwipeStartY = (e.touches[0].clientY + e.touches[1].clientY + e.touches[2].clientY) / 3;
    }, { passive: true });

    document.addEventListener('touchend', function(e) {
        if (editMode || !threeFingSwipeStartX) return;
        if (e.changedTouches.length < 1) return;
        const tx = e.changedTouches[0].clientX - threeFingSwipeStartX;
        const ty = e.changedTouches[0].clientY - threeFingSwipeStartY;
        if (Math.abs(tx) > 60 && Math.abs(tx) > Math.abs(ty) * 1.5) {
            fireGesture(tx < 0 ? '3finger_left' : '3finger_right');
        }
        threeFingSwipeStartX = 0;
    }, { passive: true });

    // Swipe from bottom edge upward
    document.addEventListener('touchstart', function(e) {
        if (editMode || e.touches.length !== 1) return;
        const y = e.touches[0].clientY;
        swipeFromBottomY = y > window.innerHeight * 0.80 ? y : 0;
    }, { passive: true });

    document.addEventListener('touchend', function(e) {
        if (!swipeFromBottomY || e.changedTouches.length < 1) return;
        const dy = swipeFromBottomY - e.changedTouches[0].clientY;
        if (dy > 60) { fireGesture('swipe_bottom_up'); }
        swipeFromBottomY = 0;
    }, { passive: true });
}

function fireGesture(gestureId) {
    if (cropEditing) return;
    const action = gestureConfig[gestureId] || 'none';
    if (!action || action === 'none') return;
    if (action === 'zoom_reset') { fitToWidth(); return; }
    if (dotNetRef) {
        dotNetRef.invokeMethodAsync('OnGesture', action).catch(function() {});
    }
}


// ═══════════════════════════════════════════════════════════════════
//  Stylus / pen detection
// ═══════════════════════════════════════════════════════════════════

let stylusAutoEdit = true;
let stylusAutoEnteredEdit = false;
let penLastMoveTime = 0;
let penEnterDebounce = null;
let stylusReentryBlocked = false;   // set on exit; cleared only after the pen leaves and re-approaches

// ── Stylus front/barrel button → eraser ─────────────────────────────
let stylusButtonMode = 'off';       // 'hold' | 'toggle' | 'off'
let _penBtnEraser = false;          // eraser currently engaged via the pen button
let _toolBeforePenBtn = null, _sizeBeforePenBtn = null;
let _stylusBtnBound = false;

// ── Pen pressure + palm rejection ───────────────────────────────────
let penPressureOn   = true;   // pressure-sensitive stroke width (pen only)
let palmRejectOn    = true;   // ignore finger/palm touches while a pen is active
let _penPressure    = 0.5;    // latest pen pressure (0..1)
let _lastDownType   = 'mouse';// pointerType of the most recent pointerdown
let _penContact     = false;  // a pen pointer is currently in contact
let _penSeenTs      = 0;      // last time any pen pointer event fired
let _strokePressure = false;  // the current stroke is a pressure-sensitive pen stroke
function _penActive() { return _penContact || (Date.now() - _penSeenTs < 700); }

window.setPenPressure = function(on) { penPressureOn = (on !== false); };
window.setPalmReject  = function(on) { palmRejectOn  = (on !== false); };

window.setStylusAutoEdit = function(on) { stylusAutoEdit = on; };
window.setStylusButtonMode = function(m) {
    stylusButtonMode = (m === 'hold' || m === 'toggle') ? m : 'off';
    if (stylusButtonMode === 'off') disengagePenEraser();
};

function engagePenEraser() {
    if (_penBtnEraser || !editMode) return;
    _penBtnEraser = true;
    _toolBeforePenBtn = (tool === 'eraser') ? 'pen' : tool;
    _sizeBeforePenBtn = penSize;
    tool = 'eraser';
    penSize = toolSizes['eraser'] || 8;
    liveStamp = null;
    if (dotNetRef) dotNetRef.invokeMethodAsync('OnStylusEraser', true, 'eraser').catch(() => {});
}
function disengagePenEraser() {
    if (!_penBtnEraser) return;
    _penBtnEraser = false;
    const restore = _toolBeforePenBtn || 'pen';
    tool = restore;
    if (_sizeBeforePenBtn != null) penSize = _sizeBeforePenBtn;
    if (dotNetRef) dotNetRef.invokeMethodAsync('OnStylusEraser', false, restore).catch(() => {});
}

window.initStylusDetection = function() {
    // Pen moves near screen → debounce-enter edit mode
    document.addEventListener('pointermove', function(e) {
        if (!stylusAutoEdit || e.pointerType !== 'pen') return;
        const now = Date.now();
        const gap = now - penLastMoveTime;
        penLastMoveTime = now;
        // After an explicit exit, ignore the pen until it has been away for a clear
        // gap (lifted out of range, then brought back) — so "X" actually exits.
        if (stylusReentryBlocked) {
            if (gap > 1000) stylusReentryBlocked = false;
            else return;
        }
        if (!editMode && !penEnterDebounce) {
            penEnterDebounce = setTimeout(function() {
                penEnterDebounce = null;
                if (!editMode && stylusAutoEdit && !stylusReentryBlocked && dotNetRef) {
                    stylusAutoEnteredEdit = true;
                    dotNetRef.invokeMethodAsync('OnStylusEvent', true).catch(() => {});
                }
            }, 80);
        }
    });
    // Finger touch → exit ONLY if pen auto-entered AND pen has been away ≥2 s
    document.addEventListener('pointerdown', function(e) {
        if (!stylusAutoEdit || e.pointerType !== 'touch') return;
        if (!editMode || !stylusAutoEnteredEdit) return;
        if (Date.now() - penLastMoveTime >= 2000) {
            stylusAutoEnteredEdit = false;
            if (dotNetRef) dotNetRef.invokeMethodAsync('OnStylusEvent', false).catch(() => {});
        }
    });

    // Pen front/barrel button (or flipped eraser end) → eraser, per chosen mode
    if (!_stylusBtnBound) {
        _stylusBtnBound = true;
        document.addEventListener('pointerdown', function(e) {
            if (e.pointerType !== 'pen' || stylusButtonMode === 'off' || !editMode) return;
            const barrel = e.button === 2 || e.button === 5 || (e.buttons & 2) || (e.buttons & 32);
            if (!barrel) return;
            if (stylusButtonMode === 'toggle') {
                if (_penBtnEraser) disengagePenEraser(); else engagePenEraser();
            } else {
                engagePenEraser();
            }
        }, true);
        document.addEventListener('pointermove', function(e) {
            if (e.pointerType !== 'pen' || stylusButtonMode !== 'hold') return;
            const held = (e.buttons & 2) || (e.buttons & 32);
            if (held && !_penBtnEraser) engagePenEraser();
            else if (!held && _penBtnEraser) disengagePenEraser();
        }, true);
        document.addEventListener('pointerup', function(e) {
            if (e.pointerType === 'pen' && stylusButtonMode === 'hold' && _penBtnEraser) disengagePenEraser();
        }, true);

        // Track pointer type + pen pressure (drives palm rejection + pressure-sensitive strokes).
        // Pointer events fire just before the paired touch/mouse events the drawing uses, so by
        // the time startDraw/onDrawMove run, these reflect the current contact.
        document.addEventListener('pointerdown', function(e) {
            _lastDownType = e.pointerType;
            if (e.pointerType === 'pen') { _penContact = true; _penSeenTs = Date.now(); if (e.pressure > 0) _penPressure = e.pressure; }
        }, true);
        document.addEventListener('pointermove', function(e) {
            if (e.pointerType === 'pen') { _penSeenTs = Date.now(); if (e.pressure > 0) _penPressure = e.pressure; }
        }, true);
        document.addEventListener('pointerup', function(e) {
            if (e.pointerType === 'pen') { _penContact = false; _penSeenTs = Date.now(); }
        }, true);
        document.addEventListener('pointercancel', function(e) {
            if (e.pointerType === 'pen') _penContact = false;
        }, true);
    }
};


// ═══════════════════════════════════════════════════════════════════
//  Placed-button overlay canvas
// ═══════════════════════════════════════════════════════════════════

let btnCanvas = null, btnCtx = null;
let _pageButtons = [];       // link points {id,page,nx,ny,label,color,isTarget} — page = single-mode display number
let _btnCurDisp = 0;         // display page shown in single/half-turn/image (for marker placement)
let _twoLeftDisp = 0, _twoRightDisp = 0, _twoLeftH = 0, _twoRightW = 0, _twoRightH = 0;
let _isPlacingBtn = false;   // placement mode active

window.initBtnCanvas = function(id) {
    btnCanvas = document.getElementById(id);
    if (!btnCanvas) return;
    btnCtx = btnCanvas.getContext('2d');
    // Overlay the PDF canvas exactly; it rides #viewer's transform like the other layers.
    btnCanvas.style.position = 'absolute';
    btnCanvas.style.top = '0';
    btnCanvas.style.left = '0';
    btnCanvas.style.width = '100%';
    btnCanvas.style.height = '100%';
    btnCanvas.style.display = 'block';
    btnCanvas.style.pointerEvents = 'none';   // taps fall through to the viewer's click handler
    btnCanvas.style.zIndex = '5';             // above pdf/marker/note so link markers stay visible
};

window.setPageButtons = function(buttons) {
    _pageButtons = buttons || [];
    _drawButtons();
};

window.setPlacingMode = function(on) { _isPlacingBtn = on; };

// Returns id of tapped button (source or target), or null. Distance is measured in
// screen pixels so the tap target stays the same size at any zoom level.
window.getButtonAtScreen = function(sx, sy) {
    if (!baseWidth || !baseHeight || !_pageButtons.length) return null;
    const HIT = 32;   // screen-px radius
    for (const b of _pageButtons) {
        const r = _btnRect(b.page == null ? _btnCurDisp : b.page);
        if (!r) continue;
        const bx = (r.x + b.nx * r.w) * zoom + panX;
        const by = (r.y + b.ny * r.h) * zoom + panY;
        if (Math.hypot(bx - sx, by - sy) < HIT) {
            return b.id + (b.isTarget ? ':target' : ':source');
        }
    }
    return null;
};

// Convert screen tap to normalized PDF coords
window.screenToNormalized = function(sx, sy) {
    if (!baseWidth || !baseHeight) return { x: 0.5, y: 0.5 };
    return {
        x: Math.max(0, Math.min(1, (sx - panX) / zoom / baseWidth)),
        y: Math.max(0, Math.min(1, (sy - panY) / zoom / baseHeight))
    };
};

// The rect (in base/composite canvas coords) occupied by a given single-mode display
// page under the CURRENT display mode, or null if that page isn't currently on screen.
// Lets link-point markers land on the right page in every mode, not just single.
function _btnRect(dp) {
    dp = parseInt(dp, 10);
    if (!Number.isInteger(dp) || !baseWidth || !baseHeight) return null;
    if (_displayMode === 'two') {
        if (dp === _twoLeftDisp)  return { x: 0, y: 0, w: _twoLeftW, h: _twoLeftH || baseHeight };
        if (dp === _twoRightDisp && _twoRightW)
            return { x: _twoLeftW + _twoGap, y: 0, w: _twoRightW, h: _twoRightH || baseHeight };
        return null;
    }
    if (_displayMode === 'scroll-v' || _displayMode === 'scroll-h') {
        const i = dp - 1;                                    // slot index: display page N is at slot N-1
        if (i < 0 || i >= _scrollPageW.length) return null;
        const w = _scrollPageW[i], h = _scrollPageH[i];
        return _displayMode === 'scroll-v'
            ? { x: Math.round((baseWidth - w) / 2), y: _scrollTops[i]  || 0, w, h }
            : { x: _scrollLefts[i] || 0, y: Math.round((baseHeight - h) / 2), w, h };
    }
    // single / half-turn / image: the canvas holds exactly the one shown page
    return dp === _btnCurDisp ? { x: 0, y: 0, w: baseWidth, h: baseHeight } : null;
}

function _drawButtons() {
    if (!btnCanvas || !btnCtx || !baseWidth || !baseHeight) return;
    // Match canvas buffer to the composite/page canvas
    btnCanvas.width  = baseWidth;
    btnCanvas.height = baseHeight;
    btnCtx.clearRect(0, 0, baseWidth, baseHeight);

    for (const b of _pageButtons) {
        const r = _btnRect(b.page == null ? _btnCurDisp : b.page);
        if (!r) continue;
        _drawMarker(btnCtx, r.x + b.nx * r.w, r.y + b.ny * r.h, b.label, b.color, b.isTarget);
    }
}

function _drawMarker(ctx, cx, cy, label, color, isTarget) {
    const R = 24;
    ctx.save();
    ctx.shadowColor = 'rgba(0,0,0,0.35)';
    ctx.shadowBlur  = 5;
    ctx.fillStyle   = color || (isTarget ? '#E53935' : '#387373');
    ctx.globalAlpha = 0.90;
    if (isTarget) {
        // Target: diamond shape
        ctx.beginPath();
        ctx.moveTo(cx, cy - R);
        ctx.lineTo(cx + R, cy);
        ctx.lineTo(cx, cy + R);
        ctx.lineTo(cx - R, cy);
        ctx.closePath();
    } else {
        // Source: circle
        ctx.beginPath(); ctx.arc(cx, cy, R, 0, Math.PI * 2);
    }
    ctx.fill();
    ctx.globalAlpha = 1; ctx.shadowBlur = 0;
    ctx.strokeStyle = 'rgba(255,255,255,0.85)'; ctx.lineWidth = 2.5;
    ctx.stroke();
    ctx.fillStyle = 'white';
    ctx.font = `bold 12px system-ui, sans-serif`;
    ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.fillText(isTarget ? '↩' : (label || '●').substring(0, 3), cx, cy);
    ctx.restore();
}

// ═══════════════════════════════════════════════════════════════════
//  Page preloading
// ═══════════════════════════════════════════════════════════════════

async function preloadPage(num) {
    if (_pageCache.has(num) || !pdfDoc || num < 1 || num > pdfDoc.numPages) return;
    try {
        const page = await pdfDoc.getPage(num);
        const vp   = page.getViewport({ scale: 2, rotation: pageRotation });
        const w = Math.ceil(vp.width), h = Math.ceil(vp.height);
        const c = makeCanvas(w, h);
        const cx = c.getContext('2d');
        cx.fillStyle = 'white'; cx.fillRect(0, 0, w, h);
        await page.render({ canvasContext: cx, viewport: vp }).promise;
        _pageCache.set(num, { canvas: c, width: w, height: h });
    } catch {}
}

function preloadAround(num) {
    for (let i = 1; i <= CACHE_AHEAD; i++) preloadPage(num + i);
    for (let i = 1; i <= CACHE_BEHIND; i++) preloadPage(num - i);
    for (const key of _pageCache.keys()) {
        if (key < num - CACHE_BEHIND - 1 || key > num + CACHE_AHEAD + 1) _pageCache.delete(key);
    }
}


// ═══════════════════════════════════════════════════════════════════
//  Loading spinner
// ═══════════════════════════════════════════════════════════════════

let _spinnerTimer = null;

function showSpinner() {
    clearTimeout(_spinnerTimer);
    _spinnerTimer = setTimeout(() => {
        const el = document.getElementById('page-spinner');
        if (el) el.style.display = 'flex';
    }, 120);
}

function hideSpinner() {
    clearTimeout(_spinnerTimer);
    const el = document.getElementById('page-spinner');
    if (el) el.style.display = 'none';
}


// ── First-page thumbnail (reuses the already-loaded pdfDoc) ─────────
// Render a first-page (or image) thumbnail. When a crop (cl,ct,cr,cb fractions) is
// given, the thumbnail shows only that region — so the library mirrors the cropped view.
window.renderThumbnail = async function (maxW, cl, ct, cr, cb) {
    try {
        const w = maxW || 180;
        if (isImageDoc && imgEl) {
            const iw  = imgEl.naturalWidth || w;
            const ih  = imgEl.naturalHeight || iw;
            const isc = w / iw;
            const fw  = Math.max(1, Math.round(iw * isc));
            const fh  = Math.max(1, Math.round(ih * isc));
            const full = document.createElement('canvas');
            full.width = fw; full.height = fh;
            const fcx = full.getContext('2d');
            fcx.fillStyle = '#ffffff'; fcx.fillRect(0, 0, fw, fh);
            fcx.drawImage(imgEl, 0, 0, fw, fh);
            return _thumbOut(full, cl, ct, cr, cb);
        }
        if (!pdfDoc) return null;
        const page = await pdfDoc.getPage(1);
        const base = page.getViewport({ scale: 1 });
        const vp = page.getViewport({ scale: w / base.width, rotation: pageRotation });
        const full = document.createElement('canvas');
        full.width  = Math.max(1, Math.round(vp.width));
        full.height = Math.max(1, Math.round(vp.height));
        const fcx = full.getContext('2d');
        fcx.fillStyle = '#ffffff'; fcx.fillRect(0, 0, full.width, full.height);
        await page.render({ canvasContext: fcx, viewport: vp }).promise;
        return _thumbOut(full, cl, ct, cr, cb);
    } catch (e) {
        return null;
    }
};

// Render a single page (1-based) to a thumbnail data URL, for the page-overview grid.
// Reuses the loaded pdfDoc / image and honours the current rotation.
window.renderPageThumb = async function (pageNum, maxW) {
    try {
        const w = maxW || 160;
        if (isImageDoc && imgEl) {
            const iw = imgEl.naturalWidth || w, ih = imgEl.naturalHeight || iw;
            const isc = w / iw;
            const fw = Math.max(1, Math.round(iw * isc)), fh = Math.max(1, Math.round(ih * isc));
            const c = document.createElement('canvas'); c.width = fw; c.height = fh;
            const cx = c.getContext('2d'); cx.fillStyle = '#ffffff'; cx.fillRect(0, 0, fw, fh);
            cx.drawImage(imgEl, 0, 0, fw, fh);
            return c.toDataURL('image/jpeg', 0.7);
        }
        if (!pdfDoc) return null;
        const n = Math.max(1, Math.min(pdfDoc.numPages, pageNum || 1));
        const page = await pdfDoc.getPage(n);
        const base = page.getViewport({ scale: 1 });
        const vp = page.getViewport({ scale: w / base.width, rotation: pageRotation });
        const c = document.createElement('canvas');
        c.width  = Math.max(1, Math.round(vp.width));
        c.height = Math.max(1, Math.round(vp.height));
        const cx = c.getContext('2d');
        cx.fillStyle = '#ffffff'; cx.fillRect(0, 0, c.width, c.height);
        await page.render({ canvasContext: cx, viewport: vp }).promise;
        return c.toDataURL('image/jpeg', 0.7);
    } catch (e) { return null; }
};

// Return the full canvas as JPEG, or just its [cl,ct,cr,cb] sub-rectangle when a crop is given.
function _thumbOut(full, cl, ct, cr, cb) {
    const hasCrop = (typeof cl === 'number' && typeof cr === 'number' &&
                     cr - cl > 0.01 && cb - ct > 0.01 &&
                     !(cl <= 0.002 && ct <= 0.002 && cr >= 0.998 && cb >= 0.998));
    if (!hasCrop) return full.toDataURL('image/jpeg', 0.72);
    const sx = Math.max(0, Math.round(cl * full.width));
    const sy = Math.max(0, Math.round(ct * full.height));
    const sw = Math.max(1, Math.round((cr - cl) * full.width));
    const sh = Math.max(1, Math.round((cb - ct) * full.height));
    const out = document.createElement('canvas');
    out.width = sw; out.height = sh;
    const ocx = out.getContext('2d');
    ocx.fillStyle = '#ffffff'; ocx.fillRect(0, 0, sw, sh);
    ocx.drawImage(full, sx, sy, sw, sh, 0, 0, sw, sh);
    return out.toDataURL('image/jpeg', 0.72);
}

// ── Dark mode toggle ────────────────────────────────────────────────
window.setDarkMode = function (on) {
    document.documentElement.classList.toggle('dark', !!on);
};
