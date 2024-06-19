package org.plan.research.cachealot.index.bloom

import java.util.BitSet

sealed class KBloomFilterKey(val nbits: Int) {
    abstract val origin: BitSet
    abstract val inverted: BitSet

    protected fun BitSet.getInverted() = (clone() as BitSet).apply { flip(0, nbits) }
}

class KBloomFilterKeyOrigin(nbits: Int, override val origin: BitSet) : KBloomFilterKey(nbits) {
    override val inverted: BitSet by lazy { origin.getInverted() }
}

class KBloomFilterKeyInverted(nbits: Int, override val inverted: BitSet) : KBloomFilterKey(nbits) {
    override val origin: BitSet by lazy { inverted.getInverted() }
}
