package org.plan.research.cachealot.checker.preprocessors

import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.checker.KUnsatChecker

abstract class KUnsatCheckerPreprocessor(private val checker: KUnsatChecker) : KUnsatChecker {

    open suspend fun preprocess(exprs: KBoolExprs): KBoolExprs = exprs
    open suspend fun unsatCorePreprocess(unsatCore: KBoolExprs): KBoolExprs = preprocess(unsatCore)
    open suspend fun checkPreprocess(exprs: KBoolExprs): KBoolExprs = preprocess(exprs)

    final override suspend fun addUnsatCore(unsatCore: KBoolExprs) {
        checker.addUnsatCore(unsatCorePreprocess(unsatCore))
    }

    final override suspend fun check(exprs: KBoolExprs): Boolean =
        checker.check(checkPreprocess(exprs))
}