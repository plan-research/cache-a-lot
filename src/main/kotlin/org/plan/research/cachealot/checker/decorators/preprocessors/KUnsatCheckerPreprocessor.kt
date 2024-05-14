package org.plan.research.cachealot.checker.decorators.preprocessors

import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.checker.KUnsatChecker
import org.plan.research.cachealot.checker.decorators.KUnsatCheckerDecorator

abstract class KUnsatCheckerPreprocessor(override val inner: KUnsatChecker) : KUnsatCheckerDecorator() {

    protected open suspend fun preprocess(exprs: KBoolExprs): KBoolExprs = exprs
    protected open suspend fun unsatCorePreprocess(unsatCore: KBoolExprs): KBoolExprs = preprocess(unsatCore)
    protected open suspend fun checkPreprocess(exprs: KBoolExprs): KBoolExprs = preprocess(exprs)

    final override suspend fun addUnsatCore(unsatCore: KBoolExprs) {
        inner.addUnsatCore(unsatCorePreprocess(unsatCore))
    }

    final override suspend fun check(exprs: KBoolExprs): Boolean =
        inner.check(checkPreprocess(exprs))
}