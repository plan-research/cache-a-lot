package org.plan.research.cachealot.index.logging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
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

class KIndexCandidatesNumberLogDecorator<K, V>(
    private val name: String,
    private val inner: KIndex<K, V>
) : KIndex<K, V> by inner {
    override suspend fun getCandidates(key: K): Flow<V> = inner.getCandidates(key).wrapConsumed(name)
}


class KFlatIndexCandidatesNumberLogDecorator<V>(
    private val name: String,
    private val inner: KFlatIndex<V>
) : KFlatIndex<V>() {
    override suspend fun getCandidates(): Flow<V> = inner.getCandidates().wrapConsumed(name)
    override suspend fun insert(value: V) = inner.insert(value)
}

fun <V> KFlatIndex<V>.withCandidatesNumberLog(name: String? = null): KFlatIndex<V> =
    KFlatIndexCandidatesNumberLogDecorator(name ?: javaClass.simpleName, this)

fun <K, V> KIndex<K, V>.withCandidatesNumberLog(name: String? = null): KIndex<K, V> =
    KIndexCandidatesNumberLogDecorator(name ?: javaClass.simpleName, this)
