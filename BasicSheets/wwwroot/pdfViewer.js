// ═══════════════════════════════════════════════════════════════════
//  pdfViewer.js – BasicSheets annotation engine
// ═══════════════════════════════════════════════════════════════════

// ── Canvas / PDF state ──────────────────────────────────────────────
let pdfDoc = null, pageNum = 1, currentPdfKey = "";
let canvas = null, ctx = null;
let markerCanvas = null, markerCtx = null;
let noteCanvas = null, noteCtx = null;
let penCanvas = null, penCtx = null;

let zoom = 1;
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
let _wasPinching = false;
let _twoFingerTapStart = 0;

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
    "dyn-ppp","dyn-pp","dyn-p","dyn-mp","dyn-mf","dyn-f","dyn-ff","dyn-fff","dyn-sf","dyn-sfz","dyn-fp"];
const STAMP_SYM   = {
    flat:"♭", sharp:"♯", natural:"♮",
    "double-flat":"𝄫", "double-sharp":"𝄪",
    fermata:"𝄐", trill:"tr",
    segno:"𝄋", coda:"𝄌",
    "dyn-ppp":"ppp", "dyn-pp":"pp", "dyn-p":"p", "dyn-mp":"mp", "dyn-mf":"mf",
    "dyn-f":"f", "dyn-ff":"ff", "dyn-fff":"fff", "dyn-sf":"sf", "dyn-sfz":"sfz", "dyn-fp":"fp"
};


// ═══════════════════════════════════════════════════════════════════
//  PDF loading
// ═══════════════════════════════════════════════════════════════════

let imgEl = null, imgUrl = null, isImageDoc = false;

async function destroyCurrentPdf() {
    _pageCache.clear();
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
    textAnnotations = []; hairpinAnnotations = [];
    undoStack = []; redoStack = [];
    baseWidth = 0; baseHeight = 0;
};


// ═══════════════════════════════════════════════════════════════════
//  Canvas init
// ═══════════════════════════════════════════════════════════════════

