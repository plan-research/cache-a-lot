package org.plan.research.cachealot.index

import org.plan.research.cachealot.KBoolExprs

interface KKeyComputer<K> {
    suspend operator fun invoke(exprs: KBoolExprs): K
}

object KEmptyComputer : KKeyComputer<Unit> {
    override suspend fun invoke(exprs: KBoolExprs) {}
}
