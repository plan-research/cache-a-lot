package org.plan.research.cachealot.checker.decorators

import org.plan.research.cachealot.checker.KUnsatChecker

abstract class KUnsatCheckerDecorator : KUnsatChecker {
    protected abstract val inner: KUnsatChecker
}
