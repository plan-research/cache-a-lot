package org.plan.research.cachealot.index.flat

import kotlinx.coroutines.flow.Flow
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.index.KEmptyComputer
import org.plan.research.cachealot.index.KIndex
import org.plan.research.cachealot.index.KKeyComputer

abstract class KFlatIndex : KIndex<Unit> {
    abstract suspend fun getCandidates(): Flow<KBoolExprs>
    abstract suspend fun insert(value: KBoolExprs)

    final override fun createCoreKeyComputer(): KKeyComputer<Unit> = KEmptyComputer
    final override fun createExprKeyComputer(): KKeyComputer<Unit> = KEmptyComputer

    final override suspend fun getCandidates(key: Unit): Flow<KBoolExprs> = getCandidates()
    final override suspend fun insert(key: Unit, value: KBoolExprs) = insert(value)
}
