package org.plan.research.cachealot.cache.decorators.filters

import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.cache.KUnsatCache

class KUnsatCoreFilterBySize(val maxSize: Int, inner: KUnsatCache) : KUnsatCacheFilter(inner) {
    override suspend fun unsatCoreFilter(unsatCore: KBoolExprs): Boolean =
        unsatCore.size <= maxSize

    override suspend fun formulaeFilter(exprs: KBoolExprs): Boolean = true
}

fun KUnsatCache.withUnsatCoreMaxSize(maxSize: Int): KUnsatCache =
    KUnsatCoreFilterBySize(maxSize, this)
