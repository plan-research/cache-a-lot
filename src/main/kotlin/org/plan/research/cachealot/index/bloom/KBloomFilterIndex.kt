package org.plan.research.cachealot.index.bloom

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.KFormulaeIndex
import org.plan.research.cachealot.index.KIndex

class KBloomFilterIndex<V> : KIndex<KBloomFilterKey, V> {
    private val cores = mutableListOf<Pair<KBloomFilterKey, V>>()

    override suspend fun insert(key: KBloomFilterKey, value: V) {
        cores += key to value
    }

    override suspend fun getCandidates(key: KBloomFilterKey): Flow<V> = cores.asSequence().mapNotNull { (bs, exprs) ->
        exprs.takeIf { !key.inverted.intersects(bs.origin) }
    }.asFlow()
}
