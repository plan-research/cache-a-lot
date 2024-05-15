package org.plan.research.cachealot.checker

import org.plan.research.cachealot.KFormulaeFlatIndex
import org.plan.research.cachealot.KFormulaeIndex
import org.plan.research.cachealot.checker.impl.KEmptyUnsatChecker
import org.plan.research.cachealot.checker.impl.KFlatUnsatCheckerImpl
import org.plan.research.cachealot.checker.impl.KUnsatCheckerImpl
import org.plan.research.cachealot.index.KKeyComputer
import org.plan.research.cachealot.testers.KUnsatTester

object KUnsatCheckerFactory {

    fun create(): KUnsatChecker = KEmptyUnsatChecker

    fun create(
        tester: KUnsatTester,
        index: KFormulaeFlatIndex
    ): KUnsatChecker = KFlatUnsatCheckerImpl(index, tester)

    fun <K> create(
        tester: KUnsatTester,
        index: KFormulaeIndex<K>,
        keyComputer: KKeyComputer<K>,
    ): KUnsatChecker = KUnsatCheckerImpl(keyComputer, index, tester)
}