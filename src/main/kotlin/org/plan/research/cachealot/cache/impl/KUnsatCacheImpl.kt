package org.plan.research.cachealot.cache.impl

import kotlinx.coroutines.flow.firstOrNull
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.KFormulaeIndex
import org.plan.research.cachealot.cache.KUnsatCache
import org.plan.research.cachealot.index.KKeyComputer
import org.plan.research.cachealot.testers.KUnsatTester

class KUnsatCacheImpl<K>(
    private val keyComputer: KKeyComputer<K>,
    private val index: KFormulaeIndex<K>,
    private val tester: KUnsatTester,
) : KUnsatCache {

    override suspend fun addUnsatCore(unsatCore: KBoolExprs) {
        val key = keyComputer(unsatCore)
        index.insert(key, unsatCore)
    }

    override suspend fun check(exprs: KBoolExprs): Boolean {
        val key = keyComputer(exprs)
        return index.getCandidates(key).firstOrNull { candidate ->
            tester.test(candidate, exprs)
        } != null
    }

}