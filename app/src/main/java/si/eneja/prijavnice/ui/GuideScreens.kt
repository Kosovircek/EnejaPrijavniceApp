package si.eneja.prijavnice.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import si.eneja.prijavnice.AppViewModel
import si.eneja.prijavnice.NavCmd
import si.eneja.prijavnice.Screen
import si.eneja.prijavnice.core.Distribution
import si.eneja.prijavnice.core.SortMove
import si.eneja.prijavnice.core.SortPlan
import si.eneja.prijavnice.core.TableState

/** Splošen zaslon z navodili po korakih: naprej / nazaj z gumbi, tapom, tipkami za glasnost ali glasom. */
@Composable
fun Guide(
    vm: AppViewModel,
    title: String,
    total: Int,
    index: Int,
    onIndex: (Int) -> Unit,
    speech: (Int) -> String,
    onExit: () -> Unit,
    content: @Composable ColumnScope.(Int) -> Unit,
) {
    val s by vm.session.collectAsState()
    val ctx = LocalContext.current
    val curIndex by rememberUpdatedState(index)
    val curOnIndex by rememberUpdatedState(onIndex)
    val curSpeech by rememberUpdatedState(speech)
    val curTotal by rememberUpdatedState(total)

    DisposableEffect(Unit) {
        vm.guideActive = true
        onDispose {
            vm.guideActive = false
            vm.speaker.stop()
        }
    }
    LaunchedEffect(index, total) { vm.say(speech(index)) }
    LaunchedEffect(Unit) {
        vm.nav.collect { cmd ->
            when (cmd) {
                NavCmd.NEXT -> if (curIndex < curTotal) curOnIndex(curIndex + 1)
                NavCmd.BACK -> if (curIndex > 0) curOnIndex(curIndex - 1)
                NavCmd.REPEAT -> vm.say(curSpeech(curIndex))
            }
        }
    }

    var micPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micPerm = granted
        if (granted) vm.update { it.copy(listenEnabled = true) }
    }
    val listening = s.listenEnabled && micPerm
    DisposableEffect(listening) {
        if (listening) vm.voice.start()
        onDispose { vm.voice.stop() }
    }

    Page(
        title,
        onBack = onExit,
        actions = {
            TextButton(onClick = { vm.update { it.copy(speakEnabled = !it.speakEnabled) } }) {
                Text(if (s.speakEnabled) "🔊" else "🔇", fontSize = 20.sp)
            }
            TextButton(onClick = {
                if (!micPerm) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                else vm.update { it.copy(listenEnabled = !it.listenEnabled) }
            }) {
                Text(if (listening) "🎤 vklj." else "🎤 izklj.", color = Color.White)
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad),
        ) {
            LinearProgressIndicator(
                progress = { if (total == 0) 1f else index.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (index >= total) "Končano ($total korakov)" else "Korak ${index + 1} / $total",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = Color.Gray,
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(enabled = index < total) { onIndex(index + 1) }
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content(index)
            }
            if (listening && vm.voice.status.isNotEmpty()) {
                Text(
                    vm.voice.status,
                    fontSize = 13.sp,
                    color = Blue,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onIndex(index - 1) },
                    enabled = index > 0,
                    modifier = Modifier
                        .weight(1f)
                        .height(76.dp),
                ) { Text("◀ Nazaj", fontSize = 18.sp) }
                OutlinedButton(
                    onClick = { vm.say(speech(index)) },
                    modifier = Modifier
                        .weight(0.8f)
                        .height(76.dp),
                ) { Text("Ponovi", fontSize = 15.sp) }
                Button(
                    onClick = { onIndex(index + 1) },
                    enabled = index < total,
                    modifier = Modifier
                        .weight(1.6f)
                        .height(76.dp),
                ) { Text("Naprej ▶", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            }
            Text(
                "Tapni na sredino ali pritisni tipko za glasnost gor = naprej, dol = nazaj.",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }
    }
}

// ---------------- 2. Razdeljevanje po programih ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DistributeScreen(vm: AppViewModel) {
    val s by vm.session.collectAsState()
    val programs = s.forms.map { it.program }
    val steps = remember(programs) { Distribution.steps(programs) }
    val idx = s.distStep.coerceIn(0, steps.size)

    Guide(
        vm = vm,
        title = "Razdeljevanje",
        total = steps.size,
        index = idx,
        onIndex = { i -> vm.update { it.copy(distStep = i.coerceIn(0, steps.size)) } },
        speech = { i ->
            if (i >= steps.size) "Končano. Vse prijavnice so razdeljene."
            else {
                val st = steps[i]
                buildString {
                    if (s.speakNames) append(s.forms[st.formIndex].name).append(". ")
                    if (st.firstOfProgram) append("Nov kupček. ")
                    append(st.program)
                }
            }
        },
        onExit = { vm.screen = Screen.Home },
    ) { i ->
        if (i >= steps.size) {
            Text("✓ Razdeljeno!", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Green)
            val firsts = steps.filter { it.firstOfProgram }
            val counts = programs.groupingBy { it }.eachCount()
            firsts.forEach { st ->
                Text("Kupček ${st.pileNumber}: ${st.program} (${counts[st.program]})", fontSize = 18.sp)
            }
            Hint("Kupčkov ne premešaj – aplikacija ve, v kakšnem vrstnem redu je vsak.")
            Button(onClick = { vm.screen = Screen.SortPick }) { Text("3. Razvrsti po abecedi") }
        } else {
            val st = steps[i]
            val f = s.forms[st.formIndex]
            if (i == 0) {
                Hint("Kup položi pred sebe z obrazom navzgor. Na vrhu mora biti zadnja slikana prijavnica.")
            }
            Text("Vrhnja prijavnica:", fontSize = 16.sp, color = Color.Gray)
            Text(f.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("položi na kupček", fontSize = 16.sp, color = Color.Gray)
            if (st.firstOfProgram) {
                Text(
                    "NOV KUPČEK",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Orange, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Text("${st.pileNumber}", fontSize = 96.sp, fontWeight = FontWeight.Black, color = Blue)
            Text(st.program, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 38.sp)
            val seen = steps.subList(0, i + 1).filter { it.firstOfProgram }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                seen.forEach { p ->
                    Chip("${p.pileNumber}: ${p.program}", highlight = p.program == st.program)
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, highlight: Boolean, color: Color = Blue) {
    Text(
        text,
        fontSize = 13.sp,
        color = if (highlight) Color.White else Color.Black,
        modifier = Modifier
            .background(if (highlight) color else Color(0xFFEDEDED), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

// ---------------- 3. Razvrščanje po abecedi ----------------

@Composable
fun SortPickScreen(vm: AppViewModel) {
    val s by vm.session.collectAsState()
    val counts = s.forms.groupingBy { it.program }.eachCount()
    val order = Distribution.steps(s.forms.map { it.program }).filter { it.firstOfProgram }

    Page("Razvrsti po abecedi", onBack = { vm.screen = Screen.Home }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Hint(
                "Izberi kupček programa. Kupček mora biti tak, kot je nastal pri razdeljevanju (z obrazom navzgor, nič premešan). " +
                    "Na koncu bo urejen od A (zgoraj) do Ž (spodaj)" +
                    (if (s.sortBySurname) ", po priimku." else ", po imenu, kot je napisano."),
            )
            order.forEach { st ->
                val p = st.program
                val step = s.sortStep[p] ?: 0
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.screen = Screen.Sort(p) },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Kupček ${st.pileNumber}: $p", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${counts[p] ?: 0} prijavnic" + if (step > 0) " · napredek: korak $step" else "",
                            color = Color.Gray,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortScreen(vm: AppViewModel, program: String) {
    val s by vm.session.collectAsState()
    val plan by produceState<SortPlan?>(null, s.forms, s.sortBySurname, program) {
        value = vm.planFor(program)
    }
    val p = plan
    if (p == null) {
        Page(program, onBack = { vm.screen = Screen.SortPick }) { pad ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Računam najboljši vrstni red …")
                }
            }
        }
        return
    }
    val byId = remember(s.forms) { s.forms.associateBy { it.id } }
    fun name(id: Int) = byId[id]?.name ?: "?"
    val total = p.moves.size
    val idx = (s.sortStep[program] ?: 0).coerceIn(0, total)

    Guide(
        vm = vm,
        title = program,
        total = total,
        index = idx,
        onIndex = { i -> vm.update { it.copy(sortStep = it.sortStep + (program to i.coerceIn(0, total))) } },
        speech = { i ->
            if (i >= total) "Končano. Kupček je urejen po abecedi."
            else {
                val m = p.moves[i]
                val who = when (m) {
                    is SortMove.SourceToPile -> m.card
                    is SortMove.SourceToOutput -> m.card
                    is SortMove.PileToOutput -> m.card
                    is SortMove.StackPile -> null
                }
                val prefix = if (s.speakNames && who != null) name(who) + ". " else ""
                prefix + when (m) {
                    is SortMove.SourceToPile -> if (m.newPile) "Nov kupček ${m.slot}" else "Kupček ${m.slot}"
                    is SortMove.SourceToOutput -> "Končni kup"
                    is SortMove.PileToOutput -> "S kupčka ${m.slot} na končni kup"
                    is SortMove.StackPile -> "Cel kupček ${m.fromSlot} položi na kupček ${m.ontoSlot}"
                }
            }
        },
        onExit = { vm.screen = Screen.SortPick },
    ) { i ->
        if (i >= total) {
            Text("✓ Urejeno!", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Green)
            val out = p.states.last().output
            if (out.isNotEmpty()) {
                Text("Končni kup: ${out.size} prijavnic, z obrazom navzgor.", fontSize = 18.sp)
                Text("Na vrhu: ${name(out.first())}", fontSize = 18.sp)
                Text("Na dnu: ${name(out.last())}", fontSize = 18.sp)
            }
            Button(onClick = { vm.screen = Screen.SortPick }) { Text("Naslednji program") }
        } else {
            if (i == 0) {
                val src = p.states[0].source
                Hint(
                    "Kupček »$program« položi pred sebe z obrazom navzgor. Na vrhu mora biti ${name(src.first())}. " +
                        "Pripravi prostor za končni kup in največ ${p.peakPiles} pomožnih kupčkov (mesta 1, 2, 3 …).",
                )
            }
            val m = p.moves[i]
            when (m) {
                is SortMove.SourceToPile -> {
                    Text("Z glavnega kupa vzemi vrhnjo:", color = Color.Gray)
                    Text(name(m.card), fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(if (m.newPile) "začni NOV kupček na mestu" else "položi na kupček", color = Color.Gray)
                    Text("${m.slot}", fontSize = 96.sp, fontWeight = FontWeight.Black, color = if (m.newPile) Orange else Blue)
                }
                is SortMove.SourceToOutput -> {
                    Text("Z glavnega kupa vzemi vrhnjo:", color = Color.Gray)
                    Text(name(m.card), fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("položi na", color = Color.Gray)
                    Text("KONČNI KUP", fontSize = 44.sp, fontWeight = FontWeight.Black, color = Green)
                }
                is SortMove.PileToOutput -> {
                    Text("S kupčka ${m.slot} vzemi vrhnjo:", color = Color.Gray, fontSize = 18.sp)
                    Text(name(m.card), fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("položi na", color = Color.Gray)
                    Text("KONČNI KUP", fontSize = 44.sp, fontWeight = FontWeight.Black, color = Green)
                }
                is SortMove.StackPile -> {
                    Text("Vzemi CEL kupček ${m.fromSlot}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("(${m.count} prijavnic, ne obračaj ga)", color = Color.Gray)
                    Text("in ga položi na kupček", color = Color.Gray)
                    Text("${m.ontoSlot}", fontSize = 96.sp, fontWeight = FontWeight.Black, color = Blue)
                    Text("Zgoraj: ${name(m.topCard)} · spodaj: ${name(m.bottomCard)}", fontSize = 14.sp, color = Color.Gray)
                }
            }
            TableView(p.states[i], m, ::name)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableView(st: TableState, move: SortMove, name: (Int) -> String) {
    val fromSlot = when (move) {
        is SortMove.PileToOutput -> move.slot
        is SortMove.StackPile -> move.fromSlot
        else -> null
    }
    val toSlot = when (move) {
        is SortMove.SourceToPile -> move.slot
        is SortMove.StackPile -> move.ontoSlot
        else -> null
    }
    val fromSource = move is SortMove.SourceToPile || move is SortMove.SourceToOutput
    val toOutput = move is SortMove.SourceToOutput || move is SortMove.PileToOutput

    Text("Na mizi (vrhnja prijavnica):", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 12.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PileBox("Glavni", st.source.firstOrNull()?.let(name), st.source.size, fromSource, Orange)
        val maxSlot = maxOf(st.piles.keys.maxOrNull() ?: 0, toSlot ?: 0)
        for (k in 1..maxSlot) {
            val cards = st.piles[k].orEmpty()
            PileBox(
                "$k",
                cards.firstOrNull()?.let(name),
                cards.size,
                highlight = k == fromSlot || k == toSlot,
                color = if (k == fromSlot) Orange else Blue,
            )
        }
        PileBox("Končni", st.output.firstOrNull()?.let(name), st.output.size, toOutput, Green)
    }
}

@Composable
private fun PileBox(label: String, top: String?, count: Int, highlight: Boolean, color: Color) {
    Column(
        Modifier
            .widthIn(min = 64.dp, max = 120.dp)
            .border(if (highlight) 3.dp else 1.dp, if (highlight) color else Color.LightGray, RoundedCornerShape(6.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (highlight) color else Color.Black)
        Text(top ?: "—", fontSize = 11.sp, maxLines = 2, textAlign = TextAlign.Center)
        Text("($count)", fontSize = 11.sp, color = Color.Gray)
    }
}
