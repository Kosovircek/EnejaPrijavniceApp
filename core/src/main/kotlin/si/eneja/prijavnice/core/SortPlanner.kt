package si.eneja.prijavnice.core

/**
 * Fizikalni model (vse prijavnice so ves čas obrnjene s sliko navzgor):
 *
 *  - IZVORNI kup: kupček enega programa. Prijavnice jemljemo z vrha.
 *  - POMOŽNI kupčki ("mesta" 1, 2, 3 ... na mizi): prijavnico vedno položimo NA VRH kupčka.
 *    Vsak pomožni kupček je od vrha navzdol urejen OD Ž PROTI A (na vrhu je abecedno največja).
 *    Zato lahko prijavnico položimo na kupček le, če je abecedno za trenutno vrhnjo.
 *  - KONČNI kup: vanj polagamo prijavnice od Ž proti A, vsako na vrh. Ko je končano,
 *    je na vrhu A, spodaj Ž (od vrha navzdol po abecedi).
 *
 * Takoj ko je abecedno največja še nerazvrščena prijavnica dostopna (na vrhu izvornega ali
 * katerega od pomožnih kupčkov), gre na končni kup. Tako se pomožni kupčki sproti praznijo.
 * Kadar se cel kupček lahko brez škode položi na drugega, ga združimo (manj kupčkov na mizi).
 * Kam položiti vsako prijavnico, izberemo s simulacijo preostanka (rollout), tako da je največje
 * število kupčkov hkrati na mizi čim manjše. Nikoli ni več kupčkov kot dolžina najdaljšega
 * padajočega podzaporedja (teoretični minimum pri enem prehodu brez sprotnega odlaganja).
 */
sealed class SortMove {
    /** Vrhnjo prijavnico z izvornega kupa položi na pomožni kupček [slot]. */
    data class SourceToPile(val card: Int, val slot: Int, val newPile: Boolean) : SortMove()

    /** Vrhnjo prijavnico z izvornega kupa položi na končni kup. */
    data class SourceToOutput(val card: Int) : SortMove()

    /** Vrhnjo prijavnico s pomožnega kupčka [slot] položi na končni kup. */
    data class PileToOutput(val card: Int, val slot: Int) : SortMove()

    /** Cel kupček [fromSlot] (brez obračanja) položi na kupček [ontoSlot]. */
    data class StackPile(
        val fromSlot: Int,
        val ontoSlot: Int,
        val count: Int,
        val topCard: Int,
        val bottomCard: Int,
    ) : SortMove()
}

/** Stanje na mizi. Vsi seznami so od vrha navzdol, vsebujejo id-je prijavnic. */
data class TableState(
    val source: List<Int>,
    val piles: Map<Int, List<Int>>,
    val output: List<Int>,
)

class SortPlan(
    val moves: List<SortMove>,
    /** states[i] je stanje PRED korakom i; states.last() je končno stanje. */
    val states: List<TableState>,
    val peakPiles: Int,
    val longestDecreasing: Int,
)

object SortPlanner {
    private class Pile(val slot: Int, val cards: ArrayList<Int>) // od spodaj navzgor (zadnji = vrh)

    private class Sim(
        val src: IntArray,
        var s: Int,
        val piles: ArrayList<Pile>,
        var out: Int,
        var moves: Int,
        var peak: Int,
    ) {
        val n get() = src.size
        fun copy() = Sim(src, s, ArrayList(piles.map { Pile(it.slot, ArrayList(it.cards)) }), out, moves, peak)
        fun maxRemaining() = n - 1 - out
        fun freeSlot(): Int {
            var k = 1
            while (piles.any { it.slot == k }) k++
            return k
        }
        fun notePeak() {
            if (piles.size > peak) peak = piles.size
        }
    }

    private data class Score(val peak: Int, val moves: Int) : Comparable<Score> {
        override fun compareTo(other: Score) =
            compareValuesBy(this, other, { it.peak }, { it.moves })
    }

    private const val MAX_DEAL_CANDIDATES = 6
    private const val MAX_STACK_CANDIDATES = 12

