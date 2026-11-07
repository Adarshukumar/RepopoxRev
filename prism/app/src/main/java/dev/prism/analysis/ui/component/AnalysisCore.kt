package dev.prism.analysis.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.prism.analysis.engine.ArcPoint
import dev.prism.analysis.engine.PrismReport
import dev.prism.analysis.ui.theme.PrismPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* ═══════════════════════════════════════════════════════════════════════
   The analysis core.

   This is the one piece of the UI that is allowed to be beautiful for its
   own sake, and even it is a readout. Its radial profile is the document's
   emotional arc, unrolled into a closed loop and resampled to 72 points:
   warm stretches of the text bulge outward, cold stretches contract. Turn
   the document around and you get the shape back.

   Everything that moves is bound to a computed value, which is the rule the
   whole app follows:

     rotation rate  ∝  rhythm01      varied prose turns faster than flat prose
     pulse depth    ∝  difficulty01  dense text throbs harder
     hue            =  valence       cold blue for negative, amber for positive
     saturation     ∝  Herdan's C    repetitive vocabulary looks washed out
     lobe amplitude ∝  volatility    a text that swings reaches further out

   A perfect circle is therefore meaningful too: it is a document with no
   emotional range, and the shape says so without being told to.
   ═══════════════════════════════════════════════════════════════════════ */

private const val SPOKES = 72

/**
 * @param live when false the frame clock stops entirely — no rotation, no
 *   pulse, no redraws. Set from the UI phase so an idle editor costs nothing.
 */
