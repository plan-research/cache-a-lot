package org.plan.research.cachealot.testers

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import org.plan.research.cachealot.KBoolExprs

class KSimpleTester : KUnsatTester {

    private fun KExpr<KBoolSort>.str(): String = buildString { print(this) }

    override suspend fun test(unsatCore: KBoolExprs, exprs: KBoolExprs): Boolean {
        val exprsStrings = exprs.map { it.str() }
        return unsatCore.all { exprsStrings.contains(it.str()) }
    }
}
