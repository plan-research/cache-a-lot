package org.plan.research.cachealot.checker

import org.plan.research.cachealot.KBoolExprs

object KEmptyUnsatChecker : KUnsatChecker {
    override suspend fun addUnsatCore(unsatCore: KBoolExprs) {}
    override suspend fun check(exprs: KBoolExprs): Boolean = false
}