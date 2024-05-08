package org.plan.research.cachealot.index

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import org.plan.research.cachealot.KBoolExprs

interface KKeyComputer<K> {
    operator suspend fun invoke(exprs: KBoolExprs): K
}
