package org.plan.research.cachealot

import io.ksmt.decl.KDecl
import io.ksmt.decl.KParameterizedFuncDecl

infix fun KDecl<*>?.structEquals(other: KDecl<*>?): Boolean {
    if (this === other) return true
    if (this == null || other == null) return false
    if (javaClass != other.javaClass) return false

    if (name != other.name) return false
    if (sort != other.sort) return false
    if (argSorts != other.argSorts) return false
    if (this is KParameterizedFuncDecl && parameters != (other as? KParameterizedFuncDecl)?.parameters) return false

    return true
}