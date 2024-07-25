package org.plan.research.cachealot.index.logging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.context.KGlobalCacheContext
import org.plan.research.cachealot.context.KLocalCacheContext
import org.plan.research.cachealot.index.KIndex
import org.plan.research.cachealot.index.flat.KFlatIndex
import org.plan.research.cachealot.statLogger
import java.util.concurrent.atomic.AtomicInteger

private fun <V> Flow<V>.wrapConsumed(name: String): Flow<V> {
    val consumed = AtomicInteger()
    return onEach {
        consumed.incrementAndGet()
    }.onCompletion {
        statLogger.info {
            "$name candidates consumed: ${consumed.get()}"
        }
    }
}

class KIndexCandidatesNumberLogDecorator<K, in L : KLocalCacheContext, in G : KGlobalCacheContext<*>>(
    private val name: String,
    private val inner: KIndex<K, L, G>
) : KIndex<K, L, G> by inner {
    override suspend fun getCandidates(ctx: L, key: K): Flow<KBoolExprs> =
        inner.getCandidates(ctx, key).wrapConsumed(name)
}


class KFlatIndexCandidatesNumberLogDecorator<in L : KLocalCacheContext, in G : KGlobalCacheContext<*>>(
    private val name: String,
    private val inner: KFlatIndex<L, G>
) : KFlatIndex<L, G>() {
    override suspend fun getCandidates(ctx: L): Flow<KBoolExprs> = inner.getCandidates(ctx).wrapConsumed(name)
    override suspend fun insert(ctx: G, value: KBoolExprs) = inner.insert(ctx, value)
}

fun <L : KLocalCacheContext, G : KGlobalCacheContext<L>> KFlatIndex<L, G>.withCandidatesNumberLog(name: String? = null): KFlatIndex<L, G> =
    KFlatIndexCandidatesNumberLogDecorator(name ?: javaClass.simpleName, this)

fun <K, L : KLocalCacheContext, G : KGlobalCacheContext<L>> KIndex<K, L, G>.withCandidatesNumberLog(name: String? = null): KIndex<K, L, G> =
    KIndexCandidatesNumberLogDecorator(name ?: javaClass.simpleName, this)
