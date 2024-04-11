package org.plan.research.cachealot

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import kotlinx.coroutines.flow.firstOrNull
import org.plan.research.cachealot.index.KIndex
import org.plan.research.cachealot.testers.KUnsatTester

class KUnsatChecker<K>(
    private val keyComputer: (KExpr<KBoolSort>) -> K,
    private val index: KIndex<K, KExpr<KBoolSort>>,
    private val tester: KUnsatTester,
) {

    suspend fun addUnsatCore(unsatCore: KExpr<KBoolSort>) {
        val key = keyComputer(unsatCore)
        index.insert(key, unsatCore)
    }

    suspend fun check(formula: KExpr<KBoolSort>): Boolean {
        val key = keyComputer(formula)
        index.getCandidates(key).firstOrNull { candidate ->
            tester.test(candidate, formula)
        } ?: return false
        return true
    }

}