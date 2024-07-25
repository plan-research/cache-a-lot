package org.plan.research.cachealot.cache.impl

import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.firstOrNull
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.cache.KUnsatCache
import org.plan.research.cachealot.index.KIndex
import org.plan.research.cachealot.index.KKeyComputer
import org.plan.research.cachealot.testers.KUnsatTester

class KUnsatCacheImpl<K>(
    private val index: KIndex<K>,
    private val tester: KUnsatTester,
) : KUnsatCache {

    private val coreKeyComputer: KKeyComputer<K> = index.createCoreKeyComputer()
    private val exprKeyComputer: KKeyComputer<K> = index.createExprKeyComputer()

    override suspend fun addUnsatCore(unsatCore: KBoolExprs) {
        val key = coreKeyComputer(unsatCore)
        index.insert(key, unsatCore)
    }

    override suspend fun check(exprs: KBoolExprs): Boolean {
        val key = exprKeyComputer(exprs)
        return index.getCandidates(key).cancellable().firstOrNull { candidate ->
            tester.test(candidate, exprs)
        } != null
    }

}