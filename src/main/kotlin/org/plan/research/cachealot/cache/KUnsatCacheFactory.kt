package org.plan.research.cachealot.cache

import org.plan.research.cachealot.cache.impl.KEmptyUnsatCache
import org.plan.research.cachealot.cache.impl.KFlatUnsatCacheImpl
import org.plan.research.cachealot.cache.impl.KUnsatCacheImpl
import org.plan.research.cachealot.context.KGlobalCacheContext
import org.plan.research.cachealot.context.KLocalCacheContext
import org.plan.research.cachealot.index.KIndex
import org.plan.research.cachealot.index.flat.KFlatIndex
import org.plan.research.cachealot.testers.KUnsatTester

object KUnsatCacheFactory {

    fun create(): KUnsatCache = KEmptyUnsatCache

    fun <K, L : KLocalCacheContext, G : KGlobalCacheContext<L>> create(
        context: G,
        tester: KUnsatTester<L>,
        index: KIndex<K, L, G>,
    ): KUnsatCache = (index as? KFlatIndex<L, G>)?.let { KFlatUnsatCacheImpl(context, index, tester) }
        ?: KUnsatCacheImpl(context, index, tester)
}
