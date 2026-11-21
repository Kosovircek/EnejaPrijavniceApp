package si.eneja.prijavnice

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import si.eneja.prijavnice.ui.AppRoot
import si.eneja.prijavnice.ui.AppTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            AppTheme {
                AppRoot(vm)
            }
        }
    }

    private fun cmdFor(keyCode: Int): NavCmd? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER -> NavCmd.NEXT

        KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_DPAD_LEFT -> NavCmd.BACK

        else -> null
    }

    // Tipka za glasnost gor = naprej, dol = nazaj (tudi gumbi na slušalkah / bluetooth daljincu)
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (vm.guideActive) {
            val cmd = cmdFor(keyCode)
            if (cmd != null) {
                if (event.repeatCount == 0) vm.sendNav(cmd)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (vm.guideActive && cmdFor(keyCode) != null) return true
        return super.onKeyUp(keyCode, event)
    }
}
