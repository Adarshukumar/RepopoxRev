package dev.prism.analysis.vm

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.prism.analysis.engine.PrismReport
import dev.prism.analysis.engine.TextAnalyzer
import dev.prism.analysis.ui.theme.PrismPalette
import dev.prism.analysis.ui.theme.derivePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ═══════════════════════════════════════════════════════════════════════
   PRISM — view model

   Holds one piece of state: the document, the report derived from it, and
   the palette derived from the report. Everything the UI draws is a pure
   function of that, so there is nothing for the composables to keep in sync
   with each other and no way for a screen to show a number the engine did
   not produce.

   Two deliberate choices:

   • Analysis is debounced, not continuous. Recomputing on every keystroke
     would rebuild the whole report — and therefore re-animate every chart —
     while the user is still typing. A 320 ms settle makes the instrument
     respond to a pause rather than to a key.

   • It runs on Dispatchers.Default. The engine is pure Kotlin with no
     Android dependency, so it is safe off the main thread, and a long
     document genuinely takes long enough to be worth moving.
   ═══════════════════════════════════════════════════════════════════════ */

enum class Phase { IDLE, WORKING, READY, FAILED }

enum class Layer(val label: String, val short: String, val blurb: String) {
    OVERVIEW("Overview", "OVER", "the whole reading at once"),
    READABILITY("Readability", "READ", "six published difficulty formulas"),
    LEXICON("Lexicon", "LEX", "vocabulary richness statistics"),
    STRUCTURE("Structure", "STR", "how sentence length is distributed"),
    SENTIMENT("Sentiment", "SENT", "tone and how it moves"),
    FREQUENCY("Frequency", "FREQ", "word rank against Zipf's law"),
}

/** Canonical texts, so the instrument has something worth reading before the
 *  user has typed anything. Each one is chosen to move the metrics a long
 *  way — that is what makes the derived palette legible. */
data class Sample(val name: String, val text: String)

val SAMPLES: List<Sample> = listOf(
    Sample(
        name = "Plain",
        text = """The cat sat on the mat. The dog ran in the park. It was a good day.
The sun was warm. We ate lunch at noon. Then we went home.""",
    ),
    Sample(
        name = "Terse",
        text = """It rained all week. The road went soft under the cart. He said nothing and
kept walking. The trees were cut low and the stumps blackened with wet. By
Thursday the river had risen two feet. Nobody spoke about the bridge.""",
    ),
    Sample(
        name = "Academic",
        text = """The methodological apparatus presupposed by contemporary psycholinguistic
inquiry necessitates a reconceptualisation of the relationship between
morphosyntactic complexity and reader comprehension latency. Empirical
investigations conducted across heterogeneous participant cohorts
demonstrate that polysyllabic density correlates, albeit imperfectly, with
measured processing difficulty, notwithstanding the confounding influence of
prior domain familiarity and the interpretive latitude afforded by contextual
disambiguation.""",
    ),
    Sample(
        name = "Legal",
        text = """The party of the first part hereby covenants and agrees to indemnify, defend
and hold harmless the party of the second part from and against any and all
claims, liabilities, damages, losses, costs and expenses whatsoever arising
out of or in connection with the performance or non-performance of the
obligations set forth herein, provided that written notice of such claim
shall have been delivered within thirty days of the event giving rise
thereto.""",
    ),
    Sample(
        name = "Warm",
        text = """Joy and hope returned with the morning. The children laughed in the garden
and the bread came out golden and good. We are grateful, we are glad, and
the light is lovely on the wall. Love is a bright and simple thing.""",
    ),
    Sample(
        name = "Cold",
        text = """Grief and sorrow followed the loss. The house was empty, silent, and dark.
Nothing was happy here any more. Bitter memory and sad regret filled every
room, and the winter wind was cruel against the broken window.""",
    ),
)

private const val DEBOUNCE_MS = 320L

/** A failure carries its own message; the engine only ever throws on inputs
 *  that should have been rejected upstream, so this exists to prove the UI
 *  degrades rather than crashes. */
data class PrismError(val message: String)

