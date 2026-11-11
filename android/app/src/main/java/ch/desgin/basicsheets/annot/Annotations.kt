package ch.desgin.basicsheets.annot

import kotlinx.serialization.Serializable

/**
 * Annotation model for one page. Mirrors the MAUI JSON shape (penStrokes/markerData/stampData/
 * textData/hairpinData/shapeData) but coordinates are NORMALIZED to [0,1] of the page, so marks
 * render correctly at any size/device (the MAUI app stored absolute scale-2 px). Fresh data, so
 * no migration. Colors are packed ARGB ints (Compose Color.toArgb()).
 */

@Serializable
data class Pt(val x: Float, val y: Float)

@Serializable
data class PenStroke(val points: List<Pt>, val color: Int, val size: Float, val eraser: Boolean = false)

@Serializable
data class MarkerStroke(val points: List<Pt>, val color: Int, val size: Float)

@Serializable
data class Stamp(val type: String, val x: Float, val y: Float, val color: Int, val size: Float)

@Serializable
data class TextAnnot(val text: String, val x: Float, val y: Float, val color: Int, val fontSize: Float)

@Serializable
data class Hairpin(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int, val size: Float, val crescendo: Boolean)

@Serializable
data class ShapeAnnot(val kind: String, val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int, val size: Float, val fill: Boolean = false)

@Serializable
data class PageAnnotations(
    val penStrokes: List<PenStroke> = emptyList(),
    val markerData: List<MarkerStroke> = emptyList(),
    val stampData: List<Stamp> = emptyList(),
    val textData: List<TextAnnot> = emptyList(),
    val hairpinData: List<Hairpin> = emptyList(),
    val shapeData: List<ShapeAnnot> = emptyList(),
) {
    fun isEmpty(): Boolean =
        penStrokes.isEmpty() && markerData.isEmpty() && stampData.isEmpty() &&
            textData.isEmpty() && hairpinData.isEmpty() && shapeData.isEmpty()
}

/** Annotation tools, mirroring the MAUI editor's palette. */
enum class Tool { PEN, MARKER, ERASER, STAMP, TEXT, CRESCENDO, DECRESCENDO, LINE, ARROW, RECT, ELLIPSE }

/** Freehand drag tools that build a point path. */
val Tool.isStroke: Boolean get() = this == Tool.PEN || this == Tool.MARKER

/** Drag-from-a to-b tools (shapes + hairpins). */
val Tool.isTwoPoint: Boolean get() = this == Tool.CRESCENDO || this == Tool.DECRESCENDO ||
    this == Tool.LINE || this == Tool.ARROW || this == Tool.RECT || this == Tool.ELLIPSE

/** Single-tap placement tools. */
val Tool.isTap: Boolean get() = this == Tool.STAMP || this == Tool.TEXT

/** The ShapeAnnot.kind string for a shape tool. */
val Tool.shapeKind: String
    get() = when (this) {
        Tool.ARROW -> "arrow"
        Tool.RECT -> "rect"
        Tool.ELLIPSE -> "ellipse"
        else -> "line"
    }