@Composable
fun AnalysisCore(
    report: PrismReport,
    palette: PrismPalette,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    /* ── the target radial profile ───────────────────────────────────── */
    val target = remember(report) { arcProfile(report.sentiment.arc, report.sentiment.volatility01) }

    /* Morph between whatever is currently on screen and the new target
       rather than snapping. A single progress value interpolates the whole
       array, so changing document never leaves a half-updated shape. */
    var from by remember { mutableStateOf(FloatArray(SPOKES)) }
    var to by remember { mutableStateOf(FloatArray(SPOKES)) }
    var progress by remember { mutableStateOf(1f) }

    LaunchedEffect(target) {
        from = profileAt(from, to, progress)
        to = target
        progress = 0f
    }

    /* Spring-ish ease driven by the frame clock. Written by hand instead of
       using Animatable so the morph and the idle spin share one ticker and
       the Canvas recomposes once per frame rather than twice. */
    var shape by remember { mutableStateOf(FloatArray(SPOKES)) }
    var rotation by remember { mutableStateOf(0f) }
    var pulse by remember { mutableStateOf(0f) }

    /* Keyed on the two values that set the rates, not just on `live`. Keying
       on `live` alone would capture rhythm and difficulty when the clock
       started and keep them forever, so loading a second document would
       change the shape but leave it turning at the first document's speed —
       exactly the kind of animation that looks bound to data but is not. */
    val rhythm = report.structure.rhythm01
    val difficulty = report.readability.difficulty01
    LaunchedEffect(live, rhythm, difficulty) {
        if (!live) return@LaunchedEffect
        var lastNanos = -1L
        /* Rotation speed: 6°/s for flat prose up to 34°/s for prose with a
           strongly varying rhythm. */
        val degreesPerSecond = 6f + rhythm.toFloat() * 28f
        /* Pulse period: 3.4 s for easy text down to 1.1 s for very dense. */
        val periodSeconds = 3.4f - difficulty.toFloat() * 2.3f
        val pulseRate = (2f * PI / periodSeconds).toFloat()

        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastNanos < 0) 0f else ((nanos - lastNanos) / 1e9f).coerceAtMost(0.1f)
                lastNanos = nanos

                rotation = (rotation + degreesPerSecond * dt) % 360f
                pulse = (pulse + pulseRate * dt) % (2f * PI.toFloat())

                /* Critically-damped approach: progress moves toward 1 fast at
                   first and eases in, which reads as the instrument settling
                   onto a new reading rather than as a tween. */
                if (progress < 1f) {
                    progress = (progress + dt * 2.6f).coerceAtMost(1f)
                }
                shape = profileAt(from, to, progress)
            }
        }
    }

    /* Guarantee the shape is populated even when the clock is stopped, so a
       paused core still draws the last document it read. */
    LaunchedEffect(target, live) {
        if (!live) shape = profileAt(from, to, if (progress >= 1f) 1f else progress)
    }

    val accent = palette.accent
    val glow = palette.accentGlow
    val dim = palette.accentDim
    val complement = palette.complement
    val lineColour = palette.line
    val pulseDepth = 0.02f + report.readability.difficulty01.toFloat() * 0.05f

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val reach = minOf(w, h) / 2f
        val profile = shape
        val rot = rotation * PI.toFloat() / 180f

        /* Pulse is a breathing scale on the whole form, not a separate
           animation layer, so the glow and the outline stay in register. */
        val breath = 1f + sin(pulse) * pulseDepth

        /* ── ambient glow ────────────────────────────────────────────── */
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glow, Color.Transparent),
                center = Offset(cx, cy),
                radius = reach * 1.05f,
            ),
            radius = reach * 1.05f,
            center = Offset(cx, cy),
        )

        /* ── instrument rings ────────────────────────────────────────── */
        /* Two faint reference circles. They do not move, which is what makes
           the morphing form readable against them. */
        drawCircle(
            color = lineColour,
            radius = reach * 0.90f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx()),
        )
        drawCircle(
            color = lineColour.copy(alpha = 0.5f),
            radius = reach * 0.46f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.dp.toPx()),
        )

        /* ── tick marks outside the form ─────────────────────────────── */
        /* 72 ticks, one per spoke; length follows that spoke's amplitude so
           the outer ring is a second, quieter rendering of the same data. */
        val tickInner = reach * 0.92f
        val strokePx = 1.4.dp.toPx()
        for (i in 0 until SPOKES) {
            val a = rot + i * (2f * PI.toFloat() / SPOKES)
            val amp = profile[i]
            val len = reach * (0.03f + amp * 0.10f)
            val ca = cos(a)
            val sa = sin(a)
            drawLine(
                color = if (amp > 0.5f) accent.copy(alpha = 0.85f) else dim,
                start = Offset(cx + ca * tickInner, cy + sa * tickInner),
                end = Offset(cx + ca * (tickInner + len), cy + sa * (tickInner + len)),
                strokeWidth = strokePx,
            )
        }

        /* ── the form itself ─────────────────────────────────────────── */
        val path = closedBlob(cx, cy, reach * 0.62f * breath, profile, rot)

        drawPath(
            path = path,
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.92f), dim, Color.Transparent),
                center = Offset(cx, cy),
                radius = reach * 0.80f,
            ),
        )
        drawPath(
            path = path,
            color = accent,
            style = Stroke(width = 2.dp.toPx()),
        )

        /* A second, smaller form offset by half a spoke and drawn in the
           complement colour. It is the same arc, so the two outlines agree
           wherever the document is emotionally flat and separate wherever it
           is not — disagreement between them is the volatility, visible. */
        val inner = closedBlob(
            cx, cy, reach * 0.40f * breath,
            profile, rot + PI.toFloat() / SPOKES,
        )
        drawPath(
            path = inner,
            color = complement.copy(alpha = 0.55f),
            style = Stroke(width = 1.5.dp.toPx()),
        )

        /* ── the nucleus ─────────────────────────────────────────────── */
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.9f), accent),
                center = Offset(cx, cy),
                radius = reach * 0.11f * breath,
            ),
            radius = reach * 0.11f * breath,
            center = Offset(cx, cy),
        )
    }
}

/* ── profile construction ─────────────────────────────────────────────── */

/**
 * Resample the emotional arc onto [SPOKES] evenly spaced angles and remap
 * valence from −1..+1 to 0..1.
 *
 * @param volatility scales the deviation from a flat circle, so a text that
 *   swings reaches further out in both directions than one that does not.
 */
