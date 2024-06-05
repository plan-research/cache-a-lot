package org.plan.research.cachealot.cache.decorators

import org.plan.research.cachealot.cache.KUnsatCache

abstract class KUnsatCacheDecorator : KUnsatCache {
    protected abstract val inner: KUnsatCache
}
