package dev.offscript.social

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val systemMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        splash.setOnExitAnimationListener { provider ->
            if (!systemMotion) provider.remove() else provider.view.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f).setDuration(230).withEndAction { provider.remove() }.start()
        }
        setContent { OffscriptApp(viewModel(), systemMotion) }
    }
}
