package org.plan.research.cachealot.index.flat

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.plan.research.cachealot.KBoolExprs

class KListIndex : KFlatIndex() {
    private val mutex = Mutex()
    private var candidates = persistentListOf<KBoolExprs>()

    override suspend fun getCandidates(): Flow<KBoolExprs> =
        mutex.withLock { candidates }.asFlow()

    override suspend fun insert(value: KBoolExprs) {
        mutex.withLock { candidates = candidates.add(value) }
    }
}