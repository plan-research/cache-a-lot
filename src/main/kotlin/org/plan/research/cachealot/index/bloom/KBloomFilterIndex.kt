package org.plan.research.cachealot.index.bloom

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.context.KCacheHashingContext
import org.plan.research.cachealot.index.KIndex
import org.plan.research.cachealot.index.KKeyComputer

class KBloomFilterIndex(val nbits: Int) : KIndex<KBloomFilterKey, KCacheHashingContext.Local, KCacheHashingContext> {
    private val mutex = Mutex()
    private var cores = persistentListOf<Pair<KBloomFilterKey, KBoolExprs>>()

    override suspend fun insert(ctx: KCacheHashingContext, key: KBloomFilterKey, value: KBoolExprs) {
        mutex.withLock {
            cores = cores.add(key to value)
        }
    }

    override suspend fun getCandidates(ctx: KCacheHashingContext.Local, key: KBloomFilterKey): Flow<KBoolExprs> = flow {
        val copy = mutex.withLock { cores }
        for ((bs, exprs) in copy) {
            val result = exprs.takeIf { !key.getInverted().intersects(bs.getOrigin()) }
            result?.let { emit(it) }
        }
    }

    override fun createCoreKeyComputer(ctx: KCacheHashingContext): KKeyComputer<KBloomFilterKey> =
        KBloomFilterComputer(nbits, ctx.coreHasher)

    override fun createExprKeyComputer(ctx: KCacheHashingContext.Local): KKeyComputer<KBloomFilterKey> =
        KBloomFilterComputer(nbits, ctx.exprHasher, true)
}
