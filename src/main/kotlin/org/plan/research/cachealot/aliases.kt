package org.plan.research.cachealot

import io.ksmt.expr.KExpr
import io.ksmt.sort.KBoolSort
import org.plan.research.cachealot.index.KFlatIndex
import org.plan.research.cachealot.index.KIndex

typealias KBoolExprs = List<KExpr<KBoolSort>>
typealias KFormulaeIndex<K> = KIndex<K, KBoolExprs>
typealias KFormulaeFlatIndex = KFlatIndex<KBoolExprs>
