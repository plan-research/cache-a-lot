package org.plan.research.cachealot.cache

import org.plan.research.cachealot.KBoolExprs

interface KUnsatCache {
    suspend fun addUnsatCore(unsatCore: KBoolExprs)
    suspend fun check(exprs: KBoolExprs): Boolean
}
