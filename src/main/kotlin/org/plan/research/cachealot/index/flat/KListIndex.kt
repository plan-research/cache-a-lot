package org.plan.research.cachealot.index.flat

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class KListIndex<V>: KFlatIndex<V>() {
    private val mutex = Mutex()
    private var candidates = persistentListOf<V>()

    override suspend fun getCandidates(): Flow<V> =
        mutex.withLock { candidates }.asFlow()

    override suspend fun insert(value: V) {
        mutex.withLock { candidates = candidates.add(value) }
    }
}