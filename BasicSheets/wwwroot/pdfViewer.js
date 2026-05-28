// ── state ──────────────────────────────────────────────────────────────
let pdfDoc = null;
let pageNum = 1;
let canvas = null;
let ctx = null;
let noteCanvas = null;
let noteCtx = null;
let currentPdfKey = "";

let zoom = 1;
let minZoom = 0.5;
let maxZoom = 6;
let panX = 0;
let panY = 0;
let baseWidth = 0;
let baseHeight = 0;

let editMode = false;
let tool = "pen";
let penColor = "#000000";
let penSize = 3;

let drawing = false;
let lastX = 0;
let lastY = 0;
let markerPoints = [];
let markerPreviewData = null;
let savedImage = null;
let undoStack = [];
let redoStack = [];

let hairpinStart = null;
let hairpinPreviewData = null;

let dotNetRef = null;

let lastDist = null;
let dragging = false;
let dragStartX = 0;
let dragStartY = 0;
let touchPanStart = null;
let touchPanInitPan = null;

// ── PDF loading ────────────────────────────────────────────────────────
window.loadPdfData = async function (filename, data) {
    currentPdfKey = filename;

    const pdfjsLib = window['pdfjs-dist/build/pdf'];
    pdfjsLib.GlobalWorkerOptions.workerSrc =
        'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';

    pdfDoc = await pdfjsLib.getDocument({ data: data }).promise;
    return pdfDoc.numPages;
};

// Keep old URL-based loader as fallback
window.loadPdf = async function (url) {
    currentPdfKey = url;

    const pdfjsLib = window['pdfjs-dist/build/pdf'];
    pdfjsLib.GlobalWorkerOptions.workerSrc =
        'https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';

    pdfDoc = await pdfjsLib.getDocument(url).promise;
    return pdfDoc.numPages;
};

// ── canvas init ────────────────────────────────────────────────────────
window.initCanvas = function (pdfId, noteId) {
    canvas = document.getElementById(pdfId);
    ctx = canvas.getContext("2d");
    noteCanvas = document.getElementById(noteId);
    noteCtx = noteCanvas.getContext("2d");

    setupDrawing();
    setupMouseZoom();
    setupMousePan();
    setupTouchEvents();
    applyView();
};

// ── rendering ──────────────────────────────────────────────────────────
window.renderPage = async function (num) {
    pageNum = num;

    const page = await pdfDoc.getPage(num);
    const viewport = page.getViewport({ scale: 1.5 });

    canvas.width = viewport.width;
    canvas.height = viewport.height;
    noteCanvas.width = viewport.width;
    noteCanvas.height = viewport.height;

    await page.render({ canvasContext: ctx, viewport }).promise;

    baseWidth = canvas.width;
    baseHeight = canvas.height;
    loadNotes();
    applyView();
};

window.renderTwoPage = async function (num) {
    pageNum = num;

    const page1 = await pdfDoc.getPage(num);
    const vp1 = page1.getViewport({ scale: 1.5 });
    const t1 = document.createElement('canvas');
    t1.width = Math.ceil(vp1.width);
    t1.height = Math.ceil(vp1.height);
    await page1.render({ canvasContext: t1.getContext('2d'), viewport: vp1 }).promise;

    let t2 = null;
    if (num + 1 <= pdfDoc.numPages) {
        const page2 = await pdfDoc.getPage(num + 1);
        const vp2 = page2.getViewport({ scale: 1.5 });
        t2 = document.createElement('canvas');
        t2.width = Math.ceil(vp2.width);
        t2.height = Math.ceil(vp2.height);
        await page2.render({ canvasContext: t2.getContext('2d'), viewport: vp2 }).promise;
    }

    const gap = 16;
    const totalW = t2 ? t1.width + gap + t2.width : t1.width;
    const maxH = t2 ? Math.max(t1.height, t2.height) : t1.height;

    canvas.width = totalW;
    canvas.height = maxH;
    noteCanvas.width = totalW;
    noteCanvas.height = maxH;

    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, totalW, maxH);
    ctx.drawImage(t1, 0, 0);

    if (t2) {
        ctx.fillStyle = "#e0e0e0";
        ctx.fillRect(t1.width + 5, 0, 6, maxH);
        ctx.drawImage(t2, t1.width + gap, 0);
    }

    baseWidth = totalW;
    baseHeight = maxH;
    loadNotes();
    applyView();
};

// ── view ───────────────────────────────────────────────────────────────
function applyView() {
    const viewer = document.getElementById("viewer");
    if (viewer) viewer.style.transform = `translate(${panX}px, ${panY}px) scale(${zoom})`;
}

