package org.plan.research.cachealot.index.flat

import kotlinx.coroutines.flow.Flow
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.context.KGlobalCacheContext
import org.plan.research.cachealot.context.KLocalCacheContext
import org.plan.research.cachealot.index.KEmptyComputer
import org.plan.research.cachealot.index.KIndex
import org.plan.research.cachealot.index.KKeyComputer

abstract class KFlatIndex<in L : KLocalCacheContext, in G : KGlobalCacheContext<*>> : KIndex<Unit, L, G> {
    abstract suspend fun getCandidates(ctx: L): Flow<KBoolExprs>
    abstract suspend fun insert(ctx: G, value: KBoolExprs)

    final override fun createCoreKeyComputer(ctx: G): KKeyComputer<Unit> = KEmptyComputer
    final override fun createExprKeyComputer(ctx: L): KKeyComputer<Unit> = KEmptyComputer

    final override suspend fun getCandidates(ctx: L, key: Unit): Flow<KBoolExprs> = getCandidates(ctx)
    final override suspend fun insert(ctx: G, key: Unit, value: KBoolExprs) = insert(ctx, value)
}
