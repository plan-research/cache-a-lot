package org.plan.research.cachealot.testers

import org.plan.research.cachealot.KBoolExprs

interface KUnsatTester {
    suspend fun test(unsatCore: KBoolExprs, exprs: KBoolExprs): Boolean
}