internal fun arcProfile(arc: List<ArcPoint>, volatility: Double): FloatArray {
    val out = FloatArray(SPOKES)
    if (arc.isEmpty()) return out
    val swing = (0.35 + volatility.coerceIn(0.0, 1.0) * 0.65).toFloat()
    for (i in 0 until SPOKES) {
        val t = i.toDouble() / SPOKES
        /* Sample the arc at this position through the text. The arc is not
           periodic, so the seam at i = 0 is blended with the final point to
           avoid a visible notch where the loop closes. */
        val v = sampleArc(arc, t)
        val last = sampleArc(arc, 1.0)
        val first = sampleArc(arc, 0.0)
        /* Blend the closing seam over the last 12% of the loop. */
        val seam = if (t > 0.88) {
            val k = ((t - 0.88) / 0.12).toFloat()
            v * (1f - k) + ((last + first) / 2.0) * k
        } else v
        out[i] = ((seam + 1.0) / 2.0).toFloat().coerceIn(0f, 1f) * swing +
            (1f - swing) * 0.5f
    }
    return out
}

/** Linear interpolation across arc points, clamped at both ends. */
internal fun sampleArc(arc: List<ArcPoint>, t: Double): Double {
    if (arc.isEmpty()) return 0.0
    if (arc.size == 1) return arc[0].valence
    val target = t.coerceIn(0.0, 1.0)
    if (target <= arc.first().t) return arc.first().valence
    if (target >= arc.last().t) return arc.last().valence
    for (i in 0 until arc.size - 1) {
        val a = arc[i]
        val b = arc[i + 1]
        if (target in a.t..b.t) {
            val span = b.t - a.t
            if (span <= 0.0) return a.valence
            val k = (target - a.t) / span
            return a.valence + (b.valence - a.valence) * k
        }
    }
    return arc.last().valence
}

/** Interpolate between two equal-length profiles at an eased position. */
internal fun profileAt(from: FloatArray, to: FloatArray, progress: Float): FloatArray {
    if (to.isEmpty()) return from
    if (from.size != to.size) return to.copyOf()
    val p = progress.coerceIn(0f, 1f)
    /* Ease-out cubic: fast departure, gentle settle. */
    val e = 1f - (1f - p) * (1f - p) * (1f - p)
    val out = FloatArray(to.size)
    for (i in out.indices) out[i] = from[i] + (to[i] - from[i]) * e
    return out
}

/**
 * Build a smooth closed curve through the spoke endpoints.
 *
 * Catmull-Rom control points converted to cubics. Straight line segments
 * would make a 72-gon, and at the amplitudes involved the facets are visible;
 * this is what makes it read as a fluid form rather than a polygon.
 */
private fun closedBlob(
    cx: Float,
    cy: Float,
    baseRadius: Float,
    profile: FloatArray,
    rotationRadians: Float,
): Path {
    val n = profile.size
    val pts = ArrayList<Offset>(n)
    for (i in 0 until n) {
        val a = rotationRadians + i * (2f * PI.toFloat() / n)
        /* Amplitude 0.5 is the neutral radius; deviations scale outward for
           warm text and inward for cold. The 0.34 factor keeps the form from
           ever collapsing through the nucleus or swallowing the tick ring. */
        val r = baseRadius * (1f + (profile[i] - 0.5f) * 0.68f)
        pts.add(Offset(cx + cos(a) * r, cy + sin(a) * r))
    }

    val path = Path()
    if (n < 3) {
        if (n > 0) {
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until n) path.lineTo(pts[i].x, pts[i].y)
            path.close()
        }
        return path
    }

    path.moveTo(pts[0].x, pts[0].y)
    for (i in 0 until n) {
        val p0 = pts[(i - 1 + n) % n]
        val p1 = pts[i]
        val p2 = pts[(i + 1) % n]
        val p3 = pts[(i + 2) % n]
        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    path.close()
    return path
}
