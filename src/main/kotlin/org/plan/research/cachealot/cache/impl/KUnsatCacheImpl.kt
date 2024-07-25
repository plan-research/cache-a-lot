package org.plan.research.cachealot.cache.impl

import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.firstOrNull
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.cache.KUnsatCache
import org.plan.research.cachealot.context.KGlobalCacheContext
import org.plan.research.cachealot.context.KLocalCacheContext
import org.plan.research.cachealot.index.KIndex
import org.plan.research.cachealot.index.KKeyComputer
import org.plan.research.cachealot.testers.KUnsatTester

class KUnsatCacheImpl<K, L : KLocalCacheContext, G : KGlobalCacheContext<L>>(
    private val context: G,
    private val index: KIndex<K, L, G>,
    private val tester: KUnsatTester<L>,
) : KUnsatCache {

    private val coreKeyComputer: KKeyComputer<K> = index.createCoreKeyComputer(context)

    override suspend fun addUnsatCore(unsatCore: KBoolExprs) {
        val key = coreKeyComputer(unsatCore)
        index.insert(context, key, unsatCore)
    }

    override suspend fun check(exprs: KBoolExprs): Boolean {
        val localContext = context.createLocal()
        try {
            val key = index.createExprKeyComputer(localContext).invoke(exprs)
            return index.getCandidates(localContext, key).cancellable().firstOrNull { candidate ->
                tester.test(localContext, candidate, exprs)
            } != null
        } finally {
            localContext.clear()
        }
    }

}