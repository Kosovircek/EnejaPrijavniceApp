package si.eneja.prijavnice.core

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SlovenianCollatorTest {
    private fun sorted(vararg s: String) = s.sortedWith(SlovenianCollator)

    @Test
    fun sumniki() {
        assertEquals(listOf("Cene", "Čeh", "Dolenc"), sorted("Dolenc", "Čeh", "Cene"))
        assertEquals(listOf("Sara", "Šinkovec", "Tina"), sorted("Šinkovec", "Tina", "Sara"))
        assertEquals(listOf("Zupan", "Žagar"), sorted("Žagar", "Zupan"))
        assertEquals(listOf("ana", "Ana Novak", "Anže"), sorted("Anže", "Ana Novak", "ana"))
    }

    @Test
    fun velikeMale() {
        assertTrue(SlovenianCollator.compare("NOVAK", "novak") != 0)
        assertTrue(SlovenianCollator.compare("NOVAK Ana", "novak Bor") < 0)
    }

    @Test
    fun priimek() {
        assertEquals("Novak Ana", NameKeys.sortKey("Ana Novak", true))
        assertEquals("Ana Novak", NameKeys.sortKey("  Ana   Novak ", false))
    }
}

class SortPlannerTest {
    /** Neodvisna fizična simulacija korakov: vrne končni kup od vrha navzdol. */
    private fun simulate(source: List<Int>, plan: SortPlan, cmp: Comparator<Int>): List<Int> {
        val src = ArrayDeque(source) // first = vrh
        val piles = HashMap<Int, ArrayDeque<Int>>() // first = vrh
        val out = ArrayDeque<Int>()
        var maxPiles = 0
        for ((i, m) in plan.moves.withIndex()) {
            when (m) {
                is SortMove.SourceToPile -> {
                    val c = src.removeFirst()
                    assertEquals(m.card, c)
                    val p = piles[m.slot]
                    if (m.newPile) {
                        assertTrue("mesto ${m.slot} ni prazno", p == null || p.isEmpty())
                        piles[m.slot] = ArrayDeque(listOf(c))
                    } else {
                        assertTrue(p != null && p.isNotEmpty())
                        assertTrue("prijavnica mora biti abecedno za vrhnjo", cmp.compare(c, p!!.first()) > 0)
                        p.addFirst(c)
                    }
                }
                is SortMove.SourceToOutput -> {
                    val c = src.removeFirst(); assertEquals(m.card, c); out.addFirst(c)
                }
                is SortMove.PileToOutput -> {
                    val p = piles.getValue(m.slot)
                    val c = p.removeFirst(); assertEquals(m.card, c); out.addFirst(c)
                    if (p.isEmpty()) piles.remove(m.slot)
                }
                is SortMove.StackPile -> {
                    val from = piles.remove(m.fromSlot)!!
                    val onto = piles.getValue(m.ontoSlot)
                    assertEquals(m.count, from.size)
                    assertEquals(m.topCard, from.first()); assertEquals(m.bottomCard, from.last())
                    assertTrue(cmp.compare(from.last(), onto.first()) > 0)
                    for (c in from.reversed()) onto.addFirst(c)
                }
            }
            maxPiles = maxOf(maxPiles, piles.size)
            // stanje v načrtu se mora ujemati
            val st = plan.states[i + 1]
            assertEquals(src.toList(), st.source)
            assertEquals(out.toList(), st.output)
            assertEquals(piles.filterValues { it.isNotEmpty() }.mapValues { it.value.toList() }, st.piles)
        }
        assertTrue(src.isEmpty())
        assertTrue(piles.values.all { it.isEmpty() })
        assertEquals(maxPiles, plan.peakPiles)
        return out.toList()
    }

    private fun check(names: List<String>) {
        val ids = names.indices.toList()
        val cmp = Comparator<Int> { a, b -> SlovenianCollator.compare(names[a], names[b]).let { if (it != 0) it else a.compareTo(b) } }
        val plan = SortPlanner.plan(ids, cmp)
        val result = simulate(ids, plan, cmp)
        assertEquals(ids.sortedWith(cmp), result)
        assertTrue("kupčkov ${plan.peakPiles} > ${plan.longestDecreasing}", plan.peakPiles <= plan.longestDecreasing)
        assertTrue(plan.moves.size <= 2 * names.size + names.size)
    }

    @Test
    fun posebniPrimeri() {
        check(emptyList())
        check(listOf("Ana"))
        check(listOf("Ana", "Bor", "Cene", "Čeh", "Žan"))   // že urejeno
        check(listOf("Žan", "Čeh", "Cene", "Bor", "Ana"))   // obratno
        check(listOf("Ana", "Ana", "Bor", "Ana"))            // enaka imena
    }

