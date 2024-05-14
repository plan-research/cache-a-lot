package org.plan.research.cachealot.checker.decorators.filters

import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.checker.KUnsatChecker
import org.plan.research.cachealot.checker.decorators.KUnsatCheckerDecorator

abstract class KUnsatCheckerFilter(override val inner: KUnsatChecker) : KUnsatCheckerDecorator() {

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