package si.eneja.prijavnice.core

import java.text.Normalizer

/** Ena vrstica prepoznanega besedila z obrobo (v pikslih slike). */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height get() = bottom - top
    val centerY get() = (top + bottom) / 2
}

data class Extracted(val program: String, val name: String, val programMatchedKnown: Boolean)

object TextNorm {
    /** male črke, brez šumnikov in naglasov, enojni presledki */
    fun norm(s: String): String {
        val d = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        val sb = StringBuilder()
        for (ch in d) {
            if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
            sb.append(if (ch == 'đ') 'd' else ch)
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }

    fun similarity(a: String, b: String): Double {
        val x = norm(a)
        val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return 1.0 - levenshtein(x, y).toDouble() / maxOf(x.length, y.length)
    }

    /** "LOGISTIČNI TEHNIK" -> "Logistični tehnik" (samo če je vse z velikimi črkami) */
    fun tidyCase(s: String, titleEachWord: Boolean): String {
        val letters = s.filter { it.isLetter() }
        if (letters.isEmpty() || letters.any { it.isLowerCase() }) return s
        val words = s.lowercase().split(" ")
        return words.mapIndexed { i, w ->
            if (titleEachWord || i == 0) w.replaceFirstChar { it.titlecase() } else w
        }.joinToString(" ")
    }
}

object ProgramMatcher {
    const val THRESHOLD = 0.8

    /** Vrne najbolj podoben znan program (ali null). */
    fun match(text: String, known: Collection<String>): String? {
        val cleaned = FormExtractor.stripProgramLabel(text)
        var best: String? = null
        var bestSim = 0.0
        for (k in known) {
            val s = TextNorm.similarity(cleaned, k)
            if (s > bestSim) {
                bestSim = s; best = k
            }
        }
        return if (bestSim >= THRESHOLD) best else null
    }
}

object FormExtractor {
    // Oznake polja z imenom učenca (normalizirano, brez šumnikov), po prednosti.
    private val NAME_LABELS = listOf(
        "ime in priimek ucenca", "ime in priimek ucenke", "ime in priimek dijaka", "ime in priimek dijakinje",
        "ime in priimek", "priimek in ime", "ime ucenca", "ime ucenke", "ime dijaka",
        "ucenec/ucenka", "ucenec/ka", "ucenec", "ucenka", "dijak/dijakinja", "dijak/inja", "dijakinja", "dijak",
        "kandidat/ka", "kandidatka", "kandidat", "udelezenec", "udelezenka", "student", "ime",
    )

    // Oznake drugih polj; če se pojavijo, tam vrednost imena konča.
    private val OTHER_LABELS = listOf(
        "ucitelj", "uciteljica", "mentor", "mentorica", "profesor", "profesorica", "razrednik", "razredničarka",
        "datum", "ocena", "opis", "program", "razred", "oddelek", "podpis", "predmet", "letnik",
        "solsko leto", "telefon", "e-posta", "email", "naslov", "rojen", "emso", "opombe",
    ).map { TextNorm.norm(it) }

    private val NOT_PROGRAM = listOf(
        "prijavnica", "prijava", "obrazec", "solsko leto", "srednja sola", "solski center", "stran",
    )

    fun stripProgramLabel(text: String): String {
        return text.trim()
            .replace(Regex("^(izobraževalni\\s+)?program(\\s+izobraževanja)?\\s*[:\\-–.]?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^(predmet|smer)\\s*[:\\-–.]\\s*", RegexOption.IGNORE_CASE), "")
            .trim(' ', ':', '-', '–', '.', '_')
    }

