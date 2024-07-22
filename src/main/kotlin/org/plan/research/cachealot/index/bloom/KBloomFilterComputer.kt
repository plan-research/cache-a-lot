package org.plan.research.cachealot.index.bloom

import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.hash.KExprHasher
import org.plan.research.cachealot.index.KKeyComputer
import java.util.BitSet

class KBloomFilterComputer(
    val nbits: Int,
    val hasher: KExprHasher,
    val computeInverted: Boolean = false
) : KKeyComputer<KBloomFilterKey> {
    override suspend fun invoke(exprs: KBoolExprs): KBloomFilterKey =
        KBloomFilterLazyKey(nbits) {
            val bitSet = BitSet(nbits)
            if (computeInverted) {
                bitSet.set(0, nbits)
            }
            exprs.forEach { expr ->
                var index = (hasher.computeHash(expr) % nbits).toInt()
                if (index < 0) index += nbits
                bitSet[index] = !computeInverted
            }
            if (computeInverted) {
                KBloomFilterKeyInverted(nbits, bitSet)
            } else {
                KBloomFilterKeyOrigin(nbits, bitSet)
            }
        }
}
