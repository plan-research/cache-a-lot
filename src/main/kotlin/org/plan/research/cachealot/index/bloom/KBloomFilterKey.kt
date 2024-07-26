package org.plan.research.cachealot.index.bloom

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import java.util.BitSet
import java.util.concurrent.atomic.AtomicReference

sealed class KBloomFilterKey(val nbits: Int) {
    suspend abstract fun getOrigin(): BitSet
    suspend abstract fun getInverted(): BitSet

    protected fun BitSet.getInverted() = (clone() as BitSet).apply { flip(0, nbits) }
}

class KBloomFilterKeyOrigin(nbits: Int, private val origin: BitSet) : KBloomFilterKey(nbits) {
    private val inverted = MutableStateFlow<BitSet?>(null)

    override suspend fun getOrigin(): BitSet = origin
    override suspend fun getInverted(): BitSet = inverted.updateAndGet {
        it ?: origin.getInverted()
    }!!
}

class KBloomFilterKeyInverted(nbits: Int, private val inverted: BitSet) : KBloomFilterKey(nbits) {
    private val origin = MutableStateFlow<BitSet?>(null)

    override suspend fun getInverted(): BitSet = inverted
    override suspend fun getOrigin(): BitSet = origin.updateAndGet {
        it ?: inverted.getInverted()
    }!!
}

class KBloomFilterLazyKey(nbits: Int, private val computeKey: suspend () -> KBloomFilterKey) : KBloomFilterKey(nbits) {
    private val key = MutableStateFlow<KBloomFilterKey?>(null)

    private suspend fun getKey(): KBloomFilterKey = key.updateAndGet {
        it ?: computeKey()
    }!!

    override suspend fun getOrigin(): BitSet = getKey().getOrigin()
    override suspend fun getInverted(): BitSet = getKey().getInverted()
}
