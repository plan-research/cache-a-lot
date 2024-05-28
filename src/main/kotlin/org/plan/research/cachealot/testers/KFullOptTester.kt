package org.plan.research.cachealot.testers

import io.ksmt.KContext
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.toCachedSequence

class KFullOptTester(private val ctx: KContext) : KUnsatTester {
    override suspend fun test(unsatCore: KBoolExprs, exprs: KBoolExprs): Boolean {
        val substitutions = unsatCore.fold(sequenceOf(SubstitutionMonadHolder())) { monads, core ->
            if (monads.firstOrNull() == null) return false
            val newMonads = exprs.mapNotNull { SubstitutionMonadHolder().run { core eq it } }
            monads.flatMap { old -> newMonads.mapNotNull { old.merge(it) } }.iterator().toCachedSequence()
        }
        return substitutions.firstOrNull() != null
    }
}