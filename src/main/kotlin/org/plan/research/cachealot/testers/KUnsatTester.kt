package org.plan.research.cachealot.testers

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import org.plan.research.cachealot.KBoolExprs

interface KUnsatTester {
    suspend fun test(unsatCore: KBoolExprs, exprs: KBoolExprs): Boolean
}
