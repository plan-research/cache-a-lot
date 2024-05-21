package org.plan.research.cachealot

import kotlinx.coroutines.*
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.random.Random

fun main() {

    println(Thread.currentThread().name)
    runBlocking(EmptyCoroutineContext) {
        println(Thread.currentThread().name)
        repeat(10) {
            println("Processing $it")
            delay(Random.nextLong(10, 1000))
            println(it)
            println(Thread.currentThread().name)
        }
    }
    println("Done")
    println(Thread.currentThread().name)
}

