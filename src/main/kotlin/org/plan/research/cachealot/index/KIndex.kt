package org.plan.research.cachealot.index

import kotlinx.coroutines.flow.Flow
import org.plan.research.cachealot.KBoolExprs

interface KIndex<K> {
    suspend fun insert(key: K, value: KBoolExprs)
    suspend fun getCandidates(key: K): Flow<KBoolExprs>

    fun createCoreKeyComputer(): KKeyComputer<K>
    fun createExprKeyComputer(): KKeyComputer<K>
}
