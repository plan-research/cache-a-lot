package org.plan.research.cachealot.cache

import org.plan.research.cachealot.KFormulaeFlatIndex
import org.plan.research.cachealot.KFormulaeIndex
import org.plan.research.cachealot.cache.impl.KEmptyUnsatCache
import org.plan.research.cachealot.cache.impl.KFlatUnsatCacheImpl
import org.plan.research.cachealot.cache.impl.KUnsatCacheImpl
import org.plan.research.cachealot.index.KKeyComputer
import org.plan.research.cachealot.testers.KUnsatTester

object KUnsatCacheFactory {

    fun create(): KUnsatCache = KEmptyUnsatCache

    fun create(
        tester: KUnsatTester,
        index: KFormulaeFlatIndex
    ): KUnsatCache = KFlatUnsatCacheImpl(index, tester)

    fun <K> create(
        tester: KUnsatTester,
        index: KFormulaeIndex<K>,
        keyComputer: KKeyComputer<K>,
    ): KUnsatCache = KUnsatCacheImpl(keyComputer, index, tester)

    fun <K> create(
        tester: KUnsatTester,
        index: KFormulaeIndex<K>,
        coreKeyComputer: KKeyComputer<K>,
        exprKeyComputer: KKeyComputer<K>,
    ): KUnsatCache = KUnsatCacheImpl(coreKeyComputer, exprKeyComputer, index, tester)
}