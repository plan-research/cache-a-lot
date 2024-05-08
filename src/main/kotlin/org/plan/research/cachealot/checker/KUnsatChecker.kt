package org.plan.research.cachealot.checker

import org.plan.research.cachealot.KBoolExprs

interface KUnsatChecker {
    suspend fun addUnsatCore(unsatCore: KBoolExprs)
    suspend fun check(exprs: KBoolExprs): Boolean
}
