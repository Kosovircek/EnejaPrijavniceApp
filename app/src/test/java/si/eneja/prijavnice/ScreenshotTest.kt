package si.eneja.prijavnice

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.ViewModelProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import si.eneja.prijavnice.core.SortMove

/** Ustvari posnetke glavnih zaslonov v app/screenshots/ (ni preverjanje, samo prikaz). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val programs = listOf("Logistični tehnik", "Kozmetični tehnik", "Frizer", "Ekonomski tehnik")
    private val names = listOf(
        "Ema Zupančič", "Črt Jerman", "Jan Kos", "Lara Žagar", "Matic Božič", "Urška Dolenc", "Zala Vidmar",
        "Tim Novak", "Lana Korošec", "Jaka Hribar", "Špela Turk", "Nika Čeh", "Žiga Kralj", "Ana Babič",
        "Luka Mlakar", "Tjaša Golob", "Maja Pirc", "Žan Erjavec", "Eva Šuštar", "Nejc Bizjak", "Klara Rozman",
        "Ožbej Kovačič", "Neža Potočnik", "Aljaž Horvat", "Sara Čuk", "Rok Ambrožič", "Pia Žnidaršič", "Blaž Vidmar",
    )

    private fun demo(): SessionData {
        val forms = names.mapIndexed { i, n -> Form(i + 1, programs[(i * 7 + i / 3) % programs.size], n) }
        return SessionData(forms = forms, programs = programs, nextId = forms.size + 1, speakEnabled = false)
    }

    private fun shot(name: String, setup: (AppViewModel) -> Unit) {
        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        rule.runOnUiThread { setup(vm) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test
    fun zasloni() {
        val data = demo()
        shot("1_domov") { it.update { data }; it.screen = Screen.Home }
        shot("2_slikanje_pregled") {
            it.scanPreview = ScanResult("Kozmetični tehnik", "Sara Čuk", "")
            it.screen = Screen.Scan
        }
        shot("3_razdeljevanje") { vm -> vm.update { it.copy(distStep = 6) }; vm.screen = Screen.Distribute }
        shot("4_izbira_programa") { it.screen = Screen.SortPick }

        val vm = ViewModelProvider(rule.activity)[AppViewModel::class.java]
        val program = data.forms.groupingBy { it.program }.eachCount().maxBy { it.value }.key
        val plan = runBlocking { vm.planFor(program) }
        // korak, kjer sta na mizi vsaj dva pomožna kupčka in gre prijavnica na kupček
        val step = plan.moves.indices.firstOrNull { i ->
            plan.states[i].piles.size >= 2 && plan.moves[i] is SortMove.SourceToPile
        } ?: 0
        shot("5_abecedno_korak") { v -> v.update { it.copy(sortStep = mapOf(program to step)) }; v.screen = Screen.Sort(program) }
        val outStep = plan.moves.indices.firstOrNull { it > step && plan.moves[it] is SortMove.PileToOutput } ?: step
        shot("6_abecedno_na_koncni_kup") { v -> v.update { it.copy(sortStep = mapOf(program to outStep)) } }
        shot("7_abecedno_koncano") { v -> v.update { it.copy(sortStep = mapOf(program to plan.moves.size)) } }
        shot("8_seznam") { it.screen = Screen.Forms }
        shot("9_nastavitve") { it.screen = Screen.Settings }
    }
}
