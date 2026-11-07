package dev.prism.analysis.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.prism.analysis.ui.theme.PrismPalette
import dev.prism.analysis.vm.Layer
import dev.prism.analysis.vm.Sample
import dev.prism.analysis.vm.SAMPLES

/* ═══════════════════════════════════════════════════════════════════════
   Controls: the layer switcher, the buttons, and the text editor.

   These are built from Box and Canvas rather than Material components on
   purpose. The instrument look depends on exact stroke weights and corner
   radii that a themed Button would override, and the fewer Material
   signatures this app touches, the less of its appearance is decided by a
   library version it does not control.
   ═══════════════════════════════════════════════════════════════════════ */

private val TABS: List<Layer> = Layer.values().toList()

/**
 * A segmented switcher with a sliding indicator.
 *
 * All tabs are equal width, so the indicator position is a multiple of the
 * tab width and animating it is a single `animateDpAsState`. Measuring the
 * children would allow variable widths and add a layout pass this does not
 * need.
 */
@Composable
fun LayerTabs(
    selected: Layer,
    onSelect: (Layer) -> Unit,
    palette: PrismPalette,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.panel)
    ) {
        val tabWidth = maxWidth / TABS.size
        val index = TABS.indexOf(selected).coerceAtLeast(0)
        val offset by animateDpAsState(
            targetValue = tabWidth * index,
            animationSpec = tween(340, easing = FastOutSlowInEasing),
        )

        /* sliding indicator — offset is applied after the inset padding so it
           tracks the tab centres rather than their outer edges */
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = offset + 4.dp)
                .width(tabWidth - 8.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(palette.accent.copy(alpha = 0.16f))
                .border(1.dp, palette.accent.copy(alpha = 0.55f), RoundedCornerShape(9.dp))
        )

        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            TABS.forEach { layer ->
                val active = layer == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onSelect(layer) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = layer.short,
                        color = if (active) palette.accent else palette.textLow,
                        fontSize = 9.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        letterSpacing = 1.1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Visible,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/* ── buttons ──────────────────────────────────────────────────────────── */

@Composable
fun PrismButton(
    label: String,
    onClick: () -> Unit,
    palette: PrismPalette,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(10.dp)
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (primary) palette.accent.copy(alpha = 0.90f * alpha)
                else palette.raised.copy(alpha = alpha)
            )
            .border(
                1.dp,
                if (primary) palette.accent.copy(alpha = alpha) else palette.line.copy(alpha = alpha),
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) palette.background else palette.textMid,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            maxLines = 1,
        )
    }
}

/** A round icon-sized button. The glyph is a drawn shape rather than an icon
 *  font, so the app carries no icon dependency at all. */
@Composable
fun GlyphButton(
    glyph: String,
    contentDescription: String,
    onClick: () -> Unit,
    palette: PrismPalette,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (active) palette.accent.copy(alpha = 0.18f) else palette.raised)
            .border(
                1.dp,
                if (active) palette.accent.copy(alpha = 0.6f) else palette.line,
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = if (active) palette.accent else palette.textMid,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/* ── the editor ───────────────────────────────────────────────────────── */

private val EditorStyle = TextStyle(
    fontSize = 14.5.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 22.sp,
)

/**
 * The document input.
 *
 * A [BasicTextField] with a hand-drawn placeholder rather than a Material
 * TextField, because the Material one brings its own colours, shape and
 * indicator line that would fight the panel it sits on.
 */
@Composable
fun Editor(
    text: String,
    onTextChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onClear: () -> Unit,
    onLoadSample: (Sample) -> Unit,
    activeSample: String?,
    palette: PrismPalette,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.panel)
            .border(1.dp, palette.line, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabelText("SOURCE TEXT", palette, emphasized = true)
            LabelText(approxWords(text) + " WORDS", palette)
        }

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 132.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.background.copy(alpha = if (palette.background == palette.panel) 1f else 0.55f))
                .padding(12.dp)
        ) {
            if (text.isEmpty()) {
                Text(
                    text = "Paste or type any text. Everything is analysed on this device — " +
                        "nothing is sent anywhere, and the app holds no network permission to send it with.",
                    color = palette.textLow,
                    style = EditorStyle,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 108.dp),
                textStyle = EditorStyle.copy(color = palette.textHigh),
                cursorBrush = SolidColor(palette.accent),
                maxLines = 600,
            )
        }

        Spacer(Modifier.height(12.dp))
        LabelText("LOAD A SAMPLE", palette)
        Spacer(Modifier.height(7.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(SAMPLES, key = { it.name }) { sample ->
                val active = sample.name == activeSample
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (active) palette.accent.copy(alpha = 0.18f) else palette.raised)
                        .border(
                            1.dp,
                            if (active) palette.accent.copy(alpha = 0.6f) else palette.line,
                            RoundedCornerShape(20.dp),
                        )
                        .clickable { onLoadSample(sample) }
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = sample.name,
                        color = if (active) palette.accent else palette.textMid,
                        fontSize = 10.5.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(13.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            PrismButton(
                label = "ANALYSE",
                onClick = onAnalyze,
                palette = palette,
                primary = true,
                enabled = text.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
            PrismButton(
                label = "CLEAR",
                onClick = onClear,
                palette = palette,
                enabled = text.isNotEmpty(),
            )
        }
    }
}

/** A cheap live word count for the editor header. It is not the tokenizer's
 *  count and does not claim to be — the authoritative figures live in the
 *  report and only appear once an analysis has actually run. */
private fun approxWords(text: String): String {
    if (text.isBlank()) return "0"
    var n = 0
    var inWord = false
    for (ch in text) {
        val isWordChar = !ch.isWhitespace()
        if (isWordChar && !inWord) n++
        inWord = isWordChar
    }
    return count(n)
}
