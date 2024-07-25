package org.plan.research.cachealot.index.bloom

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.hash.KExprHasher
import org.plan.research.cachealot.index.KIndex
import org.plan.research.cachealot.index.KKeyComputer

class KBloomFilterIndex(
    val nbits: Int, val coreHasher: KExprHasher, val exprHasher: KExprHasher
) : KIndex<KBloomFilterKey> {
    private val cores = mutableListOf<Pair<KBloomFilterKey, KBoolExprs>>()

    override suspend fun insert(key: KBloomFilterKey, value: KBoolExprs) {
        cores += key to value
    }

    override suspend fun getCandidates(key: KBloomFilterKey): Flow<KBoolExprs> =
        cores.asSequence().mapNotNull { (bs, exprs) ->
            exprs.takeIf { !key.inverted.intersects(bs.origin) }
        }.asFlow()

    override fun createCoreKeyComputer(): KKeyComputer<KBloomFilterKey> =
        KBloomFilterComputer(nbits, coreHasher)

    override fun createExprKeyComputer(): KKeyComputer<KBloomFilterKey> =
        KBloomFilterComputer(nbits, exprHasher, true)
}
