package org.plan.research.cachealot.testers

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort

interface KUnsatTester {
    suspend fun test(unsatCore: KExpr<KBoolSort>, formula: KExpr<KBoolSort>): Boolean
}
