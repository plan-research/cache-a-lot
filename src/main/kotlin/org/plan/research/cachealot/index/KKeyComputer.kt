package org.plan.research.cachealot.index

import org.plan.research.cachealot.KBoolExprs

interface KKeyComputer<K> {
    suspend operator fun invoke(exprs: KBoolExprs): K
}