    @Test
    fun nakljucno() {
        val rnd = Random(7)
        repeat(300) {
            val n = rnd.nextInt(1, 60)
            check(List(n) { "N" + rnd.nextInt(0, 1000).toString().padStart(4, '0') })
        }
    }

    @Test
    fun velikKup() {
        val rnd = Random(1)
        val names = List(300) { "Ime" + rnd.nextInt(100000) }
        val t = System.currentTimeMillis()
        check(names)
        val ms = System.currentTimeMillis() - t
        println("300 prijavnic: ${ms} ms")
        assertTrue("prepočasi: $ms ms", ms < 20000)
    }

    @Test
    fun primerjavaZGreedy() {
        // Izpis za informacijo: povprečno število kupčkov
        val rnd = Random(3)
        var sumPeak = 0; var sumLds = 0; var sumMoves = 0
        repeat(100) {
            val n = 30
            val ids = (0 until n).shuffled(rnd)
            val plan = SortPlanner.plan(ids, naturalOrder())
            sumPeak += plan.peakPiles; sumLds += plan.longestDecreasing; sumMoves += plan.moves.size
        }
        println("n=30: povp. kupčkov ${sumPeak / 100.0}, LDS ${sumLds / 100.0}, potez ${sumMoves / 100.0}")
    }
}

class DistributionTest {
    @Test
    fun obratniVrstniRed() {
        val progs = listOf("A", "B", "A", "C", "B")
        val steps = Distribution.steps(progs)
        assertEquals(listOf(4, 3, 2, 1, 0), steps.map { it.formIndex })
        assertEquals(listOf(1, 2, 3, 1, 3), steps.map { it.pileNumber })
        assertEquals(listOf(true, true, true, false, false), steps.map { it.firstOfProgram })
        // na kupčku programa: zadnja položena (najmanjši indeks) je na vrhu
        assertEquals(listOf(0, 2), Distribution.pileTopToBottom(progs, "A"))
    }
}

class FormExtractorTest {
    private data class F(val program: String, val name: String, val layout: String, val lines: List<OcrLine>)

    private fun load(): List<F> {
        val txt = javaClass.classLoader.getResource("forms.json")!!.readText()
        val arr = JSONArray(txt)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ls = o.getJSONArray("lines")
            F(o.getString("program"), o.getString("name"), o.getString("layout"), (0 until ls.length()).map { j ->
                val l = ls.getJSONObject(j)
                OcrLine(l.getString("text"), l.getInt("left"), l.getInt("top"), l.getInt("right"), l.getInt("bottom"))
            })
        }
    }

    /** Najslabši primer: OCR združi vse besedilo v isti višini v eno vrstico. */
    private fun mergeRows(lines: List<OcrLine>): List<OcrLine> {
        val out = ArrayList<OcrLine>()
        for (l in lines.sortedBy { it.left }) {
            val idx = out.indexOfFirst { kotlin.math.abs(it.centerY - l.centerY) < minOf(it.height, l.height) / 2 }
            if (idx < 0) out.add(l) else {
                val o = out[idx]
                out[idx] = OcrLine(o.text + " " + l.text, minOf(o.left, l.left), minOf(o.top, l.top), maxOf(o.right, l.right), maxOf(o.bottom, l.bottom))
            }
        }
        return out
    }

    private fun run(merge: Boolean, known: Boolean) {
        val forms = load()
        val knownList = if (known) forms.map { it.program }.distinct() else emptyList()
        val errors = ArrayList<String>()
        for (f in forms) {
            val lines = if (merge) mergeRows(f.lines) else f.lines
            val e = FormExtractor.extract(lines.shuffled(Random(1)), knownList)
            if (TextNorm.norm(e.program) != TextNorm.norm(f.program)) errors.add("${f.layout} program '${e.program}' != '${f.program}'")
            if (e.name != f.name) errors.add("${f.layout} ime '${e.name}' != '${f.name}'")
            if (known) assertTrue(e.programMatchedKnown)
        }
        errors.forEach { println(it) }
        assertEquals("napake (merge=$merge, known=$known)", 0, errors.size)
    }

    @Test fun locene() = run(merge = false, known = false)
    @Test fun loceneZnani() = run(merge = false, known = true)
    @Test fun zdruzene() = run(merge = true, known = false)
    @Test fun zdruzeneZnani() = run(merge = true, known = true)

    @Test
    fun ujemanjeProgramov() {
        val known = listOf("Logistični tehnik", "Kozmetični tehnik", "Frizer")
        assertEquals("Logistični tehnik", ProgramMatcher.match("LOGISTICNI TEHNlK", known))
        assertEquals("Kozmetični tehnik", ProgramMatcher.match("Program: Kozmetični tehnik", known))
        assertEquals(null, ProgramMatcher.match("Trgovec", known))
    }
}
