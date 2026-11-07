package dev.prism.analysis.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.prism.analysis.engine.ArcPoint
import dev.prism.analysis.engine.HistogramBucket
import dev.prism.analysis.engine.WordFrequency
import dev.prism.analysis.ui.theme.PrismPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/* ═══════════════════════════════════════════════════════════════════════
   Instruments.

   One rule governs every widget in this file: an animated float is produced
   once and then read by both the drawing and the label. The arc and the
   number underneath it are the same value at the same instant, so a gauge
   can never show "0.62" while its arc sits at 0.4. That is what makes the
   animation a readout instead of a transition, and it is why nothing here
   animates on a fixed timer.
   ═══════════════════════════════════════════════════════════════════════ */

private const val SWEEP_MS = 900
private const val STAGGER_MS = 70

/* ── typography ───────────────────────────────────────────────────────── */

@Composable
fun LabelText(
    text: String,
    palette: PrismPalette,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Text(
        text = text,
        color = if (emphasized) palette.textMid else palette.textLow,
        fontSize = 9.5.sp,
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
        letterSpacing = 1.7.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun ValueText(
    text: String,
    palette: PrismPalette,
    modifier: Modifier = Modifier,
    size: Int = 28,
    color: Color? = null,
) {
    Text(
        text = text,
        color = color ?: palette.textHigh,
        fontSize = size.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.8).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun BodyText(
    text: String,
    palette: PrismPalette,
    modifier: Modifier = Modifier,
    size: Int = 12,
) {
    Text(
        text = text,
        color = palette.textMid,
        fontSize = size.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = (size + 5).sp,
        modifier = modifier,
    )
}

@Composable
fun SectionHeader(
    title: String,
    note: String,
    palette: PrismPalette,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        LabelText(title, palette, emphasized = true)
        Spacer(Modifier.height(3.dp))
        BodyText(note, palette, size = 11)
    }
}

/* ── the radial gauge ─────────────────────────────────────────────────── */

/**
 * A 240° arc gauge with a count-up readout.
 *
 * @param value01   the normalised value; the arc sweeps 240° across 0..1
 * @param display   the formatted string to show — derived from the *animated*
 *                  value by the caller so the digits and the arc agree
 */
@Composable
fun GaugeRing(
    value01: Float,
    display: String,
    label: String,
    palette: PrismPalette,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    size: Dp = 116.dp,
    delayIndex: Int = 0,
) {
    val animated by animateFloatAsState(
        targetValue = value01.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = SWEEP_MS,
            delayMillis = delayIndex * STAGGER_MS,
            easing = FastOutSlowInEasing,
        ),
    )
    val sweep = animated * 240f
    val start = 150f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                val stroke = 7.dp.toPx()
                val inset = stroke / 2f + 1.dp.toPx()
                val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                val topLeft = Offset(inset, inset)

                /* track */
                drawArc(
                    color = palette.line,
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )

                /* value */
                if (kotlin.math.abs(sweep) > 0.01f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                palette.accentDim,
                                palette.accent,
                                palette.accentDim,
                            ),
                            center = Offset(this.size.width / 2, this.size.height / 2),
                        ),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }

                /* end cap dot, so the leading edge of the sweep is legible
                   against the track even at very small values */
                val endAngle = (start + sweep) * PI.toFloat() / 180f
                val r = arcSize.width / 2f
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = 2.2.dp.toPx(),
                    center = Offset(
                        this.size.width / 2 + cos(endAngle) * r,
                        this.size.height / 2 + sin(endAngle) * r,
                    ),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ValueText(display, palette, size = if (size >= 110.dp) 24 else 18)
                if (sublabel != null) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = sublabel,
                        color = palette.accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LabelText(label, palette, modifier = Modifier.fillMaxWidth(), emphasized = true)
    }
}

/* ── linear scale bar ─────────────────────────────────────────────────── */

/**
 * A value placed on a labelled horizontal scale, for numbers that only mean
 * something relative to a published range.
 *
 * The bar is drawn in a Canvas rather than composed from Boxes so the fill,
 * the travelling head and its highlight are all derived from one animated
 * float and cannot drift apart mid-transition.
 *
 * Bipolar values are not handled here — [SentimentDial] is the instrument for
 * those, because a signed value's interesting property is which side of zero
 * it sits on, and a bar anchored at the left edge hides that.
 */