    /**
     * @param sourceTopToBottom id-ji prijavnic na izvornem kupu, od vrha navzdol
     * @param comparator abecedni vrstni red id-jev
     */
    fun plan(sourceTopToBottom: List<Int>, comparator: Comparator<Int>): SortPlan {
        val n = sourceTopToBottom.size
        val sorted = sourceTopToBottom.sortedWith(comparator)
        val idOfRank = sorted.toIntArray()
        val rankOfId = HashMap<Int, Int>()
        sorted.forEachIndexed { r, id -> rankOfId[id] = r }
        val src = IntArray(n) { rankOfId.getValue(sourceTopToBottom[it]) }

        val sim = Sim(src, 0, ArrayList(), 0, 0, 0)
        val moves = ArrayList<SortMove>()
        val states = ArrayList<TableState>()

        fun snapshot(): TableState = TableState(
            source = (sim.s until n).map { idOfRank[src[it]] },
            piles = sim.piles.sortedBy { it.slot }
                .associate { p -> p.slot to p.cards.asReversed().map { idOfRank[it] } },
            output = (n - sim.out until n).map { idOfRank[it] },
        )
        states.add(snapshot())
        val rec: (SortMove) -> Unit = { m ->
            moves.add(
                when (m) {
                    is SortMove.SourceToPile -> m.copy(card = idOfRank[m.card])
                    is SortMove.SourceToOutput -> m.copy(card = idOfRank[m.card])
                    is SortMove.PileToOutput -> m.copy(card = idOfRank[m.card])
                    is SortMove.StackPile -> m.copy(topCard = idOfRank[m.topCard], bottomCard = idOfRank[m.bottomCard])
                }
            )
            states.add(snapshot())
        }

        while (true) {
            autoOutput(sim, rec)
            if (sim.s >= n) break

            // Združevanje celih kupčkov, če to ne poslabša največjega števila kupčkov.
            while (sim.piles.size >= 2) {
                val base = rollout(sim)
                var best: Pair<Pile, Pile>? = null
                var bestScore: Score? = null
                var evaluated = 0
                loop@ for (from in sim.piles) for (onto in sim.piles) {
                    if (from === onto) continue
                    if (from.cards.first() <= onto.cards.last()) continue
                    if (evaluated++ >= MAX_STACK_CANDIDATES) break@loop
                    val c = sim.copy()
                    stack(c, c.piles.first { it.slot == from.slot }, c.piles.first { it.slot == onto.slot }, null)
                    val sc = rollout(c)
                    if (sc.peak <= base.peak && sc.moves <= base.moves + 1 &&
                        (bestScore == null || sc < bestScore)
                    ) {
                        best = from to onto; bestScore = sc
                    }
                }
                val b = best ?: break
                stack(sim, b.first, b.second, rec)
                autoOutput(sim, rec)
            }
            if (sim.s >= n) break

            val c = src[sim.s]
            val candidates: List<Pile?> = sim.piles
                .filter { it.cards.last() < c }
                .sortedByDescending { it.cards.last() }
                .take(MAX_DEAL_CANDIDATES) + listOf(null)
            var bestTarget: Pile? = candidates.first()
            var bestScore: Score? = null
            if (candidates.size > 1) {
                for (cand in candidates) {
                    val copy = sim.copy()
                    deal(copy, cand?.let { p -> copy.piles.first { it.slot == p.slot } }, null)
                    val sc = rollout(copy)
                    if (bestScore == null || sc < bestScore) {
                        bestScore = sc; bestTarget = cand
                    }
                }
            }
            deal(sim, bestTarget, rec)
        }
        check(sim.out == n && sim.piles.isEmpty())
        return SortPlan(moves, states, sim.peak, longestDecreasing(src))
    }

