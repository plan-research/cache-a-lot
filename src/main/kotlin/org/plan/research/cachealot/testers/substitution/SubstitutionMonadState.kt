package org.plan.research.cachealot.testers.substitution

import io.ksmt.decl.KDecl

interface SubstitutionMonadState<T : SubstitutionMonadState<T>> {
    fun hasSubstitutionFor(origin: KDecl<*>): Boolean
    fun checkSubstitution(origin: KDecl<*>, target: KDecl<*>): Boolean
    fun merge(other: T): T
    fun remove(origin: KDecl<*>): T
    fun removeAll(origins: Collection<KDecl<*>>): T
    fun substitute(origin: KDecl<*>, target: KDecl<*>): T
}