@Composable
fun ScaleBar(
    value01: Float,
    leftLabel: String,
    rightLabel: String,
    title: String,
    display: String,
    palette: PrismPalette,
    modifier: Modifier = Modifier,
    note: String? = null,
    delayIndex: Int = 0,
) {
    val animated by animateFloatAsState(
        targetValue = value01.coerceIn(0f, 1f),
        animationSpec = tween(SWEEP_MS, delayIndex * STAGGER_MS, FastOutSlowInEasing),
    )
    val accent = palette.accent
    val accentDim = palette.accentDim
    val track = palette.line

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            LabelText(title, palette, emphasized = true, modifier = Modifier.weight(1f))
            ValueText(display, palette, size = 17, color = accent)
        }
        if (note != null) {
            Spacer(Modifier.height(2.dp))
            BodyText(note, palette, size = 10.5.toInt())
        }
        Spacer(Modifier.height(8.dp))

        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val barH = 4.dp.toPx()
            val top = (size.height - barH) / 2f
            val corner = CornerRadius(barH / 2f, barH / 2f)

            /* track */
            drawRoundRect(
                color = track,
                topLeft = Offset(0f, top),
                size = Size(size.width, barH),
                cornerRadius = corner,
            )

            val headR = 5.5.dp.toPx()
            val width = max(animated * size.width, barH)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(accentDim, accent)),
                topLeft = Offset(0f, top),
                size = Size(width, barH),
                cornerRadius = corner,
            )
            drawCircle(
                color = accent,
                radius = headR,
                center = Offset(width, top + barH / 2f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = headR * 0.38f,
                center = Offset(width, top + barH / 2f),
            )
        }

        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LabelText(leftLabel, palette)
            LabelText(rightLabel, palette)
        }
    }
}

/* ── metric tile ──────────────────────────────────────────────────────── */

@Composable
fun MetricTile(
    label: String,
    value: String,
    palette: PrismPalette,
    modifier: Modifier = Modifier,
    hint: String? = null,
    accent: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (accent) palette.accent.copy(alpha = 0.10f) else palette.panel)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        LabelText(label, palette)
        Spacer(Modifier.height(6.dp))
        ValueText(
            value,
            palette,
            size = 21,
            color = if (accent) palette.accent else palette.textHigh,
        )
        if (hint != null) {
            Spacer(Modifier.height(3.dp))
            BodyText(hint, palette, size = 10.5.toInt())
        }
    }
}

/* ── histogram ────────────────────────────────────────────────────────── */

/**
 * Sentence-length distribution. Bars grow from the baseline with a stagger so
 * the shape resolves left to right, and the tallest bar is marked — the
 * reader's eye lands on the mode without it being annotated in words.
 */
@Composable
fun HistogramChart(
    buckets: List<HistogramBucket>,
    palette: PrismPalette,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
) {
    val peak = buckets.maxOfOrNull { it.count } ?: 0
    val fractions = buckets.map { if (peak <= 0) 0f else it.count.toFloat() / peak.toFloat() }

    Row(
        modifier = modifier.fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        buckets.forEachIndexed { index, bucket ->
            val animated by animateFloatAsState(
                targetValue = fractions.getOrElse(index) { 0f },
                animationSpec = tween(
                    durationMillis = 720,
                    delayMillis = index * 90,
                    easing = FastOutSlowInEasing,
                ),
            )
            val isPeak = peak > 0 && bucket.count == peak
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (bucket.count > 0) {
                    Text(
                        text = bucket.count.toString(),
                        color = if (isPeak) palette.accent else palette.textLow,
                        fontSize = 10.sp,
                        fontWeight = if (isPeak) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        /* the bar takes the remaining height; a zero-height box
                           would still reserve space, so keep a 2dp floor */
                        .height((height - 44.dp) * max(animated, 0.015f))
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                        .background(
                            if (isPeak) {
                                Brush.verticalGradient(
                                    listOf(palette.accent, palette.accentDim)
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(palette.line, palette.line.copy(alpha = 0.4f))
                                )
                            }
                        )
                )
                Spacer(Modifier.height(6.dp))
                LabelText(bucket.label, palette)
            }
        }
    }
}

/* ── the emotional arc ────────────────────────────────────────────────── */

/**
 * Valence through the document, drawn as a line that draws itself.
 *
 * The reveal is a progress float, and the same float clips both the stroke and
 * the fill, so the shaded area never runs ahead of the line above it.
 */
