package org.plan.research.cachealot.testers

import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.context.KLocalCacheContext

interface KUnsatTester<in C: KLocalCacheContext> {
    suspend fun test(ctx: C, unsatCore: KBoolExprs, exprs: KBoolExprs): Boolean
}
