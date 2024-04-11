package org.plan.research.cachealot.index

import kotlinx.coroutines.flow.Flow

interface KIndex<K, V> {
    suspend fun insert(key: K, value: V)
    suspend fun getCandidates(key: K): Flow<V>
}
