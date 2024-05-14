package org.plan.research.cachealot.index.flat

import kotlinx.coroutines.flow.Flow
import org.plan.research.cachealot.index.KIndex

abstract class KFlatIndex<V> : KIndex<Unit, V> {
    abstract suspend fun getCandidates(): Flow<V>
    abstract suspend fun insert(value: V)

    final override suspend fun getCandidates(key: Unit): Flow<V> = getCandidates()
    final override suspend fun insert(key: Unit, value: V) = insert(value)
}
