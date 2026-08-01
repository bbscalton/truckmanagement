package com.truckmgmt.shared

import kotlin.random.Random

object FleetIdGenerator {
    /** Uppercase alphanumeric, excluding I/O/0/1 to reduce typos. */
    private const val CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val DEFAULT_LENGTH = 6

    fun generate(length: Int = DEFAULT_LENGTH): String =
        buildString(length) {
            repeat(length) { append(CHARS[Random.nextInt(CHARS.length)]) }
        }

    fun normalize(input: String): String = input.trim().uppercase()
}
