package si.eneja.prijavnice.core

import java.text.Normalizer

/**
 * Primerjava nizov po slovenski abecedi (a b c č ć d đ e f g h i j k l m n o p q r s š t u v w x y z ž).
 * Velike/male črke niso pomembne, presledki ločijo besede, ločila se ignorirajo.
 */
object SlovenianCollator : Comparator<String> {
    private const val ORDER = "abcčćdđefghijklmnopqrsštuvwxyzž"

    private fun ranks(s: String): IntArray {
        val out = ArrayList<Int>(s.length)
        var lastSpace = true
        for (ch in s) {
            when {
                ch.isWhitespace() || ch == '-' -> {
                    if (!lastSpace) out.add(0)
                    lastSpace = true
                }
                ch.isDigit() -> {
                    out.add(1 + (ch - '0')); lastSpace = false
                }
                ch.isLetter() -> {
                    val lower = ch.lowercaseChar()
                    var idx = ORDER.indexOf(lower)
                    if (idx < 0) {
                        val base = Normalizer.normalize(lower.toString(), Normalizer.Form.NFD)
                            .firstOrNull { it.isLetter() } ?: lower
                        idx = ORDER.indexOf(base)
                        if (idx < 0) idx = 100 + base.code
                    }
                    out.add(20 + idx); lastSpace = false
                }
                else -> Unit // ločila ignoriramo
            }
        }
        while (out.isNotEmpty() && out.last() == 0) out.removeAt(out.size - 1)
        return out.toIntArray()
    }

    override fun compare(a: String, b: String): Int {
        val ra = ranks(a)
        val rb = ranks(b)
        val n = minOf(ra.size, rb.size)
        for (i in 0 until n) {
            if (ra[i] != rb[i]) return ra[i].compareTo(rb[i])
        }
        if (ra.size != rb.size) return ra.size.compareTo(rb.size)
        return a.compareTo(b)
    }
}

object NameKeys {
    /** Ključ za razvrščanje: po priimku (zadnja beseda naprej) ali kot je napisano. */
    fun sortKey(name: String, bySurname: Boolean): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (!bySurname || words.size < 2) return words.joinToString(" ")
        return (listOf(words.last()) + words.dropLast(1)).joinToString(" ")
    }
}
