package si.eneja.prijavnice.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import si.eneja.prijavnice.AppViewModel
import si.eneja.prijavnice.ScanResult
import si.eneja.prijavnice.Screen

@Composable
fun ScanScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val s by vm.session.collectAsState()
    var hasPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPerm = it }
    LaunchedEffect(Unit) { if (!hasPerm) launcher.launch(Manifest.permission.CAMERA) }

    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmUndo by remember { mutableStateOf(false) }

    Page(
        "Slikanje (${s.forms.size})",
        onBack = { vm.screen = Screen.Home },
        actions = {
            if (s.forms.isNotEmpty()) TextButton(onClick = { confirmUndo = true }) {
                Text("Razveljavi zadnjo", color = Color.White)
            }
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding(),
        ) {
            val last = s.forms.lastOrNull()
            Text(
                if (last == null) "Slikaj prvo prijavnico. Po slikanju jo položi z obrazom navzgor na kup."
                else "Zadnja (#${s.forms.size}): ${last.name} – ${last.program}",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE8F0FB))
                    .padding(8.dp),
                fontSize = 15.sp,
            )
            val r = result
            if (r == null) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    if (hasPerm) {
                        CameraView(
                            busy = busy,
                            onBusy = { busy = it; if (it) error = null },
                            onCaptured = { bmp, rot ->
                                vm.recognize(bmp, rot) { res ->
                                    busy = false
                                    if (res.error != null) error = res.error else result = res
                                }
                            },
                            onError = { busy = false; error = it },
                        )
                    } else {
                        Column(Modifier.align(Alignment.Center).padding(24.dp)) {
                            Text("Aplikacija potrebuje dovoljenje za kamero.")
                            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Dovoli kamero") }
                        }
                    }
                    error?.let {
                        Text(
                            "Napaka: $it",
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .background(Color(0xCCB00020))
                                .padding(8.dp),
                        )
                    }
                }
            } else {
                ReviewPanel(
                    vm = vm,
                    initial = r,
                    onSave = { prog, name ->
                        vm.addForm(prog, name, r.raw)
                        result = null
                    },
                    onRetake = { result = null },
                )
            }
        }
    }

    if (confirmUndo) {
        val last = s.forms.lastOrNull()
        if (last != null) ConfirmDialog(
            "Razveljavi zadnjo?",
            "Izbriše prijavnico #${s.forms.size}: ${last.name} (${last.program}). Odstrani jo tudi z vrha kupa.",
            "Izbriši",
            onConfirm = { vm.deleteForm(last.id) },
            onDismiss = { confirmUndo = false },
        ) else confirmUndo = false
    }
}

@Composable
private fun CameraView(
    busy: Boolean,
    onBusy: (Boolean) -> Unit,
    onCaptured: (android.graphics.Bitmap, Int) -> Unit,
    onError: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(ctx).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            imageCaptureResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(Size(2048, 1536), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                )
                .build()
        }
    }
    DisposableEffect(owner) {
        controller.bindToLifecycle(owner)
        onDispose { controller.unbind() }
    }

    fun capture() {
        if (busy) return
        onBusy(true)
        controller.takePicture(
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val rot = image.imageInfo.rotationDegrees
                    val bmp = try {
                        image.toBitmap()
                    } catch (e: Exception) {
                        null
                    } finally {
                        image.close()
                    }
                    if (bmp == null) onError("Slike ni bilo mogoče prebrati") else onCaptured(bmp, rot)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception.message ?: "Napaka kamere")
                }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { c ->
                PreviewView(c).apply {
                    this.controller = controller
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            "Cela prijavnica naj bo v kadru. Tapni za ostrenje.",
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .background(Color(0x88000000))
                .padding(6.dp),
        )
        Surface(
            onClick = { capture() },
            shape = CircleShape,
            color = if (busy) Color.Gray else Color.White,
            border = androidx.compose.foundation.BorderStroke(6.dp, Blue),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .size(86.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator() else Text("SLIKAJ", fontWeight = FontWeight.Bold, color = Blue)
            }
        }
    }
}

@Composable
private fun ReviewPanel(
    vm: AppViewModel,
    initial: ScanResult,
    onSave: (String, String) -> Unit,
    onRetake: () -> Unit,
) {
    val s by vm.session.collectAsState()
    var program by remember(initial) { mutableStateOf(initial.program) }
    var name by remember(initial) { mutableStateOf(initial.name) }
    var showRaw by remember { mutableStateOf(false) }
    val canonical = if (program.isBlank()) "" else vm.canonicalProgram(program)
    val duplicate = s.forms.indexOfFirst {
        it.name.equals(name.trim(), ignoreCase = true) && it.program == canonical
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Preveri prebrano:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = program,
            onValueChange = { program = it },
            label = { Text("Program") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        if (canonical.isNotEmpty() && canonical != program.trim()) {
            Text("→ shranjeno kot: $canonical", color = Green, fontSize = 13.sp)
        } else if (canonical.isNotEmpty() && canonical !in s.programs) {
            Text("Nov program (doda se na seznam)", color = Orange, fontSize = 13.sp)
        }
        if (s.programs.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                s.programs.forEach { p ->
                    FilterChip(selected = canonical == p, onClick = { program = p }, label = { Text(p) })
                }
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Učenec (ime in priimek)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        if (duplicate >= 0) {
            Text("Pozor: ta prijavnica je morda že skenirana (#${duplicate + 1}).", color = Color(0xFFB00020))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f).height(56.dp)) { Text("Slikaj znova") }
            Button(
                onClick = { onSave(program, name) },
                enabled = program.isNotBlank() && name.isNotBlank(),
                modifier = Modifier.weight(1.4f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green),
            ) { Text("Shrani ✓", fontSize = 18.sp) }
        }
        Hint("Po shranjevanju prijavnico položi z obrazom navzgor na vrh kupa.")
        TextButton(onClick = { showRaw = !showRaw }) { Text(if (showRaw) "Skrij prebrano besedilo" else "Pokaži prebrano besedilo") }
        if (showRaw) Text(initial.raw.ifBlank { "(ni besedila)" }, fontSize = 12.sp)
    }
}
