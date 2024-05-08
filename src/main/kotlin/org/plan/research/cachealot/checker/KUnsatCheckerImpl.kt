package org.plan.research.cachealot.checker

import kotlinx.coroutines.flow.firstOrNull
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.index.KIndex
import org.plan.research.cachealot.testers.KUnsatTester
import org.plan.research.cachealot.index.KKeyComputer

class KUnsatCheckerImpl<K>(
    private val keyComputer: KKeyComputer<K>,
    private val index: KIndex<K, KBoolExprs>,
    private val tester: KUnsatTester,
) : KUnsatChecker {

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