window.initCanvas = function(pdfId, markerId, noteId) {
    canvas       = document.getElementById(pdfId);     ctx       = canvas.getContext("2d");
    markerCanvas = document.getElementById(markerId);  markerCtx = markerCanvas.getContext("2d");
    noteCanvas   = document.getElementById(noteId);    noteCtx   = noteCanvas.getContext("2d");

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

window.renderPage = async function(num) {
    scrollMode = null; halfSide = null; _displayMode = 'single';
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
        const vp   = page.getViewport({ scale: 2 });
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
    pageNum = 1;
    showSpinner();
    let w = imgEl.naturalWidth || 1, h = imgEl.naturalHeight || 1;
    const maxDim = 2600;
    const sc = Math.min(1, maxDim / Math.max(w, h));
    w = Math.max(1, Math.round(w * sc));
    h = Math.max(1, Math.round(h * sc));
    canvas.width = markerCanvas.width = noteCanvas.width  = w;
    canvas.height = markerCanvas.height = noteCanvas.height = h;
    penCanvas.width = w; penCanvas.height = h;
    ctx.fillStyle = "white"; ctx.fillRect(0, 0, w, h);
    try { ctx.drawImage(imgEl, 0, 0, w, h); } catch (e) {}
    baseWidth = w; baseHeight = h;
    loadNotes();
    if (btnCanvas) { btnCanvas.width = baseWidth; btnCanvas.height = baseHeight; _drawButtons(); }
    applyPageScaling();
    hideSpinner();
};

window.renderTwoPage = async function(num) {
    scrollMode = null; halfSide = null; _displayMode = 'two';
    cancelActiveRender();
    const gen = _renderGen;
    showSpinner();
    beginPageTransition(num);
    pageNum = num;
    const p1 = await pdfDoc.getPage(num);
    if (gen !== _renderGen) return;
    const v1 = p1.getViewport({ scale: 2 });
    const t1 = makeCanvas(Math.ceil(v1.width), Math.ceil(v1.height));
    const c1 = t1.getContext("2d");
    c1.fillStyle = "white"; c1.fillRect(0, 0, t1.width, t1.height);
    await p1.render({ canvasContext: c1, viewport: v1 }).promise;
    if (gen !== _renderGen) return;

    let t2 = null;
    if (num + 1 <= pdfDoc.numPages) {
        const p2 = await pdfDoc.getPage(num + 1);
        if (gen !== _renderGen) return;
        const v2 = p2.getViewport({ scale: 2 });
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

window.renderHalfTurn = async function(num, topHalf) {
    scrollMode = null; halfSide = null;
    _displayMode = 'half-turn';
    _halfTurnTop = topHalf;
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
    const vp   = page.getViewport({ scale: 2 });
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

window.renderAllV = async function() {
    _displayMode = 'scroll-v';
    scrollMode = "scroll-v"; halfSide = null;
    cancelActiveRender();
    const gen = _renderGen;
    showSpinner();
    beginPageTransition(null);
    pageNum = -1;
    const gap = 24;
    const pages = [];

    for (let i = 1; i <= pdfDoc.numPages; i++) {
        const page = await pdfDoc.getPage(i);
        if (gen !== _renderGen) return;
        const vp   = page.getViewport({ scale: 2 });
        const w = Math.ceil(vp.width), h = Math.ceil(vp.height);
        const tmp = makeCanvas(w, h);
        const tc  = tmp.getContext("2d");
        tc.fillStyle = "white"; tc.fillRect(0, 0, w, h);
        await page.render({ canvasContext: tc, viewport: vp }).promise;
        if (gen !== _renderGen) return;
        pages.push(tmp);
    }

    const maxW   = Math.max(...pages.map(p => p.width));
    const totalH = pages.reduce((a, p) => a + p.height, 0) + gap * (pages.length - 1);

    canvas.width = markerCanvas.width = noteCanvas.width  = maxW;
    canvas.height = markerCanvas.height = noteCanvas.height = totalH;
    penCanvas.width = maxW; penCanvas.height = totalH;

    ctx.fillStyle = "#2a2a2a"; ctx.fillRect(0, 0, maxW, totalH);
    let y = 0;
    for (const pg of pages) {
        ctx.drawImage(pg, Math.round((maxW - pg.width) / 2), y);
        y += pg.height + gap;
    }

    baseWidth = maxW; baseHeight = totalH;
    loadNotes();
    if (btnCanvas) { btnCanvas.width = baseWidth; btnCanvas.height = baseHeight; _drawButtons(); }
    // Scroll mode: always fit to width, user scrolls vertically
    fitToWidth();
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

function getFitZoom() {
    if (!baseWidth || !baseHeight) return 1;
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
    return baseWidth * zoom > vw + 2 || baseHeight * zoom > availViewH() + 2;
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
}

function fitToWidth() {
    const hdrH = getHeaderHeight();
    zoom = getFitZoom();
    const scaledH = baseHeight * zoom;
    const viewH   = availViewH();
    panX = 0;
    panY = scaledH <= viewH ? hdrH + (viewH - scaledH) / 2 : hdrH;
    applyView();
}

function fitToScreen() {
    const hdrH  = getHeaderHeight();
    const viewW = window.innerWidth;
    const viewH = availViewH();
    zoom = Math.min(viewW / baseWidth, viewH / baseHeight);
    const scaledW = baseWidth * zoom;
    const scaledH = baseHeight * zoom;
    panX = (viewW - scaledW) / 2;
    panY = hdrH + (viewH - scaledH) / 2;
    applyView();
}

function fitToHeight() {
    const hdrH  = getHeaderHeight();
    const viewH = availViewH();
    zoom = viewH / baseHeight;
    const scaledW = baseWidth * zoom;
    panX = scaledW <= window.innerWidth ? (window.innerWidth - scaledW) / 2 : 0;
    panY = hdrH;
    applyView();
}

function fitFullScreen() {
    const hdrH  = getHeaderHeight();
    const viewW = window.innerWidth;
    const viewH = availViewH();
    zoom = Math.max(viewW / baseWidth, viewH / baseHeight);
    const scaledW = baseWidth * zoom;
    const scaledH = baseHeight * zoom;
    panX = (viewW - scaledW) / 2;
    panY = hdrH + (viewH - scaledH) / 2;
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
}

window.setPageScaling = function(mode) {
    pageScaling = mode || 'fit-screen';
    // Only apply to single/two page modes — scroll and half-turn have their own fit
    if (_displayMode === 'single' || _displayMode === 'two') {
        applyPageScaling();
    }
};

function clampPan() {
    const vw = window.innerWidth, vh = window.innerHeight;
    const w  = baseWidth * zoom, h = baseHeight * zoom;
    const hdrH  = getHeaderHeight();
    const viewH = availViewH();

    if (w <= vw) panX = (vw - w) / 2;
    else panX = Math.min(0, Math.max(vw - w, panX));

    if (h <= viewH) {
        panY = hdrH + (viewH - h) / 2;
    } else if (halfSide !== null) {
        panY = Math.max(hdrH - h / 2, Math.min(hdrH, panY));
    } else {
        panY = Math.min(hdrH, Math.max(hdrH + viewH - h, panY));
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

    const vw = window.innerWidth, vh = window.innerHeight;
    const w = baseWidth * targetZoom, h = baseHeight * targetZoom;
    const hdrH = getHeaderHeight(), viewH = availViewH();

    if (w <= vw) ePX = (vw - w) / 2;
    else ePX = Math.min(0, Math.max(vw - w, ePX));
    if (h <= viewH) ePY = hdrH + (viewH - h) / 2;
    else if (halfSide !== null) ePY = Math.max(hdrH - h / 2, Math.min(hdrH, ePY));
    else ePY = Math.min(hdrH, Math.max(hdrH + viewH - h, ePY));

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

window.getWindowWidth = () => window.innerWidth;


// ═══════════════════════════════════════════════════════════════════
//  Interaction — unified touch + mouse (MobileSheets-style)
// ═══════════════════════════════════════════════════════════════════

let _currentBg = null;
let _clickCapture = null;

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
    if (editMode) return;
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
    if (editMode) return;

    if (e.touches.length === 2) {
        e.preventDefault();
        _touchDragging = false;
        _wasPinching = true;
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

function onTouchEnd(e) {
    if (editMode) return;
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

// ── Mouse ──────────────────────────────────────────────────────────

function onWheelZoom(e) {
    if (!canvas || editMode) return;
    e.preventDefault();
    const oz = zoom;
    zoom = clampZoom(zoom * (e.deltaY < 0 ? 1.12 : 0.88));
    panX = e.clientX - (e.clientX - panX) * (zoom / oz);
    panY = e.clientY - (e.clientY - panY) * (zoom / oz);
    clampPan(); applyView();
}

function onMouseDown(e) {
    if (editMode || e.button !== 0) return;
    _panDragging = true;
    _panStartX = e.clientX - panX; _panStartY = e.clientY - panY;
    document.body.style.cursor = 'grabbing';
}

function onMouseMove(e) {
    if (!_panDragging) return;
    panX = e.clientX - _panStartX; panY = e.clientY - _panStartY;
    clampPan(); applyView();
}

function onMouseUp() {
    if (!_panDragging) return;
    _panDragging = false;
    document.body.style.cursor = editMode ? 'crosshair' : 'grab';
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
//  Color persistence
// ═══════════════════════════════════════════════════════════════════

window.loadColors           = () => { try { return localStorage.getItem("bs_colors_v2");  } catch { return null; } };
window.saveColors           = json => { try { localStorage.setItem("bs_colors_v2", json); } catch {} };
window.loadLegacyUserColors = () => { try { return localStorage.getItem("bs_user_colors"); } catch { return null; } };
window.loadUserColors = window.loadColors;
window.saveUserColors = window.saveColors;


// ── Orientation helper ─────────────────────────────────────────────
window.isLandscape = () => window.innerWidth > window.innerHeight;


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
            dotNetRef.invokeMethodAsync("ShowTextInput", p.x, p.y, sx, sy);
        }
        return;
    }

    if (tool === "crescendo" || tool === "decrescendo") {
        drawing = true;
        hairpinStart = { x: p.x, y: p.y };
        liveHairpin  = null;
        return;
    }

    if (tool === "marker") {
        drawing = true;
        markerPoints = [p];
        return;
    }

    // Pen / Eraser — begin a new vector stroke
    drawing = true;
    liveStroke = { color: penColor, size: penSize, eraser: tool === "eraser", points: [p] };
}


// ── onDrawMove ───────────────────────────────────────────────────────
function onDrawMove(e) {
    if (!editMode) return;
    if (e.touches && e.touches.length > 1) return;
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

    if (tool === "marker") {
        markerPoints.push(p);
        redrawMarkers(markerPoints);
        lastX = p.x; lastY = p.y; return;
    }

    // Pen / Eraser — accumulate points and update live preview
    if (liveStroke) {
        liveStroke.points.push(p);
        if (liveStroke.eraser) eraseNearby(p.x, p.y, penSize * 5);
        redrawPenLayer();
        redrawVectorLayer();
    }
    lastX = p.x; lastY = p.y;
}


// ── endDraw ──────────────────────────────────────────────────────────
function endDraw(e) {
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

function redrawPenLayer() {
    penCtx.clearRect(0, 0, penCanvas.width, penCanvas.height);
    const all = liveStroke ? [...penStrokes, liveStroke] : penStrokes;
    for (const s of all) {
        if (!s.points || s.points.length < 2) continue;
        penCtx.save();
        penCtx.lineCap = penCtx.lineJoin = "round";
        if (s.eraser) {
            penCtx.globalCompositeOperation = "destination-out";
            penCtx.strokeStyle = "rgba(0,0,0,1)";
            penCtx.lineWidth   = s.size * 5;
        } else {
            penCtx.globalCompositeOperation = "source-over";
            penCtx.strokeStyle = s.color;
            penCtx.lineWidth   = s.size;
        }
        penCtx.globalAlpha = 1;
        penCtx.beginPath();
        penCtx.moveTo(s.points[0].x, s.points[0].y);
        for (let i = 1; i < s.points.length; i++) penCtx.lineTo(s.points[i].x, s.points[i].y);
        penCtx.stroke();
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
        tc.beginPath(); tc.moveTo(m.points[0].x, m.points[0].y);
        for (let i = 1; i < m.points.length; i++) tc.lineTo(m.points[i].x, m.points[i].y);
        tc.stroke();
    }
    if (livePoints && livePoints.length > 1) {
        tc.strokeStyle = penColor;
        tc.lineWidth   = penSize * 3;
        tc.globalAlpha = 1; tc.globalCompositeOperation = "source-over";
        tc.beginPath(); tc.moveTo(livePoints[0].x, livePoints[0].y);
        for (let i = 1; i < livePoints.length; i++) tc.lineTo(livePoints[i].x, livePoints[i].y);
        tc.stroke();
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
}

function drawStampShape(ctx2d, s, alpha) {
    ctx2d.save();
    ctx2d.globalAlpha = alpha;
    ctx2d.fillStyle = s.color;
    const isDyn = typeof s.type === "string" && s.type.startsWith("dyn-");
    ctx2d.font = `${isDyn ? "italic bold" : "bold"} ${s.size * 5 + 15}px "FreeSerif","Noto Serif",serif`;
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

    if (changed) redrawMarkers(null);
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
        w: noteCanvas.width, h: noteCanvas.height
    };
}

function saveNotes() { persistNotes(); pushUndo(); }

function persistNotes() {
    try { localStorage.setItem(storageKey(), JSON.stringify(getFullState())); } catch {}
}

function loadNotes() {
    penCtx.clearRect(0, 0, penCanvas.width, penCanvas.height);
    noteCtx.clearRect(0, 0, noteCanvas.width, noteCanvas.height);
    markerCtx.clearRect(0, 0, markerCanvas.width, markerCanvas.height);
    penStrokes = [];
    markerAnnotations  = []; stampAnnotations   = [];
    textAnnotations    = []; hairpinAnnotations = [];
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

    if (data.markerData)  markerAnnotations  = data.markerData;
    if (data.stampData)   stampAnnotations   = data.stampData;
    if (data.textData)    textAnnotations    = data.textData;
    if (data.hairpinData) hairpinAnnotations = data.hairpinData;

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
    textAnnotations    = []; hairpinAnnotations = [];
    saveNotes();
};

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
let threeFingSwipeStartX = 0, threeFingSwipeStartY = 0;
let swipeFromBottomY = 0;

window.initGestures = function(config) {
    gestureConfig = config || {};
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
    }
};


// ═══════════════════════════════════════════════════════════════════
//  Placed-button overlay canvas
// ═══════════════════════════════════════════════════════════════════

let btnCanvas = null, btnCtx = null;
let _pageButtons = [];       // buttons for current page {id,nx,ny,label,color,isTarget}
let _isPlacingBtn = false;   // placement mode active

window.initBtnCanvas = function(id) {
    btnCanvas = document.getElementById(id);
    if (!btnCanvas) return;
    btnCtx = btnCanvas.getContext('2d');
    btnCanvas.style.pointerEvents = 'none';
    btnCanvas.style.zIndex = '5';
};

window.setPageButtons = function(buttons) {
    _pageButtons = buttons || [];
    _drawButtons();
};

window.setPlacingMode = function(on) { _isPlacingBtn = on; };

// Returns id of tapped button (source or target), or null
window.getButtonAtScreen = function(sx, sy) {
    if (!baseWidth || !baseHeight || !_pageButtons.length) return null;
    const cx = (sx - panX) / zoom;
    const cy = (sy - panY) / zoom;
    const HIT = 30;
    for (const b of _pageButtons) {
        if (Math.hypot(b.nx * baseWidth - cx, b.ny * baseHeight - cy) < HIT) {
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

function _drawButtons() {
    if (!btnCanvas || !btnCtx || !baseWidth || !baseHeight) return;
    // Match canvas buffer to PDF canvas
    btnCanvas.width  = baseWidth;
    btnCanvas.height = baseHeight;
    btnCtx.clearRect(0, 0, baseWidth, baseHeight);

    for (const b of _pageButtons) {
        const cx = b.nx * baseWidth;
        const cy = b.ny * baseHeight;
        _drawMarker(btnCtx, cx, cy, b.label, b.color, b.isTarget);
    }
}

function _drawMarker(ctx, cx, cy, label, color, isTarget) {
    const R = 20;
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
        const vp   = page.getViewport({ scale: 2 });
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
window.renderThumbnail = async function (maxW) {
    try {
        if (isImageDoc && imgEl) {
            const iw  = imgEl.naturalWidth || (maxW || 180);
            const isc = (maxW || 180) / iw;
            const tiw = Math.max(1, Math.round(iw * isc));
            const tih = Math.max(1, Math.round((imgEl.naturalHeight || iw) * isc));
            const ic  = document.createElement('canvas');
            ic.width = tiw; ic.height = tih;
            const icx = ic.getContext('2d');
            icx.fillStyle = '#ffffff'; icx.fillRect(0, 0, tiw, tih);
            icx.drawImage(imgEl, 0, 0, tiw, tih);
            return ic.toDataURL('image/jpeg', 0.72);
        }
        if (!pdfDoc) return null;
        const page = await pdfDoc.getPage(1);
        const base = page.getViewport({ scale: 1 });
        const scale = (maxW || 180) / base.width;
        const vp = page.getViewport({ scale: scale });
        const c = document.createElement('canvas');
        c.width  = Math.max(1, Math.round(vp.width));
        c.height = Math.max(1, Math.round(vp.height));
        const ctx = c.getContext('2d');
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, c.width, c.height);
        await page.render({ canvasContext: ctx, viewport: vp }).promise;
        return c.toDataURL('image/jpeg', 0.72);
    } catch (e) {
        return null;
    }
};

// ── Dark mode toggle ────────────────────────────────────────────────
window.setDarkMode = function (on) {
    document.documentElement.classList.toggle('dark', !!on);
};
