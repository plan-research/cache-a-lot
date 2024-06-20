package org.plan.research.cachealot.cache

import org.plan.research.cachealot.cache.impl.KEmptyUnsatCache
import org.plan.research.cachealot.cache.impl.KFlatUnsatCacheImpl
import org.plan.research.cachealot.cache.impl.KUnsatCacheImpl
import org.plan.research.cachealot.index.KIndex
import org.plan.research.cachealot.index.flat.KFlatIndex
import org.plan.research.cachealot.testers.KUnsatTester

object KUnsatCacheFactory {

    fun create(): KUnsatCache = KEmptyUnsatCache

    fun create(
        tester: KUnsatTester,
        index: KFlatIndex
    ): KUnsatCache = KFlatUnsatCacheImpl(index, tester)

    fun <K> create(
        tester: KUnsatTester,
        index: KIndex<K>,
    ): KUnsatCache = KUnsatCacheImpl(index, tester)
}