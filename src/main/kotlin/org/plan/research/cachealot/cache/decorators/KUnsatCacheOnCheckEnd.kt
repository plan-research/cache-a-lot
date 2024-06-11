package org.plan.research.cachealot.cache.decorators

import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.cache.KUnsatCache

class KUnsatCacheOnCheckEnd(override val inner: KUnsatCache, private val onCheckEnd: () -> Unit) : KUnsatCacheDecorator() {
    override suspend fun addUnsatCore(unsatCore: KBoolExprs) {
        inner.addUnsatCore(unsatCore)
    }

    override suspend fun check(exprs: KBoolExprs): Boolean =
        inner.check(exprs).also { onCheckEnd() }

}

fun KUnsatCache.onCheckEnd(block: () -> Unit): KUnsatCache =
    KUnsatCacheOnCheckEnd(this, block)
