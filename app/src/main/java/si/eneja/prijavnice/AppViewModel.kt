package si.eneja.prijavnice

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import si.eneja.prijavnice.core.FormExtractor
import si.eneja.prijavnice.core.NameKeys
import si.eneja.prijavnice.core.OcrLine
import si.eneja.prijavnice.core.ProgramMatcher
import si.eneja.prijavnice.core.SlovenianCollator
import si.eneja.prijavnice.core.SortPlan
import si.eneja.prijavnice.core.SortPlanner

sealed interface Screen {
    data object Home : Screen
    data object Scan : Screen
    data object Forms : Screen
    data object Distribute : Screen
    data object SortPick : Screen
    data class Sort(val program: String) : Screen
    data object Settings : Screen
}

data class ScanResult(val program: String, val name: String, val raw: String, val error: String? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SessionStore(app)
    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<SessionData> = _session.asStateFlow()

    var screen by mutableStateOf<Screen>(Screen.Home)

    @Volatile
    var guideActive = false

    private val _nav = MutableSharedFlow<NavCmd>(extraBufferCapacity = 16)
    val nav: SharedFlow<NavCmd> = _nav.asSharedFlow()
    fun sendNav(cmd: NavCmd) {
        _nav.tryEmit(cmd)
    }

    val speaker = Speaker(app)
    val voice = VoiceCommands(app) { sendNav(it) }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val saveMutex = Mutex()

    fun update(f: (SessionData) -> SessionData) {
        _session.value = f(_session.value)
        viewModelScope.launch(Dispatchers.IO) {
            saveMutex.withLock { store.save(_session.value) }
        }
    }

    fun say(text: String) {
        if (_session.value.speakEnabled && text.isNotBlank()) speaker.speak(text)
    }

    // ---------- Slikanje ----------

    fun recognize(bitmap: Bitmap, rotation: Int, onDone: (ScanResult) -> Unit) {
        val input = InputImage.fromBitmap(bitmap, rotation)
        recognizer.process(input)
            .addOnSuccessListener { text ->
                val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { l ->
                    val b = l.boundingBox ?: return@mapNotNull null
                    OcrLine(l.text, b.left, b.top, b.right, b.bottom)
                }
                val ex = FormExtractor.extract(lines, _session.value.programs)
                onDone(ScanResult(ex.program, ex.name, text.text))
            }
            .addOnFailureListener { e ->
                onDone(ScanResult("", "", "", e.message ?: "Napaka pri branju"))
            }
    }

    /** Vrne obstoječ program, če je ime dovolj podobno, sicer očiščeno novo ime. */
    fun canonicalProgram(input: String): String {
        val t = input.trim().replace(Regex("\\s+"), " ")
        val known = _session.value.programs
        known.firstOrNull { it.equals(t, ignoreCase = true) }?.let { return it }
        return ProgramMatcher.match(t, known) ?: t
    }

    fun addForm(program: String, name: String, raw: String) {
        val p = canonicalProgram(program)
        update { s ->
            s.copy(
                forms = s.forms + Form(s.nextId, p, name.trim().replace(Regex("\\s+"), " "), raw),
                nextId = s.nextId + 1,
                programs = if (p in s.programs) s.programs else s.programs + p,
            )
        }
    }

    fun editForm(id: Int, program: String, name: String) {
        val p = canonicalProgram(program)
        update { s ->
            s.copy(
                forms = s.forms.map { if (it.id == id) it.copy(program = p, name = name.trim()) else it },
                programs = if (p in s.programs) s.programs else s.programs + p,
            )
        }
    }

    fun deleteForm(id: Int) = update { s -> s.copy(forms = s.forms.filterNot { it.id == id }) }

    fun renameProgram(old: String, new: String) {
        val n = new.trim()
        if (n.isEmpty()) return
        update { s ->
            val progs = s.programs.map { if (it == old) n else it }.distinct()
            s.copy(
                programs = progs,
                forms = s.forms.map { if (it.program == old) it.copy(program = n) else it },
                sortStep = s.sortStep.mapKeys { if (it.key == old) n else it.key },
            )
        }
    }

    fun addProgram(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        update { s -> if (s.programs.any { it.equals(n, true) }) s else s.copy(programs = s.programs + n) }
    }

    fun deleteProgram(name: String) = update { s -> s.copy(programs = s.programs - name) }

    fun resetSession() = update { s ->
        SessionData(
            programs = s.programs,
            sortBySurname = s.sortBySurname,
            speakEnabled = s.speakEnabled,
            speakNames = s.speakNames,
            listenEnabled = s.listenEnabled,
        )
    }

    // ---------- Razvrščanje ----------

    private data class PlanKey(val program: String, val ids: List<Int>, val keys: List<String>)

    private var planCache: Pair<PlanKey, SortPlan>? = null

    suspend fun planFor(program: String): SortPlan {
        val s = _session.value
        // Po razdelitvi je na kupčku programa od vrha navzdol vrstni red enak vrstnemu redu slikanja.
        val pile = s.forms.filter { it.program == program }
        val keys = pile.map { NameKeys.sortKey(it.name, s.sortBySurname) }
        val key = PlanKey(program, pile.map { it.id }, keys)
        planCache?.let { if (it.first == key) return it.second }
        val plan = withContext(Dispatchers.Default) {
            val keyOf = pile.associate { it.id to NameKeys.sortKey(it.name, s.sortBySurname) }
            val order = pile.withIndex().associate { it.value.id to it.index }
            val cmp = Comparator<Int> { a, b ->
                val c = SlovenianCollator.compare(keyOf.getValue(a), keyOf.getValue(b))
                if (c != 0) c else order.getValue(a).compareTo(order.getValue(b))
            }
            SortPlanner.plan(pile.map { it.id }, cmp)
        }
        planCache = key to plan
        return plan
    }

    override fun onCleared() {
        voice.stop()
        speaker.shutdown()
        recognizer.close()
        super.onCleared()
    }
}
