package org.plan.research.cachealot.cache.decorators.preprocessors

import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.cache.KUnsatCache
import org.plan.research.cachealot.cache.decorators.KUnsatCacheDecorator

abstract class KUnsatCachePreprocessor(override val inner: KUnsatCache) : KUnsatCacheDecorator() {

    protected abstract suspend fun unsatCorePreprocess(unsatCore: KBoolExprs): KBoolExprs
    protected abstract suspend fun formulaePreprocess(exprs: KBoolExprs): KBoolExprs

    final override suspend fun addUnsatCore(unsatCore: KBoolExprs) {
        inner.addUnsatCore(unsatCorePreprocess(unsatCore))
    }

    final override suspend fun check(exprs: KBoolExprs): Boolean =
        inner.check(formulaePreprocess(exprs))
}