function clampPan() {
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    const w = baseWidth * zoom;
    const h = baseHeight * zoom;
    panX = Math.max(Math.min(0, vw - w), Math.min(0, panX));
    panY = Math.max(Math.min(0, vh - h), Math.min(0, panY));
}

window.getWindowWidth = function () { return window.innerWidth; };

// ── mouse zoom ─────────────────────────────────────────────────────────
function setupMouseZoom() {
    document.addEventListener("wheel", e => {
        if (!canvas) return;
        e.preventDefault();
        const oldZoom = zoom;
        zoom = Math.max(minZoom, Math.min(maxZoom, zoom + (e.deltaY < 0 ? 0.12 : -0.12)));
        const viewer = document.getElementById("viewer");
        if (!viewer) return;
        const rect = viewer.getBoundingClientRect();
        const mx = e.clientX - rect.left;
        const my = e.clientY - rect.top;
        panX = mx - (mx - panX) * (zoom / oldZoom);
        panY = my - (my - panY) * (zoom / oldZoom);
        clampPan();
        applyView();
    }, { passive: false });
}

// ── mouse pan ──────────────────────────────────────────────────────────
function setupMousePan() {
    const viewer = document.getElementById("viewer");
    if (!viewer) return;
    viewer.addEventListener("mousedown", e => {
        if (editMode) return;
        dragging = true;
        dragStartX = e.clientX - panX;
        dragStartY = e.clientY - panY;
        viewer.style.cursor = "grabbing";
    });
    window.addEventListener("mousemove", e => {
        if (!dragging) return;
        panX = e.clientX - dragStartX;
        panY = e.clientY - dragStartY;
        clampPan();
        applyView();
    });
    window.addEventListener("mouseup", () => {
        if (!dragging) return;
        dragging = false;
        const v = document.getElementById("viewer");
        if (v) v.style.cursor = editMode ? "crosshair" : "grab";
    });
}

// ── touch (pan + pinch zoom) ───────────────────────────────────────────
function setupTouchEvents() {
    document.addEventListener("touchstart", e => {
        if (editMode) return;
        if (e.touches.length === 1) {
            touchPanStart = { x: e.touches[0].clientX, y: e.touches[0].clientY };
            touchPanInitPan = { x: panX, y: panY };
        }
        lastDist = null;
    }, { passive: true });

    document.addEventListener("touchmove", e => {
        if (e.touches.length === 2) {
            e.preventDefault();
            const a = e.touches[0], b = e.touches[1];
            const dist = Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY);
            const cx = (a.clientX + b.clientX) / 2;
            const cy = (a.clientY + b.clientY) / 2;
            if (lastDist) {
                const oldZoom = zoom;
                zoom = Math.max(minZoom, Math.min(maxZoom, zoom + (dist - lastDist) * 0.005));
                const viewer = document.getElementById("viewer");
                if (viewer) {
                    const rect = viewer.getBoundingClientRect();
                    const mx = cx - rect.left;
                    const my = cy - rect.top;
                    panX = mx - (mx - panX) * (zoom / oldZoom);
                    panY = my - (my - panY) * (zoom / oldZoom);
                }
                clampPan();
                applyView();
            }
            lastDist = dist;
        } else if (e.touches.length === 1 && !editMode && touchPanStart) {
            e.preventDefault();
            panX = touchPanInitPan.x + (e.touches[0].clientX - touchPanStart.x);
            panY = touchPanInitPan.y + (e.touches[0].clientY - touchPanStart.y);
            clampPan();
            applyView();
        }
    }, { passive: false });

    document.addEventListener("touchend", e => {
        if (e.touches.length === 0) {
            touchPanStart = null;
            lastDist = null;
        }
    }, { passive: true });
}

// ── edit mode ──────────────────────────────────────────────────────────
window.setEditMode = function (on) {
    editMode = on;
    noteCanvas.style.pointerEvents = on ? "auto" : "none";
    const viewer = document.getElementById("viewer");
    if (viewer) viewer.style.cursor = on ? "crosshair" : "grab";
};

window.setTool = function (t) { tool = t; };
window.setColor = function (c) { penColor = c; };
window.setPenSize = function (s) { penSize = s; };
window.setPdfDotNetRef = function (ref) { dotNetRef = ref; };

// ── drawing setup ──────────────────────────────────────────────────────
function setupDrawing() {
    noteCanvas.addEventListener("mousedown", startDraw);
    noteCanvas.addEventListener("mousemove", drawMove);
    noteCanvas.addEventListener("mouseup", endDraw);
    noteCanvas.addEventListener("mouseleave", endDraw);
    noteCanvas.addEventListener("touchstart", startDraw, { passive: false });
    noteCanvas.addEventListener("touchmove", drawMove, { passive: false });
    noteCanvas.addEventListener("touchend", endDraw);
}

