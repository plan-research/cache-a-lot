package org.plan.research.cachealot.cache.decorators.filters

import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.cache.KUnsatCache
import org.plan.research.cachealot.cache.decorators.KUnsatCacheDecorator

abstract class KUnsatCacheFilter(override val inner: KUnsatCache) : KUnsatCacheDecorator() {

    protected abstract suspend fun unsatCoreFilter(unsatCore: KBoolExprs): Boolean
    protected abstract suspend fun formulaeFilter(exprs: KBoolExprs): Boolean

    final override suspend fun addUnsatCore(unsatCore: KBoolExprs) {
        if (unsatCoreFilter(unsatCore)) {
            inner.addUnsatCore(unsatCore)
        }
    }

    final override suspend fun check(exprs: KBoolExprs): Boolean =
        formulaeFilter(exprs) && inner.check(exprs)

}