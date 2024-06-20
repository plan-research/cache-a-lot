package org.plan.research.cachealot.cache.impl

import kotlinx.coroutines.flow.firstOrNull
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.cache.KUnsatCache
import org.plan.research.cachealot.index.flat.KFlatIndex
import org.plan.research.cachealot.testers.KUnsatTester

class KFlatUnsatCacheImpl(
    private val index: KFlatIndex,
    private val tester: KUnsatTester,
) : KUnsatCache {

    override suspend fun addUnsatCore(unsatCore: KBoolExprs) {
        index.insert(unsatCore)
    }

    override suspend fun check(exprs: KBoolExprs): Boolean {
        return index.getCandidates().firstOrNull { candidate ->
            tester.test(candidate, exprs)
        } != null
    }

}