package org.plan.research.cachealot.index

import kotlinx.coroutines.flow.Flow
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.context.KGlobalCacheContext
import org.plan.research.cachealot.context.KLocalCacheContext

interface KIndex<K, in L: KLocalCacheContext, in G: KGlobalCacheContext<*>> {
    suspend fun insert(ctx: G, key: K, value: KBoolExprs)
    suspend fun getCandidates(ctx: L, key: K): Flow<KBoolExprs>

    fun createCoreKeyComputer(ctx: G): KKeyComputer<K>
    fun createExprKeyComputer(ctx: L): KKeyComputer<K>
}
