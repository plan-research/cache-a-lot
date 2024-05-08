package org.plan.research.cachealot.index.composite

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.fold
import org.plan.research.cachealot.index.KIndex

class KIndexComposite<K, I, V, IN : KIndex<I, V>>(
    outerIndex: () -> KIndex<K, IN>,
    val innerIndex: () -> IN
) : KIndex<Pair<K, I>, V> {
    private val outer = outerIndex()

    override suspend fun insert(key: Pair<K, I>, value: V) {
        val added = outer.getCandidates(key.first).fold(false) { _, inner ->
            inner.insert(key.second, value)
            true
        }

        if (!added) {
            outer.insert(key.first, innerIndex().apply {
                insert(key.second, value)
            })
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getCandidates(key: Pair<K, I>): Flow<V> =
        outer.getCandidates(key.first).flatMapConcat {
            it.getCandidates(key.second)
        }
}