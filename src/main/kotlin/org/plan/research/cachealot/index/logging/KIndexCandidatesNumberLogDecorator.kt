package org.plan.research.cachealot.index.logging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import org.plan.research.cachealot.KBoolExprs
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

class KIndexCandidatesNumberLogDecorator<K>(
    private val name: String,
    private val inner: KIndex<K>
) : KIndex<K> by inner {
    override suspend fun getCandidates(key: K): Flow<KBoolExprs> = inner.getCandidates(key).wrapConsumed(name)
}


class KFlatIndexCandidatesNumberLogDecorator(
    private val name: String,
    private val inner: KFlatIndex
) : KFlatIndex() {
    override suspend fun getCandidates(): Flow<KBoolExprs> = inner.getCandidates().wrapConsumed(name)
    override suspend fun insert(value: KBoolExprs) = inner.insert(value)
}

fun KFlatIndex.withCandidatesNumberLog(name: String? = null): KFlatIndex =
    KFlatIndexCandidatesNumberLogDecorator(name ?: javaClass.simpleName, this)

fun <K> KIndex<K>.withCandidatesNumberLog(name: String? = null): KIndex<K> =
    KIndexCandidatesNumberLogDecorator(name ?: javaClass.simpleName, this)
