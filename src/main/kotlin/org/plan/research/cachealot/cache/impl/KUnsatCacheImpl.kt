package org.plan.research.cachealot.cache.impl

import kotlinx.coroutines.flow.firstOrNull
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.KFormulaeIndex
import org.plan.research.cachealot.cache.KUnsatCache
import org.plan.research.cachealot.index.KKeyComputer
import org.plan.research.cachealot.testers.KUnsatTester

class KUnsatCacheImpl<K>(
    private val coreKeyComputer: KKeyComputer<K>,
    private val exprKeyComputer: KKeyComputer<K>,
    private val index: KFormulaeIndex<K>,
    private val tester: KUnsatTester,
) : KUnsatCache {

    constructor(
        keyComputer: KKeyComputer<K>,
        index: KFormulaeIndex<K>,
        tester: KUnsatTester,
    ) : this(keyComputer, keyComputer, index, tester)

    override suspend fun addUnsatCore(unsatCore: KBoolExprs) {
        val key = coreKeyComputer(unsatCore)
        index.insert(key, unsatCore)
    }

    override suspend fun check(exprs: KBoolExprs): Boolean {
        val key = exprKeyComputer(exprs)
        return index.getCandidates(key).firstOrNull { candidate ->
            tester.test(candidate, exprs)
        } != null
    }

}