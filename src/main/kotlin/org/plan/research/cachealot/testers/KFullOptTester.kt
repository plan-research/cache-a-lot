package org.plan.research.cachealot.testers

import io.ksmt.KContext
import io.ksmt.decl.KDecl
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.testers.substitution.*
import org.plan.research.cachealot.toCachedSequence

class KFullOptTester(private val ctx: KContext) : KUnsatTester {

    private data class OptState(
        val inner: MapSubstitutionMonadState = MapSubstitutionMonadState(),
        val variables: PersistentMap<KDecl<*>, PersistentSet<KDecl<*>>> = persistentHashMapOf(),
        private val ignore: PersistentSet<KDecl<*>> = persistentHashSetOf(),
    ) : SubstitutionMonadState<OptState> {
        override fun hasSubstitutionFor(origin: KDecl<*>): Boolean =
            inner.hasSubstitutionFor(origin)

        override fun checkSubstitution(origin: KDecl<*>, target: KDecl<*>): Boolean =
            inner.checkSubstitution(origin, target)

        override fun extract(origins: Collection<KDecl<*>>): OptState =
            copy(inner.extract(origins), variables.extractAll(origins))

        override fun removeAll(origins: Collection<KDecl<*>>): OptState =
            copy(inner.removeAll(origins), variables.removeAll(origins), ignore.addAll(origins))

        override fun substitute(origin: KDecl<*>, target: KDecl<*>): OptState =
            copy(
                inner.substitute(origin, target),
                if (origin in ignore) variables
                else variables.builder().apply {
                    compute(origin) { _, old -> old?.add(target) ?: persistentHashSetOf(target) }
                }.build()
            )

        override fun merge(other: OptState): OptState =
            copy(inner.merge(other.inner), variables.putAll(other.variables), other.ignore)
    }

    override suspend fun test(unsatCore: KBoolExprs, exprs: KBoolExprs): Boolean {
        val possibleTargets = hashMapOf<KDecl<*>, MutableSet<KDecl<*>>>()
        val states = unsatCore.map { core ->
            var vars = persistentHashMapOf<KDecl<*>, PersistentSet<KDecl<*>>>()
            val result = exprs.mapNotNull {
                OptState(variables = vars).wrap().run {
                    core eq it
                    vars = state.variables
                }?.monad?.state?.inner?.map
            }.takeIf { it.isNotEmpty() } ?: return false

            vars.forEach { (origin, targets) ->
                when (val oldTargets = possibleTargets[origin]) {
                    null -> possibleTargets[origin] = targets.toMutableSet()
                    else -> {
                        oldTargets.retainAll(targets)
                        if (oldTargets.isEmpty()) return false
                    }
                }
            }

            result
        }

        val result = states.fold(sequenceOf(persistentHashMapOf<KDecl<*>, KDecl<*>>())) { acc, maps ->
            if (acc.firstOrNull() == null) return false
            val filteredMap = maps.filter { it.all { (origin, target) -> target in possibleTargets[origin]!! } }
            acc.flatMap { map -> filteredMap.mapNotNull { map.join(it) } }.iterator().toCachedSequence()
        }

        return result.firstOrNull() != null
    }
}