@Composable
fun ArcChart(
    points: List<ArcPoint>,
    palette: PrismPalette,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
) {
    val reveal by animateFloatAsState(
        targetValue = if (points.size < 2) 0f else 1f,
        animationSpec = tween(1100, 120, FastOutSlowInEasing),
    )

    val axis = palette.line
    val zero = palette.textLow
    val positive = palette.accent
    val negative = palette.complement

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (points.size < 2) return@Canvas

        val padL = 4.dp.toPx()
        val padR = 4.dp.toPx()
        val padT = 10.dp.toPx()
        val padB = 10.dp.toPx()
        val plotW = size.width - padL - padR
        val plotH = size.height - padT - padB
        val midY = padT + plotH / 2f

        fun px(t: Double): Float = padL + (plotW * t).toFloat()
        fun py(v: Double): Float = midY - (plotH / 2f * v).toFloat()

        /* zero line — the emotional baseline */
        drawLine(
            color = zero.copy(alpha = 0.55f),
            start = Offset(padL, midY),
            end = Offset(padL + plotW, midY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(4.dp.toPx(), 4.dp.toPx())
            ),
        )
        /* frame top and bottom */
        drawLine(axis.copy(alpha = 0.4f), Offset(padL, padT), Offset(padL + plotW, padT), 1.dp.toPx())
        drawLine(axis.copy(alpha = 0.4f), Offset(padL, padT + plotH), Offset(padL + plotW, padT + plotH), 1.dp.toPx())

        val n = points.size
        val revealCount = (reveal * (n - 1)).coerceIn(0f, (n - 1).toFloat())
        val whole = revealCount.toInt()

        val stroke = Path()
        val fill = Path()
        var started = false
        for (i in 0..whole) {
            val x = px(points[i].t)
            val y = py(points[i].valence)
            if (!started) {
                stroke.moveTo(x, y)
                fill.moveTo(x, midY)
                fill.lineTo(x, y)
                started = true
            } else {
                stroke.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        /* the partial segment, so the line leads exactly where the reveal is
           rather than jumping a whole sample at a time */
        val frac = revealCount - whole
        if (whole < n - 1 && frac > 0f) {
            val a = points[whole]
            val b = points[whole + 1]
            val x = px(a.t + (b.t - a.t) * frac)
            val y = py(a.valence + (b.valence - a.valence) * frac)
            stroke.lineTo(x, y)
            fill.lineTo(x, y)
            fill.lineTo(x, midY)
        } else if (started) {
            fill.lineTo(px(points[whole].t), midY)
        }
        if (started) fill.close()

        if (started) {
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    0f to positive.copy(alpha = 0.30f),
                    0.5f to positive.copy(alpha = 0.05f),
                    0.5f to negative.copy(alpha = 0.05f),
                    1f to negative.copy(alpha = 0.30f),
                ),
            )
            drawPath(
                path = stroke,
                brush = Brush.horizontalGradient(listOf(negative, positive)),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        /* sample dots, appearing as the line reaches them */
        for (i in 0..minOf(whole, n - 1)) {
            val warm = points[i].valence >= 0
            drawCircle(
                color = if (warm) positive else negative,
                radius = 2.6.dp.toPx(),
                center = Offset(px(points[i].t), py(points[i].valence)),
            )
        }
    }
}

/* ── frequency bars ───────────────────────────────────────────────────── */

/**
 * Rank-ordered word frequency with each word's deviation from the share
 * Zipf's law predicts for its rank. Content words are drawn in the accent and
 * stopwords dimmed, which is the same distinction the engine computes.
 */
@Composable
fun FrequencyBars(
    words: List<WordFrequency>,
    palette: PrismPalette,
    modifier: Modifier = Modifier,
) {
    val peak = words.maxOfOrNull { it.count } ?: 0
    Column(modifier = modifier.fillMaxWidth()) {
        words.forEachIndexed { index, word ->
            val target = if (peak <= 0) 0f else word.count.toFloat() / peak.toFloat()
            val animated by animateFloatAsState(
                targetValue = target,
                animationSpec = tween(640, index * 45, FastOutSlowInEasing),
            )
            val zipf by animateFloatAsState(
                targetValue = word.zipfDeviation.toFloat().coerceIn(-1f, 1f),
                animationSpec = tween(640, index * 45 + 200, FastOutSlowInEasing),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = (index + 1).toString().padStart(2, '0'),
                    color = palette.textLow,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.width(20.dp),
                )
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = word.word,
                            color = if (word.isStopword) palette.textLow else palette.textHigh,
                            fontSize = 12.5.sp,
                            fontWeight = if (word.isStopword) FontWeight.Normal else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = count(word.count),
                            color = palette.textMid,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(palette.line.copy(alpha = 0.6f))
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(fraction = max(animated, 0.006f))
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(
                                    if (word.isStopword) palette.textLow.copy(alpha = 0.55f)
                                    else palette.accent
                                )
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                /* Zipf deviation: a small bar either side of a centre line.
                   Right means the word appears more often than its rank
                   predicts, left means less. */
                Box(Modifier.width(34.dp).height(16.dp)) {
                    Box(
                        Modifier.width(1.dp).height(16.dp).align(Alignment.Center)
                            .background(palette.line)
                    )
                    /* Dp is Comparable but kotlin.math.max has no overload for
                       it, so the floor is applied with coerceAtLeast. */
                    val w = (kotlin.math.abs(zipf) * 15f).dp.coerceAtLeast(0.5.dp)
                    Box(
                        Modifier
                            .width(w)
                            .height(3.dp)
                            .align(if (zipf >= 0) Alignment.CenterEnd else Alignment.CenterStart)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(if (zipf >= 0) palette.accent else palette.complement)
                    )
                }
            }
        }
    }
}

