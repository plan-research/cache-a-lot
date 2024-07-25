package org.plan.research.cachealot.index.bloom

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.context.KCacheHashingContext
import org.plan.research.cachealot.index.KIndex
import org.plan.research.cachealot.index.KKeyComputer

class KBloomFilterIndex(val nbits: Int) : KIndex<KBloomFilterKey, KCacheHashingContext.Local, KCacheHashingContext> {
    private val cores = mutableListOf<Pair<KBloomFilterKey, KBoolExprs>>()

    override suspend fun insert(ctx: KCacheHashingContext, key: KBloomFilterKey, value: KBoolExprs) {
        cores += key to value
    }

    override suspend fun getCandidates(ctx: KCacheHashingContext.Local, key: KBloomFilterKey): Flow<KBoolExprs> =
        cores.asSequence().mapNotNull { (bs, exprs) ->
            exprs.takeIf { !key.inverted.intersects(bs.origin) }
        }.asFlow()

    override fun createCoreKeyComputer(ctx: KCacheHashingContext): KKeyComputer<KBloomFilterKey> =
        KBloomFilterComputer(nbits, ctx.coreHasher)

    override fun createExprKeyComputer(ctx: KCacheHashingContext.Local): KKeyComputer<KBloomFilterKey> =
        KBloomFilterComputer(nbits, ctx.exprHasher, true)
}
