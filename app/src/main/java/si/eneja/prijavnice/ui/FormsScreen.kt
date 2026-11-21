package si.eneja.prijavnice.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import si.eneja.prijavnice.AppViewModel
import si.eneja.prijavnice.Form
import si.eneja.prijavnice.Screen

@Composable
fun FormsScreen(vm: AppViewModel) {
    val s by vm.session.collectAsState()
    var editing by remember { mutableStateOf<Form?>(null) }

    Page("Prijavnice (${s.forms.size})", onBack = { vm.screen = Screen.Home }) { pad ->
        Column(Modifier.padding(pad)) {
            Hint(
                "  Vrstni red je vrstni red slikanja (#1 je na dnu kupa). Popravek imena je vedno varen; " +
                    "programa ali brisanja po razdelitvi raje ne spreminjaj.",
            )
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(s.forms, key = { _, f -> f.id }) { i, f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = f }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("#${i + 1}", modifier = Modifier.width(52.dp), color = Color.Gray)
                        Column(Modifier.weight(1f)) {
                            Text(f.name.ifBlank { "(brez imena)" }, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(f.program, fontSize = 14.sp, color = Color(0xFF444444))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    editing?.let { f ->
        EditFormDialog(vm, f, onDismiss = { editing = null })
    }
}

@Composable
private fun EditFormDialog(vm: AppViewModel, f: Form, onDismiss: () -> Unit) {
    val s by vm.session.collectAsState()
    var program by remember { mutableStateOf(f.program) }
    var name by remember { mutableStateOf(f.name) }
    var confirmDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uredi prijavnico") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Učenec") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(value = program, onValueChange = { program = it }, label = { Text("Program") }, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.programs.forEach { p -> FilterChip(selected = p == program, onClick = { program = p }, label = { Text(p) }) }
                }
                TextButton(onClick = { confirmDelete = true }) { Text("Izbriši prijavnico", color = Color(0xFFB00020)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.editForm(f.id, program, name); onDismiss() },
                enabled = name.isNotBlank() && program.isNotBlank(),
            ) { Text("Shrani") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Prekliči") } },
    )
    if (confirmDelete) {
        ConfirmDialog(
            "Izbriši?", "Prijavnica ${f.name} bo izbrisana s seznama.", "Izbriši",
            onConfirm = { vm.deleteForm(f.id); onDismiss() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.session.collectAsState()
    val ctx = LocalContext.current
    var renaming by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val counts = s.forms.groupingBy { it.program }.eachCount()

    Page("Nastavitve", onBack = { vm.screen = Screen.Home }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Toggle("Razvrsti po priimku (zadnja beseda imena)", s.sortBySurname) { v -> vm.update { it.copy(sortBySurname = v) } }
            Hint("Izklopljeno: po abecedi točno tako, kot je ime napisano (npr. »Ana Novak« pod A).")
            Toggle("Govorjena navodila", s.speakEnabled) { v -> vm.update { it.copy(speakEnabled = v) } }
            Toggle("Izgovori tudi ime učenca", s.speakNames) { v -> vm.update { it.copy(speakNames = v) } }
            when (vm.speaker.slovenianAvailable) {
                false -> {
                    Text(
                        "Slovenski glas ni nameščen. Namesti ga v nastavitvah »Pretvorba besedila v govor« (npr. Google ali eSpeak/RHVoice).",
                        color = Orange,
                    )
                    TextButton(onClick = {
                        try {
                            ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {
                        }
                    }) { Text("Odpri nastavitve govora") }
                }
                true -> Text("Slovenski glas: nameščen ✓", color = Green)
                null -> {}
            }
            TextButton(onClick = { vm.speaker.speak("Kozmetični tehnik. Kupček tri. Končni kup.") }) { Text("Preizkusi glas") }

            HorizontalDivider()
            Text("Programi", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Hint("Prebrano ime programa se samodejno poveže z najbolj podobnim s tega seznama.")
            s.programs.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("$p (${counts[p] ?: 0})", modifier = Modifier.weight(1f))
                    TextButton(onClick = { renaming = p }) { Text("Preimenuj") }
                    if ((counts[p] ?: 0) == 0) TextButton(onClick = { vm.deleteProgram(p) }) { Text("Briši") }
                }
            }
            TextButton(onClick = { adding = true }) { Text("+ Dodaj program") }

            HorizontalDivider()
            TextButton(onClick = { confirmReset = true }) {
                Text("Nova seja (izbriši vse prijavnice)", color = Color(0xFFB00020))
            }
        }
    }

    renaming?.let { old ->
        TextInputDialog("Preimenuj program", old, onDismiss = { renaming = null }) { vm.renameProgram(old, it) }
    }
    if (adding) TextInputDialog("Nov program", "", onDismiss = { adding = false }) { vm.addProgram(it) }
    if (confirmReset) ConfirmDialog(
        "Nova seja?",
        "Izbrišejo se vse skenirane prijavnice in napredek. Seznam programov ostane.",
        "Izbriši vse",
        onConfirm = { vm.resetSession() },
        onDismiss = { confirmReset = false },
    )
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun TextInputDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onOk(text); onDismiss() }, enabled = text.isNotBlank()) { Text("V redu") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Prekliči") } },
    )
}
