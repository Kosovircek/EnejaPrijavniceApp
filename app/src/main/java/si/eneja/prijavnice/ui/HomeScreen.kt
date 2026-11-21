package si.eneja.prijavnice.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import si.eneja.prijavnice.AppViewModel
import si.eneja.prijavnice.Screen

@Composable
fun HomeScreen(vm: AppViewModel) {
    val s by vm.session.collectAsState()
    val n = s.forms.size
    val counts = s.forms.groupingBy { it.program }.eachCount()

    Page("Prijavnice", onBack = null) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Skeniranih prijavnic: $n", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (counts.isNotEmpty()) {
                        Text("Programov: ${counts.size}", fontSize = 16.sp)
                        counts.entries.sortedByDescending { it.value }.forEach { (p, c) ->
                            Text("• $p: $c", fontSize = 14.sp, color = Color(0xFF444444))
                        }
                    }
                }
            }

            BigStep(
                "1. Slikanje prijavnic",
                "Slikaj eno za drugo. Vsako po slikanju položi z obrazom navzgor NA VRH kupa.",
            ) { vm.screen = Screen.Scan }

            BigStep(
                "2. Razdeli po programih",
                if (n == 0) "Najprej slikaj prijavnice." else
                    "Korak ${minOf(s.distStep, n)} / $n. Jemlji z vrha kupa, app pove, na kateri kupček.",
                enabled = n > 0,
            ) { vm.screen = Screen.Distribute }

            BigStep(
                "3. Razvrsti program po abecedi",
                "Izberi kupček programa in sledi navodilom.",
                enabled = n > 0,
            ) { vm.screen = Screen.SortPick }

            OutlinedButton(
                onClick = { vm.screen = Screen.Forms },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) { Text("Seznam prijavnic (popravki)") }
            OutlinedButton(
                onClick = { vm.screen = Screen.Settings },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Nastavitve in programi") }
        }
    }
}

@Composable
private fun BigStep(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .heightIn(min = 88.dp),
        colors = ButtonDefaults.buttonColors(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
