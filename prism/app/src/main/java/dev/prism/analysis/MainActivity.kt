package dev.prism.analysis

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import dev.prism.analysis.ui.PrismApp
import dev.prism.analysis.vm.PrismViewModel

/**
 * The only Activity in the app.
 *
 * There is no second screen to route to, so there is no navigation host, no
 * fragment manager and no intent handling beyond the launcher entry point and
 * the shared-text filter declared in the manifest. Everything past `setContent`
 * is Compose state.
 *
 * The ViewModel is resolved here rather than inside [PrismApp] so that the
 * Activity and the composition provably share one instance. If each obtained
 * its own, a shared-text intent would populate one while the UI observed
 * another — the kind of bug that is invisible until somebody actually uses the
 * share sheet.
 *
 * [installSplashScreen] must run before `super.onCreate`: that is the
 * documented contract, and calling it later means the system splash has
 * already been torn down and replaced by a blank window.
 */
class MainActivity : ComponentActivity() {

    private lateinit var vm: PrismViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        vm = ViewModelProvider(this)[PrismViewModel::class.java]

        /* Draw under the status and navigation bars; the shell applies the
           insets itself so content is padded rather than letterboxed. */
        enableEdgeToEdge()

        acceptSharedText(intent)

        setContent { PrismApp(vm) }
    }

    /** `singleTop` means a share from another app while PRISM is already
     *  foreground arrives here instead of starting a second instance. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptSharedText(intent)
    }

    /**
     * Load text handed over by another app's share sheet.
     *
     * Defensive on every field: the manifest declares `text/plain`, but an
     * action can be delivered without the extra, with a null type, or with a
     * type that does not match, and none of those should be able to crash the
     * launch. Anything unexpected is simply ignored and the app opens empty.
     */
    private fun acceptSharedText(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_SEND) return
        val type = intent.type ?: return
        if (type != "text/plain") return

        val shared = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        if (shared.isBlank()) return

        vm.onTextChange(shared)
        /* Collapse the editor: the document arrived already written, so the
           useful thing to show is the analysis of it. */
        vm.setEditorOpen(false)
        vm.analyzeNow()
    }
}
