package com.mushaf.reader

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.mushaf.reader.reader.ReaderScreen
import com.mushaf.reader.reader.ReaderViewModel
import com.mushaf.reader.ui.theme.MushafTheme

class MainActivity : ComponentActivity() {

    private val vm: ReaderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the screen awake while the reader is in the foreground — it's a reading app,
        // so the device must not dim/lock and interrupt the user mid-read. The flag only
        // applies while this window is visible; the screen locks normally once the app leaves.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Let content draw into the short-edge cutout so its insets are reported; the
        // reader header then pads itself below the camera (see ReaderHeader).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()
        setContent {
            MushafTheme(darkTheme = vm.darkTheme) {
                ReaderScreen(viewModel = vm)
            }
        }
    }

    /** A foreground period starts a reading session. */
    override fun onStart() {
        super.onStart()
        vm.beginSession()
    }

    /** Leaving the foreground records the session (start/end time, pages read). */
    override fun onStop() {
        super.onStop()
        vm.commitSession()
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
