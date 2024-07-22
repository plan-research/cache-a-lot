package org.plan.research.cachealot.testers

import io.ksmt.KContext
import io.ksmt.decl.KDecl
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.hash.KCacheContext
import org.plan.research.cachealot.testers.substitution.*
import org.plan.research.cachealot.testers.substitution.impl.MapSubstitutionMonadState
import java.util.*

class KFullOptTester(
    private val ctx: KContext,
    private val cacheContext: KCacheContext,
) : KUnsatTester {

    private data class OptState(
        val inner: MapSubstitutionMonadState = MapSubstitutionMonadState(),
        val variables: PersistentMap<KDecl<*>, PersistentSet<KDecl<*>>> = persistentHashMapOf(),
        private val ignore: PersistentSet<KDecl<*>> = persistentHashSetOf(),
        private val possibleTargets: Map<KDecl<*>, Set<*>> = emptyMap(),
    ) : SubstitutionMonadState<OptState> {
        override fun hasSubstitutionFor(origin: KDecl<*>): Boolean =
            inner.hasSubstitutionFor(origin)

        override fun checkSubstitution(origin: KDecl<*>, target: KDecl<*>): Boolean =
            inner.checkSubstitution(origin, target)

        override fun extract(origins: Collection<KDecl<*>>): OptState =
            copy(inner.extract(origins), variables.extractAll(origins))

        override fun removeAll(origins: Collection<KDecl<*>>): OptState =
            copy(inner.removeAll(origins), variables.removeAll(origins), ignore.addAll(origins))

        override fun substitute(origin: KDecl<*>, target: KDecl<*>): OptState {
            val ignored = origin in ignore
            if (!ignored) {
                possibleTargets[origin]?.let {
                    substitutionAssert { target in it }
                }
            }
            return copy(
                inner.substitute(origin, target),
                if (ignored) variables
                else variables.builder().apply {
                    compute(origin) { _, old -> old?.add(target) ?: persistentHashSetOf(target) }
                }.build()
            )
        }

        override fun merge(other: OptState): OptState =
            copy(inner.merge(other.inner), variables.putAll(other.variables), other.ignore)
    }

    private suspend fun buildStates(
        unsatCore: KBoolExprs,
        exprs: KBoolExprs,
        possibleTargets: MutableMap<KDecl<*>, Set<KDecl<*>>>
    ): List<List<PersistentMap<KDecl<*>, KDecl<*>>>>? = with(cacheContext) {
        val hash2Exprs = getOrComputeHash2Exprs(exprs)

        unsatCore.map { core ->
            val coreHash = coreHasher.computeHash(core)
            hash2Exprs[coreHash]?.let { core to it } ?: return null
        }.map { (core, filteredExprs) ->
            var vars = persistentHashMapOf<KDecl<*>, PersistentSet<KDecl<*>>>()
            val result = filteredExprs.mapNotNull {
                OptState(
                    variables = vars,
                    possibleTargets = possibleTargets,
                ).wrap().withHash(coreHasher, exprHasher).wrap().run {
                    core eq it
                    vars = state.variables
                }?.monad?.state?.inner?.map
            }.takeIf { it.isNotEmpty() } ?: return null

            possibleTargets.putAll(vars)

            result
        }
    }

    private suspend fun isJoinedStatesNotEmpty(
        states: List<List<PersistentMap<KDecl<*>, KDecl<*>>>>,
        possibleTargets: MutableMap<KDecl<*>, Set<KDecl<*>>>
    ): Boolean {
        // ---------------------------------------------------- Cut off version
//        val result = states.fold(listOf(persistentHashMapOf<KDecl<*>, KDecl<*>>())) { acc, maps ->
//            if (acc.isEmpty()) return false
//            val filteredMap = maps.filter { it.all { (origin, target) -> target in possibleTargets[origin]!! } }
//            acc.flatMap { map -> filteredMap.mapNotNull { map.join(it) } }
//                .randomSequence() // memory optimization (accuracy decrease by 20% but 30G -> 5G)
//                .take(MAX_STATES_SIZE)
//                .toList()
//        }
//
//        return result.isNotEmpty()

        // ---------------------------------------------------- Lazy (initial) version

        //        val result = states.fold(sequenceOf(persistentHashMapOf<KDecl<*>, KDecl<*>>())) { acc, maps ->
//            if (acc.firstOrNull() == null) return false
//            val filteredMap = maps.filter { it.all { (origin, target) -> target in possibleTargets[origin]!! } }
//            acc.flatMap { map -> filteredMap.mapNotNull { map.join(it) } }.iterator().toCachedSequence()
//        }
//
//        return result.firstOrNull() != null

        // ---------------------------------------------------- True lazy version

//        val filteredStates = lazyList(mutableListOf()) { i ->
//            states[i].filter { it.all { (origin, target) -> target in possibleTargets[origin]!! } }.also {
//                statLogger.info { "${i}-th state size: ${it.size}" }
//            }
//        }
//
//        val index = MutableList(states.size) { 0 }
//        var currentStateIndex = 0
//        val currentStates = mutableListOf(persistentHashMapOf<KDecl<*>, KDecl<*>>())
//
//        while (currentStateIndex < states.size) {
//            val prevState = currentStates.last()
//            val state = filteredStates[currentStateIndex]
//            if (state.size == 0) return false
//
//            var i = index[currentStateIndex]
//            var newState: PersistentMap<KDecl<*>, KDecl<*>>? = null
//            while (i < state.size) {
//                newState = prevState.join(state[i++]) ?: continue
//                break
//            }
//
//            if (newState == null) {
//                if (currentStateIndex == 0) return false
//                index[currentStateIndex--] = 0
//                currentStates.removeLast()
//            } else {
//                index[currentStateIndex++] = i
//                currentStates.add(newState)
//            }
//        }
//        return true

        // ---------------------------------------------------- Prioritisation version
        val filteredStates = states.map {
            it.filter { it.all { (origin, target) -> target in possibleTargets[origin]!! } }.also {
                if (it.isEmpty()) return false
            }
        }

        val statesQueue =
            PriorityQueue<List<PersistentMap<KDecl<*>, KDecl<*>>>>(filteredStates.size, compareBy { it.size })

        statesQueue.addAll(filteredStates)

        while (statesQueue.size > 1) {
            var state1 = statesQueue.poll()
            var state2 = statesQueue.poll()
            if (state1.first().size < state2.first().size) {
                state1.let {
                    state1 = state2
                    state2 = it
                }
            }
            val joinedState = state1.flatMap { map -> state2.mapNotNull { map.join(it) } }
            if (joinedState.isEmpty()) return false
            statesQueue.add(joinedState)
        }
        return statesQueue.poll().isNotEmpty()
    }

    override suspend fun test(unsatCore: KBoolExprs, exprs: KBoolExprs): Boolean {
        val possibleTargets = hashMapOf<KDecl<*>, Set<KDecl<*>>>()
        val states = buildStates(unsatCore, exprs, possibleTargets) ?: return false
        return isJoinedStatesNotEmpty(states, possibleTargets)
    }
}