/* ── the working shimmer ──────────────────────────────────────────────── */

/** Shown only while an analysis is in flight. It is driven by an infinite
 *  transition rather than a looping drawable so it stops the instant the
 *  phase changes. */
@Composable
fun Shimmer(palette: PrismPalette, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    val transition = rememberInfiniteTransition()
    val shift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(
                Brush.horizontalGradient(
                    0f to palette.line.copy(alpha = 0.25f),
                    (shift * 0.33f).coerceIn(0f, 1f) to palette.accent.copy(alpha = 0.85f),
                    ((shift * 0.33f) + 0.30f).coerceIn(0f, 1f) to palette.line.copy(alpha = 0.25f),
                    1f to palette.line.copy(alpha = 0.25f),
                )
            )
    )
}

/* ── a dial for bipolar values ────────────────────────────────────────── */

/**
 * Sentiment valence on a semicircular dial with a needle. −1 points left,
 * 0 straight up, +1 right. A needle rather than a filling arc, because the
 * interesting fact about valence is which side of neutral it sits on and how
 * far, and a needle shows both at once.
 */
@Composable
fun SentimentDial(
    valence: Double,
    palette: PrismPalette,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    delayIndex: Int = 0,
) {
    val animated by animateFloatAsState(
        targetValue = (valence.toFloat()).coerceIn(-1f, 1f),
        animationSpec = tween(SWEEP_MS + 200, delayIndex * STAGGER_MS, FastOutSlowInEasing),
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2f + 2.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f

            /* track */
            drawArc(
                color = palette.line,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            /* warm half, faintly, to establish which side is which */
            drawArc(
                color = palette.accent.copy(alpha = 0.18f),
                startAngle = 270f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            /* cold half */
            drawArc(
                color = palette.complement.copy(alpha = 0.18f),
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            /* the value itself, from the top toward whichever side */
            drawArc(
                color = if (animated >= 0) palette.accent else palette.complement,
                startAngle = 270f,
                sweepAngle = animated * 90f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            /* needle */
            val angle = (180f + (animated + 1f) / 2f * 180f) * PI.toFloat() / 180f
            val needleLen = arcSize.width / 2f - 6.dp.toPx()
            drawLine(
                color = palette.textHigh,
                start = Offset(cx, cy),
                end = Offset(cx + cos(angle) * needleLen, cy + sin(angle) * needleLen),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(color = palette.textHigh, radius = 4.dp.toPx(), center = Offset(cx, cy))
            drawCircle(color = palette.background, radius = 1.6.dp.toPx(), center = Offset(cx, cy))

            /* end labels */
            val labelR = arcSize.width / 2f + 2.dp.toPx()
            listOf(
                Triple(180f, "−", palette.complement),
                Triple(270f, "0", palette.textLow),
                Triple(360f, "+", palette.accent),
            ).forEach { (deg, _, colour) ->
                val a = deg * PI.toFloat() / 180f
                drawCircle(
                    color = colour.copy(alpha = 0.9f),
                    radius = 2.dp.toPx(),
                    center = Offset(cx + cos(a) * labelR, cy + sin(a) * labelR),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
        ) {
            ValueText(num(animated.toDouble(), 3, signed = true), palette, size = 19)
        }
    }
}

/* ── small shared bits ────────────────────────────────────────────────── */

/** A thin rule that picks up the accent, used to separate layer sections. */
@Composable
fun Hairline(palette: PrismPalette, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        palette.line,
                        palette.accent.copy(alpha = 0.35f),
                        palette.line,
                        Color.Transparent,
                    )
                )
            )
    )
}

/** Convenience: the animated fraction of a readability score against the top
 *  of its published range, so gauges are comparable across formulas. */
internal fun normalise(value: Double, low: Double, high: Double): Float {
    if (value.isNaN() || high <= low) return 0f
    return ((value - low) / (high - low)).toFloat().coerceIn(0f, 1f)
}
