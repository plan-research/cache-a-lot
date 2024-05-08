package org.plan.research.cachealot.index.composite

import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.index.KKeyComputer

class KKeyComputerComposite<F, S>(
    private val first: KKeyComputer<F>,
    private val second: KKeyComputer<S>,
) : KKeyComputer<Pair<F, S>> {
    override suspend fun invoke(exprs: KBoolExprs): Pair<F, S> =
        first(exprs) to second(exprs)
}
