package dev.prism.analysis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

/* ═══════════════════════════════════════════════════════════════════════
   PRISM — theme

   The accent colour is not a constant and not a user setting. It is derived
   from the text currently being analysed: sentiment picks the hue, lexical
   richness picks the saturation, and difficulty picks how far the colour
   sits from white. Two different documents therefore look like two
   different instruments, which is the point.

   This is the same move Mochy makes when it themes itself from the album
   artwork — colour as a readout of the content, rather than decoration laid
   over it. The difference is that here the derivation is arithmetic anyone
   can check: hue is a linear map of valence from −1..+1 onto 232°..40°.
   ═══════════════════════════════════════════════════════════════════════ */

/** HSL → sRGB. Written out rather than pulled from a library, because
 *  androidx.compose.ui.graphics.Color has no HSL constructor. */
fun hsl(h: Double, s: Double, l: Double, a: Float = 1f): Color {
    val hh = ((h % 360.0) + 360.0) % 360.0
    val ss = s.coerceIn(0.0, 1.0)
    val ll = l.coerceIn(0.0, 1.0)
    val c = (1.0 - abs(2.0 * ll - 1.0)) * ss
    val x = c * (1.0 - abs((hh / 60.0) % 2.0 - 1.0))
    val m = ll - c / 2.0
    val (r1, g1, b1) = when {
        hh < 60.0 -> Triple(c, x, 0.0)
        hh < 120.0 -> Triple(x, c, 0.0)
        hh < 180.0 -> Triple(0.0, c, x)
        hh < 240.0 -> Triple(0.0, x, c)
        hh < 300.0 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    return Color(
        red = ((r1 + m) * 255).roundToInt().coerceIn(0, 255) / 255f,
        green = ((g1 + m) * 255).roundToInt().coerceIn(0, 255) / 255f,
        blue = ((b1 + m) * 255).roundToInt().coerceIn(0, 255) / 255f,
        alpha = a,
    )
}

/* ── the fixed shell ─────────────────────────────────────────────────── */
val InkBlack = Color(0xFF05080C)
val InkPanel = Color(0xFF0B1119)
val InkRaised = Color(0xFF121B26)
val InkLine = Color(0xFF1E2A38)
val InkTextHi = Color(0xFFF2F7FC)
val InkTextMid = Color(0xFFA9BACB)
val InkTextLow = Color(0xFF6B7D8F)
val PaperWhite = Color(0xFFFBFCFD)

/** The idle accent, used before there is any text to derive one from. */
val NeutralAccent = hsl(196.0, 0.72, 0.60)

/* ── the derived palette ─────────────────────────────────────────────── */

@Immutable
data class PrismPalette(
    val accent: Color,
    val accentDim: Color,
    val accentGlow: Color,
    val complement: Color,
    val background: Color,
    val panel: Color,
    val raised: Color,
    val line: Color,
    val textHigh: Color,
    val textMid: Color,
    val textLow: Color,
    /** 0..1, how difficult the text is; the UI drives its pulse rate from it */
    val difficulty: Float,
    /** −1..1, how warm the text is; drives the core's bias */
    val valence: Float,
)

/**
 * @param valence   −1..+1 sentiment
 * @param richness  0..1 lexical variety (Herdan's C)
 * @param difficulty 0..1 composite reading difficulty
 */
fun derivePalette(
    valence: Double,
    richness: Double,
    difficulty: Double,
    dark: Boolean = true,
): PrismPalette {
    val v = valence.coerceIn(-1.0, 1.0)
    val r = richness.coerceIn(0.0, 1.0)
    val d = difficulty.coerceIn(0.0, 1.0)

    /* Hue: cold blue-violet for negative, teal for neutral, warm amber for
       positive. Interpolating through the short way round the wheel keeps
       neutral text teal rather than passing through green. */
    val hue = if (v < 0.0) 196.0 + (-v) * 36.0 else 196.0 - v * 156.0

    /* Saturation rises with lexical variety: a repetitive text looks washed
       out, a varied one looks saturated. That is a real correspondence. */
    val sat = 0.42 + r * 0.44

    /* Lightness drops as difficulty rises, so dense text reads as a deeper,
       heavier instrument colour. */
    val light = if (dark) 0.66 - d * 0.14 else 0.42 - d * 0.10

    val accent = hsl(hue, sat, light)

    return if (dark) {
        PrismPalette(
            accent = accent,
            accentDim = hsl(hue, sat * 0.7, light * 0.55, 0.55f),
            accentGlow = hsl(hue, sat, (light + 0.18).coerceAtMost(0.92), 0.30f),
            complement = hsl(hue + 168.0, sat * 0.55, light * 0.9),
            background = InkBlack,
            panel = InkPanel,
            raised = InkRaised,
            line = InkLine,
            textHigh = InkTextHi,
            textMid = InkTextMid,
            textLow = InkTextLow,
            difficulty = d.toFloat(),
            valence = v.toFloat(),
        )
    } else {
        PrismPalette(
            accent = hsl(hue, sat, light.coerceAtMost(0.42)),
            accentDim = hsl(hue, sat * 0.6, 0.62, 0.35f),
            accentGlow = hsl(hue, sat, 0.70, 0.22f),
            complement = hsl(hue + 168.0, sat * 0.5, 0.44),
            background = PaperWhite,
            panel = Color(0xFFF1F4F7),
            raised = Color(0xFFE7ECF1),
            line = Color(0xFFD3DBE3),
            textHigh = Color(0xFF0C1219),
            textMid = Color(0xFF43525F),
            textLow = Color(0xFF778794),
            difficulty = d.toFloat(),
            valence = v.toFloat(),
        )
    }
}

val LocalPrism = staticCompositionLocalOf {
    derivePalette(0.0, 0.5, 0.25)
}

private val DarkScheme = darkColorScheme(
    primary = NeutralAccent,
    onPrimary = InkBlack,
    background = InkBlack,
    onBackground = InkTextHi,
    surface = InkPanel,
    onSurface = InkTextHi,
    surfaceVariant = InkRaised,
    onSurfaceVariant = InkTextMid,
    outline = InkLine,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0E6E86),
    onPrimary = PaperWhite,
    background = PaperWhite,
    onBackground = Color(0xFF0C1219),
    surface = Color(0xFFF1F4F7),
    onSurface = Color(0xFF0C1219),
    surfaceVariant = Color(0xFFE7ECF1),
    onSurfaceVariant = Color(0xFF43525F),
    outline = Color(0xFFD3DBE3),
)

/**
 * @param palette derived from the analysed text; defaults to the idle accent
 */
@Composable
fun PrismTheme(
    palette: PrismPalette = derivePalette(0.0, 0.5, 0.25),
    darkOverride: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark = darkOverride ?: isSystemInDarkTheme()
    /* MaterialTheme only supplies the defaults; the palette that actually
       drives the drawing is provided separately so nothing has to reach into
       the colour scheme to find the accent. */
    val scheme = if (dark) {
        DarkScheme.copy(primary = palette.accent, onPrimary = InkBlack)
    } else {
        LightScheme.copy(primary = palette.accent, onPrimary = PaperWhite)
    }
    CompositionLocalProvider(LocalPrism provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

object PrismTypography {
    /* Spacing and weight are the whole design system here: one family, four
       sizes, generous letter-spacing on the small caps labels. */
    const val LABEL_SPACING = 0.18f
    const val DISPLAY_TIGHT = -0.03f
}