    fun extract(linesIn: List<OcrLine>, knownPrograms: Collection<String>): Extracted {
        val lines = linesIn.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.top }, { it.left }))
        if (lines.isEmpty()) return Extracted("", "", false)
        val (program, matched) = findProgram(lines, knownPrograms)
        val name = findName(lines)
        return Extracted(program, name, matched)
    }

    private fun findProgram(lines: List<OcrLine>, known: Collection<String>): Pair<String, Boolean> {
        val minY = lines.minOf { it.top }
        val maxY = lines.maxOf { it.bottom }
        val h = (maxY - minY).coerceAtLeast(1)
        val upper = lines.filter { it.top <= minY + h * 0.45 }

        // 1) znan program v zgornjem delu
        for (l in upper) {
            val m = ProgramMatcher.match(l.text, known)
            if (m != null) return m to true
        }
        // 2) oznaka "Program:" z vrednostjo v isti ali naslednji vrstici
        for ((i, l) in upper.withIndex()) {
            val n = TextNorm.norm(l.text)
            if (n.startsWith("program") || n.startsWith("izobrazevalni program")) {
                val v = stripProgramLabel(l.text)
                if (v.count { it.isLetter() } >= 3) return clean(v) to false
                val next = nearestValues(l, lines, upper.getOrNull(i + 1)).firstOrNull()
                if (next != null) return clean(stripProgramLabel(next.text)) to false
            }
        }
        // 3) največja pisava v zgornji tretjini (naslov), ki ni splošna beseda
        val top = lines.filter { it.top <= minY + h * 0.33 }.filter { l ->
            val n = TextNorm.norm(l.text)
            l.text.count { it.isLetter() } >= 3 && NOT_PROGRAM.none { n.startsWith(it) } &&
                NAME_LABELS.none { n.startsWith(it) } && OTHER_LABELS.none { n.startsWith(it) && it != "program" }
        }
        val maxH = top.maxOfOrNull { it.height } ?: return "" to false
        val title = top.filter { it.height >= maxH * 0.8 }.minByOrNull { it.top }
            ?: return "" to false
        // naslov je lahko v dveh vrsticah z enako pisavo
        val second = top.firstOrNull {
            it !== title && it.height >= maxH * 0.8 && it.top > title.top &&
                it.top - title.bottom < title.height * 0.8
        }
        val text = if (second != null) title.text + " " + second.text else title.text
        return clean(stripProgramLabel(text)) to false
    }

    private fun clean(s: String) =
        TextNorm.tidyCase(s.replace(Regex("[_]{2,}|\\.{3,}"), " ").replace(Regex("\\s+"), " ").trim(' ', ':', '-', '.'), false)

    private fun findName(lines: List<OcrLine>): String {
        for (label in NAME_LABELS) {
            for ((i, l) in lines.withIndex()) {
                val n = TextNorm.norm(l.text)
                val idx = labelIndex(n, label) ?: continue
                // vrednost v isti vrstici za oznako
                val after = afterLabel(l.text, n, idx + label.length)
                val v1 = cleanName(cutAtOtherLabel(after))
                if (v1.isNotEmpty()) {
                    if (looksLikeName(v1) && v1.first().isUpperCase()) return v1
                    continue // npr. "Učenec je pokazal ..." v opisu
                }
                // vrednost desno ali spodaj
                for (next in nearestValues(l, lines, lines.getOrNull(i + 1))) {
                    val v2 = cleanName(cutAtOtherLabel(next.text))
                    if (looksLikeName(v2) && v2.first().isUpperCase()) return v2
                }
            }
        }
        return ""
    }

    /** Oznaka mora biti na začetku vrstice ali za presledkom in je ne sme slediti črka. */
    private fun labelIndex(normLine: String, label: String): Int? {
        var from = 0
        while (true) {
            val i = normLine.indexOf(label, from)
            if (i < 0) return null
            val beforeOk = i == 0 || !normLine[i - 1].isLetter()
            val end = i + label.length
            val afterOk = end >= normLine.length || !normLine[end].isLetter()
            // "ime in priimek" ne sme biti del "ime in priimek ucitelja", "ime programa" ...
            val rest = normLine.substring(end).trimStart(' ', ':')
            val badRest = listOf("program", "ucitelj", "mentor", "profesor", "razrednik", "sol", "stars", "predmet")
                .any { rest.startsWith(it) }
            // na začetku vrstice ali (sredi vrstice) z dvopičjem za oznako
            val placeOk = i < 3 || normLine.substring(end).trimStart().startsWith(":")
            if (beforeOk && afterOk && !badRest && placeOk) return i
            from = i + 1
            if (from >= normLine.length) return null
        }
    }

    /** Ker ima normaliziran niz enako dolžino kot izvirni (razen redkih znakov), vzamemo ostanek. */
    private fun afterLabel(original: String, norm: String, normEnd: Int): String {
        // poiščemo isti položaj v originalu tako, da normaliziramo predpone
        val trimmed = original.trim().replace(Regex("\\s+"), " ")
        for (k in 0..trimmed.length) {
            if (TextNorm.norm(trimmed.substring(0, k)).length >= normEnd) {
                return trimmed.substring(k).trim().trimStart(':', '-', '–', '.', ' ', '_')
            }
        }
        return ""
    }

    private fun cutAtOtherLabel(s: String): String {
        val n = TextNorm.norm(s)
        var cut = s.length
        for (o in OTHER_LABELS) {
            val r = Regex("(^|\\s)" + Regex.escape(o) + "\\b")
            val m = r.find(n) ?: continue
            val pos = m.range.first
            if (pos < cut) cut = pos
        }
        return if (cut < s.length) s.substring(0, minOf(cut, s.length)) else s
    }

    private fun cleanName(s: String): String {
        val c = s.replace(Regex("[_]{2,}|\\.{2,}|[|:;]"), " ")
            .replace(Regex("[^\\p{L} '\\-.]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '.', ',')
        return TextNorm.tidyCase(c, true)
    }

    private fun looksLikeName(s: String): Boolean {
        val words = s.split(" ").filter { w -> w.count { it.isLetter() } >= 2 }
        if (words.isEmpty() || words.size > 5) return false
        val n = TextNorm.norm(s)
        if (OTHER_LABELS.any { n.startsWith(it) } || NAME_LABELS.any { n == it }) return false
        return s.count { it.isLetter() } >= 3
    }

    /** Kandidati za vrednost polja: najprej desno od oznake (v isti višini), nato tik pod njo. */
    private fun nearestValues(label: OcrLine, lines: List<OcrLine>, fallback: OcrLine?): List<OcrLine> {
        val right = lines.filter {
            it !== label && it.left >= label.right - 5 &&
                it.centerY in (label.top - label.height / 2)..(label.bottom + label.height / 2)
        }.minByOrNull { it.left }
        val below = lines.filter {
            it !== label && it.top >= label.top + label.height / 2 &&
                it.top - label.bottom <= label.height * 3 &&
                it.right > label.left - label.height * 2 && it.left < label.right + label.height * 10
        }.minByOrNull { (it.top - label.bottom) * 4 + kotlin.math.abs(it.left - label.left) }
        val fb = fallback?.takeIf { it.top >= label.top && it.top - label.bottom <= label.height * 3 }
        return listOfNotNull(right, below, fb).distinct()
    }
}
