package org.plan.research.cachealot.checker.impl

import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.checker.KUnsatChecker

object KEmptyUnsatChecker : KUnsatChecker {
    override suspend fun addUnsatCore(unsatCore: KBoolExprs) {}
    override suspend fun check(exprs: KBoolExprs): Boolean = false
}