function getPos(e) {
    const rect = noteCanvas.getBoundingClientRect();
    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
    const clientY = e.touches ? e.touches[0].clientY : e.clientY;
    // Convert screen coords → canvas pixel coords (accounts for CSS zoom)
    return {
        x: (clientX - rect.left) * (noteCanvas.width / rect.width),
        y: (clientY - rect.top) * (noteCanvas.height / rect.height)
    };
}

const STAMP_TOOLS = ["flat", "sharp", "natural", "double-flat", "double-sharp", "fermata", "trill", "segno", "coda"];
const STAMP_SYMBOLS = {
    flat: "♭", sharp: "♯", natural: "♮",
    "double-flat": "𝄫", "double-sharp": "𝄪",
    fermata: "𝄐", trill: "tr", segno: "𝄋", coda: "𝄌"
};

// ── draw events ────────────────────────────────────────────────────────
function startDraw(e) {
    if (!editMode) return;
    if (e.touches && e.touches.length > 1) return;
    e.preventDefault();

    const p = getPos(e);
    lastX = p.x;
    lastY = p.y;

    if (STAMP_TOOLS.includes(tool)) {
        drawStamp(p.x, p.y, tool);
        saveNotes();
        return;
    }

    if (tool === "text") {
        if (dotNetRef) {
            const cx = e.touches ? e.touches[0].clientX : e.clientX;
            const cy = e.touches ? e.touches[0].clientY : e.clientY;
            dotNetRef.invokeMethodAsync('ShowTextInput', p.x, p.y, cx, cy);
        }
        return;
    }

    if (tool === "crescendo" || tool === "decrescendo") {
        drawing = true;
        hairpinStart = { x: p.x, y: p.y };
        hairpinPreviewData = noteCtx.getImageData(0, 0, noteCanvas.width, noteCanvas.height);
        return;
    }

    drawing = true;

    if (tool === "marker") {
        markerPoints = [p];
        markerPreviewData = noteCtx.getImageData(0, 0, noteCanvas.width, noteCanvas.height);
    } else {
        markerPoints = [];
    }
}

function drawMove(e) {
    if (!drawing || !editMode) return;
    if (e.touches && e.touches.length > 1) return;
    e.preventDefault();

    const p = getPos(e);

    noteCtx.lineCap = "round";
    noteCtx.lineJoin = "round";

    if (tool === "eraser") {
        noteCtx.globalCompositeOperation = "destination-out";
        noteCtx.lineWidth = penSize * 5;
        noteCtx.globalAlpha = 1;
        noteCtx.beginPath();
        noteCtx.moveTo(lastX, lastY);
        noteCtx.lineTo(p.x, p.y);
        noteCtx.stroke();

    } else if (tool === "marker") {
        markerPoints.push(p);
        // Restore state before stroke (synchronous)
        if (markerPreviewData) noteCtx.putImageData(markerPreviewData, 0, 0);
        noteCtx.globalCompositeOperation = "source-over";
        noteCtx.lineWidth = penSize * 7;
        noteCtx.strokeStyle = penColor;
        noteCtx.globalAlpha = 0.35;
        noteCtx.beginPath();
        noteCtx.moveTo(markerPoints[0].x, markerPoints[0].y);
        for (let i = 1; i < markerPoints.length; i++) noteCtx.lineTo(markerPoints[i].x, markerPoints[i].y);
        noteCtx.stroke();

    } else if (tool === "crescendo" || tool === "decrescendo") {
        if (hairpinPreviewData) noteCtx.putImageData(hairpinPreviewData, 0, 0);
        drawHairpin(hairpinStart.x, hairpinStart.y, p.x, p.y, tool === "crescendo");

    } else {
        // pen
        noteCtx.globalCompositeOperation = "source-over";
        noteCtx.lineWidth = penSize;
        noteCtx.strokeStyle = penColor;
        noteCtx.globalAlpha = 1;
        noteCtx.beginPath();
        noteCtx.moveTo(lastX, lastY);
        noteCtx.lineTo(p.x, p.y);
        noteCtx.stroke();
    }

    noteCtx.globalAlpha = 1;
    noteCtx.globalCompositeOperation = "source-over";
    lastX = p.x;
    lastY = p.y;
}

