package org.plan.research.cachealot.testers

import io.ksmt.KContext
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.testers.substitution.MapSubstitutionMonadState
import org.plan.research.cachealot.testers.substitution.SubstitutionMonad
import org.plan.research.cachealot.testers.substitution.SubstitutionMonadHolder
import org.plan.research.cachealot.testers.substitution.wrap
import org.plan.research.cachealot.toCachedSequence

class KFullOptTester(private val ctx: KContext) : KUnsatTester {
    private fun createMonad() = MapSubstitutionMonadState().wrap()

    override suspend fun test(unsatCore: KBoolExprs, exprs: KBoolExprs): Boolean {
        val substitutions = unsatCore.fold(sequenceOf(createMonad())) { monads, core ->
            if (monads.firstOrNull() == null) return false
            val newMonads = exprs.mapNotNull { createMonad().run { core eq it } }
            monads.flatMap { old -> newMonads.mapNotNull { old.merge(it) } }.iterator().toCachedSequence()
        }
        return substitutions.firstOrNull() != null
    }
}