package org.plan.research.cachealot

import kotlinx.coroutines.*
import kotlin.random.Random

fun main() {

    runBlocking(Dispatchers.Default) {
        coroutineScope {
            repeat(10) {
                launch {
                    coroutineScope {
                        println("Processing $it")
                        delay(Random.nextLong(10, 1000))
                        println(it)
                    }
                }
            }
        }
        println("Done")
    }

}