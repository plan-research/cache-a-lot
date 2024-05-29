package org.plan.research.cachealot.testers.substitution

import io.ksmt.decl.KDecl
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.plan.research.cachealot.structEquals

data class MapSubstitutionMonadState(
    val map: PersistentMap<KDecl<*>, KDecl<*>> = persistentHashMapOf()
) : SubstitutionMonadState<MapSubstitutionMonadState> {
    override fun checkSubstitution(origin: KDecl<*>, target: KDecl<*>): Boolean =
        map[origin] structEquals target

    override fun extract(origins: Collection<KDecl<*>>): MapSubstitutionMonadState =
        copy(map.extractAll(origins))

    override fun merge(other: MapSubstitutionMonadState): MapSubstitutionMonadState =
        copy(map.putAll(other.map))

    override fun removeAll(origins: Collection<KDecl<*>>): MapSubstitutionMonadState =
        copy(map.removeAll(origins))

    override fun substitute(origin: KDecl<*>, target: KDecl<*>): MapSubstitutionMonadState =
        copy(map.put(origin, target))

    override fun hasSubstitutionFor(origin: KDecl<*>): Boolean =
        origin in map
}