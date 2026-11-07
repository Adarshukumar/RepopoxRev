package dev.prism.analysis.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.prism.analysis.ui.component.AnalysisCore
import dev.prism.analysis.ui.component.BodyText
import dev.prism.analysis.ui.component.Editor
import dev.prism.analysis.ui.component.GlyphButton
import dev.prism.analysis.ui.component.Hairline
import dev.prism.analysis.ui.component.LabelText
import dev.prism.analysis.ui.component.LayerTabs
import dev.prism.analysis.ui.component.Shimmer
import dev.prism.analysis.ui.component.ValueText
import dev.prism.analysis.ui.component.count
import dev.prism.analysis.ui.component.gradeLabel
import dev.prism.analysis.ui.component.num
import dev.prism.analysis.ui.screen.FrequencyLayer
import dev.prism.analysis.ui.screen.LexiconLayer
import dev.prism.analysis.ui.screen.OverviewLayer
import dev.prism.analysis.ui.screen.ReadabilityLayer
import dev.prism.analysis.ui.screen.SentimentLayer
import dev.prism.analysis.ui.screen.StructureLayer
import dev.prism.analysis.ui.theme.LocalPrism
import dev.prism.analysis.ui.theme.PrismTheme
import dev.prism.analysis.vm.Layer
import dev.prism.analysis.vm.Phase
import dev.prism.analysis.vm.PrismViewModel

/* ═══════════════════════════════════════════════════════════════════════
   The app shell.

   One scrollable column, top to bottom: identity, the analysis core, a live
   readout strip, the editor, the layer switcher, and the selected layer.

   There is no navigation graph. Six layers over one document do not need
   routes, back-stack entries or arguments, and every route that does not
   exist is a route that cannot be mis-declared. State lives in the ViewModel;
   this file only decides what to draw for the state it is given.
   ═══════════════════════════════════════════════════════════════════════ */

@Composable
fun PrismApp(vm: PrismViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val systemDark = isSystemInDarkTheme()

    /* Push the platform's current preference into the ViewModel so it can
       derive palettes from a coroutine without reading a Compose local. */
    LaunchedEffect(systemDark) { vm.setSystemDark(systemDark) }

    PrismTheme(palette = state.palette, darkOverride = state.isDark) {
        val palette = LocalPrism.current
        val report = state.report
        val ready = state.phase == Phase.READY && !report.isEmpty

        Box(
            Modifier
                .fillMaxSize()
                .background(palette.background)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
            ) {
                /* ── identity ─────────────────────────────────────────── */
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    /* The mark is drawn, not shipped as an asset: a small
                       rotated square over a circle, the same form the core
                       resolves into. */
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(palette.accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(palette.accent)
                        )
                    }
                    Spacer(Modifier.size(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "PRISM",
                            color = palette.textHigh,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 5.2.sp,
                        )
                        Spacer(Modifier.height(1.dp))
                        LabelText("DEEP TEXT ANALYSIS", palette)
                    }
                    GlyphButton(
                        glyph = if (state.isDark) "☀" else "☾",
                        contentDescription = "Switch colour scheme",
                        onClick = { vm.toggleDark() },
                        palette = palette,
                        active = state.darkOverride != null,
                    )
                    Spacer(Modifier.size(8.dp))
                    GlyphButton(
                        glyph = "✎",
                        contentDescription = if (state.editorOpen) "Hide editor" else "Show editor",
                        onClick = { vm.setEditorOpen(!state.editorOpen) },
                        palette = palette,
                        active = state.editorOpen,
                    )
                }

                /* ── working indicator ────────────────────────────────── */
                AnimatedVisibility(
                    visible = state.phase == Phase.WORKING,
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(240)),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                        Shimmer(palette)
                        Spacer(Modifier.height(8.dp))
                    }
                }

                /* ── error ────────────────────────────────────────────── */
                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    state.error?.let { err ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.complement.copy(alpha = 0.14f))
                                .padding(13.dp)
                        ) {
                            Column {
                                LabelText("ANALYSIS INCOMPLETE", palette, emphasized = true)
                                Spacer(Modifier.height(4.dp))
                                BodyText(err.message, palette, size = 11.5.toInt())
                            }
                        }
                    }
                }

                /* ── the core ─────────────────────────────────────────── */
                AnalysisCore(
                    report = report,
                    palette = palette,
                    live = ready,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(268.dp)
                        .padding(horizontal = 24.dp),
                )

                /* What the core is actually showing. Stating it turns an
                   abstract animation into a labelled instrument. */
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (ready) {
                            "THE EMOTIONAL ARC, UNROLLED INTO A CIRCLE"
                        } else {
                            "AWAITING TEXT"
                        },
                        color = if (ready) palette.textMid else palette.textLow,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.9.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    if (ready) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "rotation ∝ rhythm · pulse ∝ difficulty · hue ∝ valence",
                            color = palette.textLow,
                            fontSize = 8.sp,
                            letterSpacing = 0.5.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                /* ── live readout strip ───────────────────────────────── */
                ReadoutStrip(state, palette)

                Spacer(Modifier.height(18.dp))

                /* ── editor ───────────────────────────────────────────── */
                AnimatedVisibility(
                    visible = state.editorOpen,
                    enter = fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                        expandVertically(tween(300, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(180)) + shrinkVertically(tween(240)),
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Editor(
                            text = state.text,
                            onTextChange = vm::onTextChange,
                            onAnalyze = vm::analyzeNow,
                            onClear = { vm.onTextChange("") },
                            onLoadSample = vm::loadSample,
                            activeSample = state.activeSample,
                            palette = palette,
                        )
                        Spacer(Modifier.height(18.dp))
                    }
                }

                /* ── layers ───────────────────────────────────────────── */
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    LayerTabs(
                        selected = state.layer,
                        onSelect = vm::setLayer,
                        palette = palette,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.layer.blurb,
                        color = palette.textLow,
                        fontSize = 10.sp,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp, bottom = 12.dp),
                    )
                }

                /* Crossfade between layers rather than a hard swap. The
                   target is the enum, so Compose disposes the outgoing layer
                   once the fade completes and only one is ever mounted. */
                Crossfade(
                    targetState = state.layer,
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) { layer ->
                    if (!ready) {
                        EmptyLayer(layer, palette)
                    } else {
                        when (layer) {
                            Layer.OVERVIEW -> OverviewLayer(report, palette)
                            Layer.READABILITY -> ReadabilityLayer(report, palette)
                            Layer.LEXICON -> LexiconLayer(report, palette)
                            Layer.STRUCTURE -> StructureLayer(report, palette)
                            Layer.SENTIMENT -> SentimentLayer(report, palette)
                            Layer.FREQUENCY -> FrequencyLayer(report, palette)
                        }
                    }
                }

                Spacer(Modifier.height(26.dp))
                Hairline(palette, Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(14.dp))

                /* ── footer ───────────────────────────────────────────── */
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "OFFLINE BY CONSTRUCTION",
                        color = palette.textLow,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.0.sp,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = "This app declares no permissions. It has no network access to " +
                            "send your text with, so analysis on this device is a property of " +
                            "the build rather than a promise in a privacy policy.",
                        color = palette.textLow,
                        fontSize = 9.5.sp,
                        lineHeight = 15.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                }

                Spacer(Modifier.navigationBarsPadding().height(10.dp))
            }
        }
    }
}

