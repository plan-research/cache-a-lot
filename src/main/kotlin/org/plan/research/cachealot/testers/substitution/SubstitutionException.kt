package org.plan.research.cachealot.testers.substitution

class SubstitutionException : RuntimeException()

fun substitutionFail() {
    throw SubstitutionException()
}

inline fun substitutionAssert(block: () -> Boolean) {
    if (!block()) substitutionFail()
}