@Immutable
data class PrismUiState(
    val text: String = "",
    val phase: Phase = Phase.IDLE,
    val report: PrismReport = PrismReport.EMPTY,
    val palette: PrismPalette = derivePalette(0.0, 0.5, 0.25),
    val layer: Layer = Layer.OVERVIEW,
    val editorOpen: Boolean = true,
    val error: PrismError? = null,
    /** how long the last analysis took, in milliseconds */
    val elapsedMs: Long = 0,
    /** how many documents have been analysed this session */
    val analyses: Int = 0,
    /** which sample is loaded, or null for user-entered text */
    val activeSample: String? = null,
    /** null follows the system; set explicitly by the theme toggle */
    val darkOverride: Boolean? = null,
    /** what the platform currently reports, pushed in by the shell so the
     *  ViewModel never has to read a Compose local from a coroutine */
    val systemDark: Boolean = true,
) {
    /** Whether the shell should draw dark. Follows the system until the user
     *  says otherwise, so the derived palette and the Material scheme agree. */
    val isDark: Boolean get() = darkOverride ?: systemDark
}

class PrismViewModel : ViewModel() {

    private val _state = MutableStateFlow(PrismUiState())
    val state: StateFlow<PrismUiState> = _state.asStateFlow()

    private var pending: Job? = null

    /** Text changed. Restart the debounce and re-derive everything. */
    fun onTextChange(text: String) {
        _state.update { it.copy(text = text, activeSample = null) }
        scheduleAnalysis()
    }

    /** Force an immediate run — used by the sample chips and the analyse
     *  button, where a 320 ms wait would feel like the tap did nothing. */
    fun analyzeNow() {
        pending?.cancel()
        runAnalysis()
    }

    fun loadSample(sample: Sample) {
        _state.update {
            it.copy(text = sample.text, activeSample = sample.name, editorOpen = false)
        }
        pending?.cancel()
        runAnalysis()
    }

    fun setLayer(layer: Layer) = _state.update { it.copy(layer = layer) }

    fun setEditorOpen(open: Boolean) = _state.update { it.copy(editorOpen = open) }

    fun clearError() = _state.update { it.copy(error = null) }

    /** Flip the shell between dark and light, resolving "follow system" into
     *  an explicit choice the first time it is used. */
    /** Keep the recorded system preference current. Cheap and idempotent, so
     *  it is safe to call on every recomposition of the shell. */
    fun setSystemDark(dark: Boolean) {
        if (_state.value.systemDark != dark) _state.update { it.copy(systemDark = dark) }
    }

    fun toggleDark() = _state.update {
        val nowDark = !(it.darkOverride ?: it.systemDark)
        val pal = derivePalette(
            valence = it.report.sentiment.valence,
            richness = it.report.lexicon.herdansC.coerceIn(0.0, 1.0),
            difficulty = it.report.readability.difficulty01,
            dark = nowDark,
        )
        it.copy(darkOverride = nowDark, palette = pal)
    }

    private fun scheduleAnalysis() {
        pending?.cancel()
        pending = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            if (isActive) runAnalysis()
        }
    }

    private fun runAnalysis() {
        val text = _state.value.text
        if (text.isBlank()) {
            _state.update {
                it.copy(
                    phase = Phase.IDLE,
                    report = PrismReport.EMPTY,
                    palette = derivePalette(0.0, 0.5, 0.25),
                    elapsedMs = 0,
                    error = null,
                )
            }
            return
        }
        _state.update { it.copy(phase = Phase.WORKING) }
        viewModelScope.launch {
            val started = System.nanoTime()
            val outcome = runCatching {
                withContext(Dispatchers.Default) { TextAnalyzer.analyze(text) }
            }
            val elapsed = (System.nanoTime() - started) / 1_000_000
            outcome.fold(
                onSuccess = { rep ->
                    /* The palette is a function of the report, so it is
                       derived here exactly once and every screen reads the
                       same colours. */
                    val palette = derivePalette(
                        valence = rep.sentiment.valence,
                        richness = rep.lexicon.herdansC.coerceIn(0.0, 1.0),
                        difficulty = rep.readability.difficulty01,
                        dark = _state.value.isDark,
                    )
                    _state.update {
                        it.copy(
                            phase = Phase.READY,
                            report = rep,
                            palette = palette,
                            elapsedMs = elapsed,
                            analyses = it.analyses + 1,
                            error = null,
                        )
                    }
                },
                onFailure = { t ->
                    _state.update {
                        it.copy(
                            phase = Phase.FAILED,
                            elapsedMs = elapsed,
                            error = PrismError(t.message ?: "analysis could not complete"),
                        )
                    }
                },
            )
        }
    }
}
