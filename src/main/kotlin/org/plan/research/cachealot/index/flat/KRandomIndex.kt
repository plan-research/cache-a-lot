package org.plan.research.cachealot.index.flat

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.context.KEmptyGlobal
import org.plan.research.cachealot.context.KEmptyLocal
import org.plan.research.cachealot.randomSequence

class KRandomIndex(private val numberOfCandidates: Int) : KFlatIndex<KEmptyLocal, KEmptyGlobal>() {
    private val mutex = Mutex()
    private var candidates = persistentListOf<KBoolExprs>()

    override suspend fun getCandidates(ctx: KEmptyLocal): Flow<KBoolExprs> {
        val current = mutex.withLock { candidates }
        return current.randomSequence().take(numberOfCandidates).asFlow()
    }

    override suspend fun insert(ctx: KEmptyGlobal, value: KBoolExprs) {
        mutex.withLock { candidates = candidates.add(value) }
    }
}
