package com.mushaf.reader

import android.app.UiModeManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.mushaf.reader.data.ReadingStore
import com.mushaf.reader.reader.ReaderScreen
import com.mushaf.reader.reader.ReaderViewModel
import com.mushaf.reader.update.AppUpdateController
import com.mushaf.reader.update.AppUpdateUi
import com.mushaf.reader.ui.theme.MushafPalette
import com.mushaf.reader.ui.theme.MushafTheme
import com.mushaf.reader.ui.theme.paletteFor

class MainActivity : ComponentActivity() {

    private val vm: ReaderViewModel by viewModels()

    private val updates by lazy {
        AppUpdateController(this, ReadingStore(applicationContext), lifecycleScope)
    }
    /** Play hands back an IntentSender, so the consent sheet needs this contract, not a plain Intent. */
    private lateinit var updateFlow: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateFlow = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            updates.onUpdateFlowResult(it.resultCode)
        }
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            MushafTheme(paletteId = vm.themeId) {
                val palette = paletteFor(vm.themeId)
                LaunchedEffect(palette) { applyPalette(palette) }
                LaunchedEffect(vm.keepScreenOn) { applyKeepScreenOn(vm.keepScreenOn) }
                val updateUi = remember { AppUpdateUi(updates) { updates.startUpdate(updateFlow) } }
                ReaderScreen(viewModel = vm, updates = updateUi)
            }
        }
    }

    /**
     * Carries the reading theme out to the two surfaces Compose does not own.
     *
     * The launch window is drawn by the system before any of this code runs, so its color can only
     * come from resources: [UiModeManager.setApplicationNightMode] is what makes the system resolve
     * values-night for this app, and it takes effect from the next cold start. The activity window
     * behind the composition is ours, so it takes the palette's exact paper color right away — that
     * is what closes the gap between the launch window and the first drawn frame.
     */
    private fun applyPalette(palette: MushafPalette) {
        window.setBackgroundDrawable(palette.paper.toArgb().toDrawable())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
                if (palette.dark) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
            )
        }
    }

    /**
     * Holds the screen awake while the reader is in the foreground, when the reader asked for it.
     *
     * It's a reading app, so by default the device must not dim/lock and interrupt a long read —
     * but that costs battery, so it is a setting. The flag only applies while this window is
     * visible; the screen locks normally once the app leaves the foreground either way.
     */
    private fun applyKeepScreenOn(enabled: Boolean) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** A foreground period starts a reading session, and is when Play is asked about updates. */
    override fun onStart() {
        super.onStart()
        vm.beginSession()
        updates.onForeground()
    }

    /** Leaving the foreground records the session (start/end time, pages read). */
    override fun onStop() {
        super.onStop()
        vm.commitSession()
    }

    override fun onDestroy() {
        updates.dispose()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