function endDraw() {
    if (!drawing) return;
    drawing = false;
    hairpinStart = null;
    hairpinPreviewData = null;
    markerPreviewData = null;
    markerPoints = [];
    saveNotes();
}

// ── stamp & shapes ─────────────────────────────────────────────────────
function drawStamp(x, y, stampType) {
    const symbol = STAMP_SYMBOLS[stampType];
    if (!symbol) return;
    const size = penSize * 12;
    noteCtx.save();
    noteCtx.globalCompositeOperation = "source-over";
    noteCtx.globalAlpha = 1;
    noteCtx.fillStyle = penColor;
    noteCtx.font = `bold ${size}px "FreeSerif", "Noto Serif", serif`;
    noteCtx.textAlign = "center";
    noteCtx.textBaseline = "middle";
    noteCtx.fillText(symbol, x, y);
    noteCtx.restore();
}

function drawHairpin(x1, y1, x2, y2, isCrescendo) {
    const spread = penSize * 10;
    noteCtx.save();
    noteCtx.strokeStyle = penColor;
    noteCtx.lineWidth = Math.max(1.5, penSize * 1.2);
    noteCtx.lineCap = "round";
    noteCtx.globalAlpha = 1;
    noteCtx.globalCompositeOperation = "source-over";
    noteCtx.beginPath();
    if (isCrescendo) {
        noteCtx.moveTo(x1, y1);
        noteCtx.lineTo(x2, y2 - spread);
        noteCtx.moveTo(x1, y1);
        noteCtx.lineTo(x2, y2 + spread);
    } else {
        noteCtx.moveTo(x1, y1 - spread);
        noteCtx.lineTo(x2, y2);
        noteCtx.moveTo(x1, y1 + spread);
        noteCtx.lineTo(x2, y2);
    }
    noteCtx.stroke();
    noteCtx.restore();
}

window.drawTextOnCanvas = function (x, y, text, color, fontSize) {
    noteCtx.save();
    noteCtx.font = `${fontSize}px sans-serif`;
    noteCtx.fillStyle = color;
    noteCtx.textBaseline = "top";
    noteCtx.globalAlpha = 1;
    noteCtx.globalCompositeOperation = "source-over";
    noteCtx.fillText(text, x, y);
    noteCtx.restore();
    saveNotes();
};

// ── save / load ────────────────────────────────────────────────────────
function storageKey() {
    return "notes_" + currentPdfKey + "_p" + pageNum;
}

function saveNotes() {
    const data = { img: noteCanvas.toDataURL(), w: noteCanvas.width, h: noteCanvas.height };
    try { localStorage.setItem(storageKey(), JSON.stringify(data)); } catch (_) {}
    savedImage = data;
    pushUndo(data);
}

function loadNotes() {
    noteCtx.clearRect(0, 0, noteCanvas.width, noteCanvas.height);
    savedImage = null;
    undoStack = [];
    redoStack = [];

    let raw;
    try { raw = localStorage.getItem(storageKey()); } catch (_) { return; }
    if (!raw) return;

    let data;
    if (raw.startsWith("data:image")) {
        data = { img: raw, w: noteCanvas.width, h: noteCanvas.height };
        try { localStorage.setItem(storageKey(), JSON.stringify(data)); } catch (_) {}
    } else {
        try { data = JSON.parse(raw); } catch (_) { return; }
    }

    savedImage = data;
    const img = new Image();
    img.onload = () => noteCtx.drawImage(img, 0, 0, noteCanvas.width, noteCanvas.height);
    img.src = data.img;
    undoStack = [JSON.stringify(data)];
}

window.clearNotes = function () {
    noteCtx.clearRect(0, 0, noteCanvas.width, noteCanvas.height);
    saveNotes();
};

function pushUndo(data) {
    undoStack.push(JSON.stringify(data));
    if (undoStack.length > 50) undoStack.shift();
    redoStack = [];
}

window.undoNote = function () {
    if (undoStack.length <= 1) return;
    redoStack.push(undoStack.pop());
    applyUndoData(JSON.parse(undoStack[undoStack.length - 1]));
};

window.redoNote = function () {
    if (!redoStack.length) return;
    const data = JSON.parse(redoStack.pop());
    undoStack.push(JSON.stringify(data));
    applyUndoData(data);
};

function applyUndoData(data) {
    savedImage = data;
    noteCtx.clearRect(0, 0, noteCanvas.width, noteCanvas.height);
    const img = new Image();
    img.onload = () => noteCtx.drawImage(img, 0, 0, noteCanvas.width, noteCanvas.height);
    img.src = data.img;
    try { localStorage.setItem(storageKey(), JSON.stringify(data)); } catch (_) {}
}
