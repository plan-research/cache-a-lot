package org.plan.research.cachealot.testers

import io.ksmt.KContext
import io.ksmt.decl.KDecl
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentHashSetOf
import org.plan.research.cachealot.KBoolExprs
import org.plan.research.cachealot.checkActive
import org.plan.research.cachealot.hash.KCacheContext
import org.plan.research.cachealot.testers.substitution.*
import org.plan.research.cachealot.testers.substitution.impl.MapSubstitutionMonadState
import kotlin.math.max

class KFullOptTester(
    private val ctx: KContext,
    private val cacheContext: KCacheContext,
) : KUnsatTester {

    private class DeclScale {
        val decls = mutableListOf<KDecl<*>>()
        val declToIndex = hashMapOf<KDecl<*>, Int>()

        fun index(decl: KDecl<*>): Int =
            declToIndex.computeIfAbsent(decl) {
//                val index = decls.indexOfFirst { it structEquals decl }
//                if (index == -1) {
                decls += decl
                decls.lastIndex
//                } else {
//                    index
//                }
            }
    }

    private class RenameData(val scale: DeclScale) {
        val names = mutableListOf<KDecl<*>>()
        val values = mutableListOf<List<KDecl<*>>>()

        val size: Int get() = values.size

        var targetKeys: Set<KDecl<*>> = emptySet()
        val keyIndicies: List<Int> by lazy {
            names.mapIndexedNotNull { index, decl -> index.takeIf { decl in targetKeys } }
        }
        val sortKeyValues by lazy {
            values.map { value -> keyIndicies.map { scale.index(value[it]) } }
        }
        val sortValuesIndecies by lazy {
            (0 until size).sortedWith(compareBy(*Array(keyIndicies.size) { index ->
                { sortKeyValues[it][index] }
            }))
        }

        fun filter(possibleTargets: MutableMap<KDecl<*>, Set<KDecl<*>>>): RenameData {
            values.removeIf { names.withIndex().any { (index, origin) -> it[index] !in possibleTargets[origin]!! } }
            return this
        }

        private suspend fun binSearch(targetIndex: List<Int>, lowerBound: Boolean): Int {
            var left = -1
            var right = sortValuesIndecies.size
            while (right - left > 1) {
                checkActive()
                val mid = (right + left) / 2
                val index = sortKeyValues[sortValuesIndecies[mid]]
                var isLess = !lowerBound
                for (i in index.indices) {
                    checkActive()
                    if (index[i] < targetIndex[i]) {
                        isLess = true
                        break
                    } else if (index[i] > targetIndex[i]) {
                        isLess = false
                        break
                    }
                }
                if (isLess) {
                    left = mid
                } else {
                    right = mid
                }
            }
            return right
        }

        suspend fun find(getRename: (KDecl<*>) -> KDecl<*>): Iterator<List<KDecl<*>>> {
            if (keyIndicies.isEmpty()) return values.iterator()

            val targetIndex = keyIndicies.map {
                checkActive()
                scale.index(getRename(names[it]))
            }
            val lowerBound = binSearch(targetIndex, true)
            val upperBound = binSearch(targetIndex, false)
            return iterator {
                for (i in lowerBound until upperBound) {
                    yield(values[sortValuesIndecies[i]])
                }
            }
        }

        fun updateWith(map: PersistentMap<KDecl<*>, KDecl<*>>) {
            val data = mutableListOf<KDecl<*>>()

            if (names.isEmpty()) {
                map.forEach { (key, value) ->
                    names += key
                    data += value
                }
            } else {
                names.forEach { key ->
                    data += map[key]!!
                }
            }
            values += data
        }
    }

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
    ): List<RenameData>? = with(cacheContext) {
        val scale = DeclScale()
        val hash2Exprs = getOrComputeHash2Exprs(exprs)

        checkActive()

        unsatCore.map { core ->
            checkActive()
            val coreHash = coreHasher.computeHash(core)
            hash2Exprs[coreHash]?.let { core to it } ?: return null
        }.map { (core, filteredExprs) ->
            checkActive()
            var vars = persistentHashMapOf<KDecl<*>, PersistentSet<KDecl<*>>>()
            val result = RenameData(scale)
            filteredExprs.forEach {
                checkActive()
                OptState(
                    variables = vars,
                    possibleTargets = possibleTargets,
                ).wrap().withHash(coreHasher, exprHasher).wrap().run {
                    core eq it
                    vars = state.variables
                }?.monad?.state?.inner?.map?.let {
                    result.updateWith(it)
                }
            }

            if (result.size == 0) return null

            possibleTargets.putAll(vars)
            result
        }
    }

    private suspend fun isJoinedStatesNotEmpty(
        states: List<RenameData>,
        possibleTargets: MutableMap<KDecl<*>, Set<KDecl<*>>>
    ): Boolean {
        states.forEach {
            if (it.filter(possibleTargets).size == 0) return false
        }

        val sortedStates = states.sortedBy { it.size }

        var firstKeyStateIndex = persistentHashMapOf<KDecl<*>, Int>()
        sortedStates.forEachIndexed { index, it ->
            it.targetKeys = firstKeyStateIndex.keys
            firstKeyStateIndex = firstKeyStateIndex.builder().apply {
                it.names.forEach { putIfAbsent(it, index) }
            }.build()
        }

        checkActive()

        val index = mutableListOf<Iterator<List<KDecl<*>>>>()
        var currentStateIndex = 0
        val currentState = hashMapOf<KDecl<*>, KDecl<*>>()
        while (currentStateIndex < sortedStates.size) {
            checkActive()

            val state = sortedStates[currentStateIndex]
            var isFirst = false
            val iter = index.getOrElse(currentStateIndex) {
                isFirst = true
                state.find { currentState[it]!! }.also {
                    index += it
                }
            }
            if (iter.hasNext()) {
                currentState.putAll(state.names.asSequence().zip(iter.next().asSequence()))
                currentStateIndex++
            } else {
                if (currentStateIndex == 0) return false
                currentStateIndex = if (isFirst) {
                    state.keyIndicies.fold(0) { acc, i ->
                        max(acc, firstKeyStateIndex[state.names[i]]!!)
                    }
                } else {
                    currentStateIndex - 1
                }
                while (currentStateIndex >= index.size) {
                    index.removeLast()
                }
            }
        }
        return true
    }

    override suspend fun test(unsatCore: KBoolExprs, exprs: KBoolExprs): Boolean {
        val possibleTargets = hashMapOf<KDecl<*>, Set<KDecl<*>>>()
        val states = buildStates(unsatCore, exprs, possibleTargets) ?: return false
        return isJoinedStatesNotEmpty(states.filter { it.names.size > 1 }, possibleTargets)
    }
}