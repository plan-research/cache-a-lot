package org.plan.research.cachealot.checker

import kotlinx.coroutines.flow.firstOrNull
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.KFormulaeFlatIndex
import org.plan.research.cachealot.testers.KUnsatTester

class KFlatUnsatCheckerImpl(
    private val index: KFormulaeFlatIndex,
    private val tester: KUnsatTester,
) : KUnsatChecker {

    override suspend fun addUnsatCore(unsatCore: KBoolExprs) {
        index.insert(unsatCore)
    }

    override suspend fun check(exprs: KBoolExprs): Boolean {
        return index.getCandidates().firstOrNull { candidate ->
            tester.test(candidate, exprs)
        } != null
    }

}