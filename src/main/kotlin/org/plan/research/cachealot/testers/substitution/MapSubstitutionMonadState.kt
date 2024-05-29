package org.plan.research.cachealot.testers.substitution

import io.ksmt.decl.KDecl
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import org.plan.research.cachealot.structEquals

data class MapSubstitutionMonadState(
    private val map: PersistentMap<KDecl<*>, KDecl<*>> = persistentMapOf()
) : SubstitutionMonadState<MapSubstitutionMonadState> {
    override fun checkSubstitution(origin: KDecl<*>, target: KDecl<*>): Boolean =
        map[origin] structEquals target

    override fun merge(other: MapSubstitutionMonadState): MapSubstitutionMonadState =
        copy(map.merge(other.map))

    override fun remove(origin: KDecl<*>): MapSubstitutionMonadState =
        copy(map.remove(origin))

    override fun removeAll(origins: Collection<KDecl<*>>): MapSubstitutionMonadState =
        copy(origins.fold(map) { acc, decl -> acc.remove(decl) })

    override fun substitute(origin: KDecl<*>, target: KDecl<*>): MapSubstitutionMonadState =
        copy(map.put(origin, target))

    override fun hasSubstitutionFor(origin: KDecl<*>): Boolean =
        origin in map
}