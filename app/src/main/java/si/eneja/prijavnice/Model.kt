package si.eneja.prijavnice

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Form(
    val id: Int,
    val program: String,
    val name: String,
    val raw: String = "",
)

@Serializable
data class SessionData(
    /** prijavnice v vrstnem redu slikanja (prva slikana = indeks 0 = na dnu kupa) */
    val forms: List<Form> = emptyList(),
    val programs: List<String> = emptyList(),
    val nextId: Int = 1,
    val distStep: Int = 0,
    val sortStep: Map<String, Int> = emptyMap(),
    val sortBySurname: Boolean = false,
    val speakEnabled: Boolean = true,
    val speakNames: Boolean = false,
    val listenEnabled: Boolean = false,
)

class SessionStore(context: Context) {
    private val file = File(context.filesDir, "seja.json")
    private val tmp = File(context.filesDir, "seja.json.tmp")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun load(): SessionData = try {
        if (file.exists()) json.decodeFromString(SessionData.serializer(), file.readText()) else SessionData()
    } catch (e: Exception) {
        SessionData()
    }

    fun save(data: SessionData) {
        tmp.writeText(json.encodeToString(SessionData.serializer(), data))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
