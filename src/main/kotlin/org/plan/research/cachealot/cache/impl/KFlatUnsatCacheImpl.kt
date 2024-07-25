package org.plan.research.cachealot.cache.impl

import kotlinx.coroutines.flow.firstOrNull
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.cache.KUnsatCache
import org.plan.research.cachealot.context.KGlobalCacheContext
import org.plan.research.cachealot.context.KLocalCacheContext
import org.plan.research.cachealot.index.flat.KFlatIndex
import org.plan.research.cachealot.testers.KUnsatTester

class KFlatUnsatCacheImpl<L : KLocalCacheContext, G : KGlobalCacheContext<L>>(
    private val context: G,
    private val index: KFlatIndex<L, G>,
    private val tester: KUnsatTester<L>,
) : KUnsatCache {

    override suspend fun addUnsatCore(unsatCore: KBoolExprs) {
        index.insert(context, unsatCore)
    }

    override suspend fun check(exprs: KBoolExprs): Boolean {
        val localContext = context.createLocal()
        try {
            return index.getCandidates(localContext).firstOrNull { candidate ->
                tester.test(localContext, candidate, exprs)
            } != null
        } finally {
            localContext.clear()
        }
    }

}