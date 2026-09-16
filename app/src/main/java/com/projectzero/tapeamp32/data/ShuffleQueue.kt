package com.projectzero.tapeamp32.data

import kotlin.random.Random

object ShuffleQueue {

    fun <T> shuffled(list: List<T>, random: Random = Random.Default): List<T> {
        if (list.size <= 1) return list
        val array = list.toMutableList()
        for (i in array.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = array[i]
            array[i] = array[j]
            array[j] = tmp
        }
        return array
    }
}

fun <T> List<T>.toShuffledQueue(random: Random = Random.Default): List<T> =
    ShuffleQueue.shuffled(this, random)