    private fun autoOutput(sim: Sim, rec: ((SortMove) -> Unit)?) {
        while (true) {
            val m = sim.maxRemaining()
            if (m < 0) return
            if (sim.s < sim.n && sim.src[sim.s] == m) {
                sim.s++; sim.out++; sim.moves++
                rec?.invoke(SortMove.SourceToOutput(m))
                continue
            }
            val p = sim.piles.firstOrNull { it.cards.last() == m } ?: return
            p.cards.removeAt(p.cards.size - 1)
            if (p.cards.isEmpty()) sim.piles.remove(p)
            sim.out++; sim.moves++
            rec?.invoke(SortMove.PileToOutput(m, p.slot))
        }
    }

    private fun deal(sim: Sim, target: Pile?, rec: ((SortMove) -> Unit)?) {
        val c = sim.src[sim.s]
        sim.s++; sim.moves++
        if (target == null) {
            val p = Pile(sim.freeSlot(), arrayListOf(c))
            sim.piles.add(p)
            sim.notePeak()
            rec?.invoke(SortMove.SourceToPile(c, p.slot, true))
        } else {
            require(target.cards.last() < c)
            target.cards.add(c)
            rec?.invoke(SortMove.SourceToPile(c, target.slot, false))
        }
    }

    private fun stack(sim: Sim, from: Pile, onto: Pile, rec: ((SortMove) -> Unit)?) {
        require(from.cards.first() > onto.cards.last())
        val count = from.cards.size
        val top = from.cards.last()
        val bottom = from.cards.first()
        onto.cards.addAll(from.cards)
        sim.piles.remove(from)
        sim.moves++
        rec?.invoke(SortMove.StackPile(from.slot, onto.slot, count, top, bottom))
    }

    /** Požrešna simulacija preostanka: vsako prijavnico na najtesnejši kupček. */
    private fun rollout(start: Sim): Score {
        val sim = start.copy()
        while (true) {
            autoOutput(sim, null)
            if (sim.s >= sim.n) break
            val c = sim.src[sim.s]
            var best: Pile? = null
            for (p in sim.piles) {
                val t = p.cards.last()
                if (t < c && (best == null || t > best.cards.last())) best = p
            }
            deal(sim, best, null)
        }
        return Score(sim.peak, sim.moves)
    }

    /** Dolžina najdaljšega strogo padajočega podzaporedja. */
    fun longestDecreasing(seq: IntArray): Int {
        val tails = ArrayList<Int>() // za naraščajoče na negiranih vrednostih
        for (v in seq) {
            val x = -v
            var lo = 0
            var hi = tails.size
            while (lo < hi) {
                val mid = (lo + hi) / 2
                if (tails[mid] < x) lo = mid + 1 else hi = mid
            }
            if (lo == tails.size) tails.add(x) else tails[lo] = x
        }
        return tails.size
    }
}

/** Korak razdeljevanja po programih. */
data class DistStep(
    /** indeks prijavnice v vrstnem redu slikanja */
    val formIndex: Int,
    val program: String,
    /** zaporedna številka kupčka programa (po vrsti, kot se pojavijo) */
    val pileNumber: Int,
    val firstOfProgram: Boolean,
)

object Distribution {
    /**
     * Kup je zložen tako, da je zadnja slikana prijavnica na vrhu. Jemljemo z vrha in polagamo
     * na kupčke programov (vsako na vrh). Po razdelitvi je na vsakem kupčku programa od vrha
     * navzdol vrstni red enak vrstnemu redu slikanja.
     */
    fun steps(programsInScanOrder: List<String>): List<DistStep> {
        val pileOf = LinkedHashMap<String, Int>()
        val out = ArrayList<DistStep>()
        for (i in programsInScanOrder.indices.reversed()) {
            val p = programsInScanOrder[i]
            val first = p !in pileOf
            val num = pileOf.getOrPut(p) { pileOf.size + 1 }
            out.add(DistStep(i, p, num, first))
        }
        return out
    }

    /** Indeksi prijavnic programa na njegovem kupčku od vrha navzdol (po razdelitvi). */
    fun pileTopToBottom(programsInScanOrder: List<String>, program: String): List<Int> =
        programsInScanOrder.indices.filter { programsInScanOrder[it] == program }
}
