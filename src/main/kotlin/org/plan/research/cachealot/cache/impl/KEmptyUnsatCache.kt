package org.plan.research.cachealot.cache.impl

import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.cache.KUnsatCache

object KEmptyUnsatCache : KUnsatCache {
    override suspend fun addUnsatCore(unsatCore: KBoolExprs) {}
    override suspend fun check(exprs: KBoolExprs): Boolean = false
}