/* ── the readout strip ────────────────────────────────────────────────── */

/**
 * Four figures that stay on screen whichever layer is selected, so switching
 * tabs never loses sight of the document's headline numbers.
 */
@Composable
private fun ReadoutStrip(state: dev.prism.analysis.vm.PrismUiState, palette: dev.prism.analysis.ui.theme.PrismPalette) {
    val report = state.report
    val ready = state.phase == Phase.READY && !report.isEmpty
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.panel)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Readout(
            value = if (ready) count(report.surface.words) else "—",
            label = "WORDS",
            palette = palette,
            ready = ready,
        )
        Divider(palette)
        Readout(
            value = if (ready) gradeLabel(report.readability.fleschKincaidGrade) else "—",
            label = "LEVEL",
            palette = palette,
            ready = ready,
        )
        Divider(palette)
        Readout(
            value = if (ready) num(report.sentiment.valence, 2, signed = true) else "—",
            label = "TONE",
            palette = palette,
            ready = ready,
        )
        Divider(palette)
        Readout(
            value = if (ready) num(report.lexicon.richness01 * 100.0, 0) + "%" else "—",
            label = "RICHNESS",
            palette = palette,
            ready = ready,
        )
    }
    /* The strip also carries the cost of the last analysis. Showing it is
       honest about the fact that real work happened, and it is a useful
       signal when a document is large enough to take measurable time. */
    if (state.analyses > 0) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${count(state.analyses)} analysed this session · last took ${state.elapsedMs} ms",
            color = palette.textLow,
            fontSize = 8.5.sp,
            letterSpacing = 0.6.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun Readout(
    value: String,
    label: String,
    palette: dev.prism.analysis.ui.theme.PrismPalette,
    ready: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ValueText(
            value,
            palette,
            size = 15,
            color = if (ready) palette.accent else palette.textLow,
        )
        Spacer(Modifier.height(3.dp))
        LabelText(label, palette)
    }
}

@Composable
private fun Divider(palette: dev.prism.analysis.ui.theme.PrismPalette) {
    Box(
        Modifier
            .width(1.dp)
            .height(30.dp)
            .background(palette.line)
    )
}

/* ── the not-ready state ──────────────────────────────────────────────── */

/**
 * Shown in place of a layer when there is no report to draw.
 *
 * It names the layer that would appear, so the switcher is never a row of
 * dead controls, and it says what is missing rather than showing zeros —
 * zeros would be a claim about the text, and there is no text.
 */
@Composable
private fun EmptyLayer(layer: Layer, palette: dev.prism.analysis.ui.theme.PrismPalette) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.panel)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LabelText(layer.label.uppercase(), palette, emphasized = true)
        Spacer(Modifier.height(9.dp))
        Text(
            text = when (layer) {
                Layer.OVERVIEW -> "Load a sample or type a document to see the whole reading."
                Layer.READABILITY -> "Six published formulas will be scored against their ranges."
                Layer.LEXICON -> "Vocabulary richness needs at least a few sentences to mean anything."
                Layer.STRUCTURE -> "Sentence-length distribution needs two or more sentences."
                Layer.SENTIMENT -> "Tone and the emotional arc appear once there is text."
                Layer.FREQUENCY -> "Word ranks and Zipf deviation appear once there is text."
            },
            color = palette.textMid,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        BodyText(
            "Nothing is estimated in the meantime. An absent measurement and a measurement of " +
                "zero are different facts, and this app only ever shows the second one when it " +
                "has actually computed it.",
            palette,
            size = 10.5.toInt(),
        )
    }
}
