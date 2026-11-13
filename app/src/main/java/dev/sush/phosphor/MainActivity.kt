package dev.sush.phosphor

import android.animation.ValueAnimator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.sush.phosphor.platform.SettingsStore
import dev.sush.phosphor.ui.PhosphorApp
import dev.sush.phosphor.ui.theme.Motion

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setOnExitAnimationListener { screen ->
            val store = SettingsStore(this)
            val still = store.load().reducedMotion || store.loadPreview().reducedMotion
            if (!ValueAnimator.areAnimatorsEnabled() || still) {
                screen.remove()
            } else {
                // Android calls this when the first app frame is ready. Never hold startup
                // for the icon animation: fade straight into the already composed gallery.
                screen.view.animate()
                    .alpha(0f)
                    .setDuration(Motion.QUICK.toLong())
                    .withEndAction { screen.remove() }
                    .start()
            }
        }
        setContent { PhosphorApp() }
    }
}
