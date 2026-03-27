package com.gideontek.phonetrack

data class SubscribeParams(val dist: Int, val freq: Int, val hours: Int)

object SmsCommandParser {

    /** Parses "--dist N --freq N --time N" tokens (any order, all optional).
     *  Returns null on any error (unknown flag, missing value, freq < 1). */
    fun parseSubscribe(tokens: List<String>): SubscribeParams? {
        var dist = 200
        var freq = 15
        var hours = 4

        var i = 0
        while (i < tokens.size) {
            when (tokens[i]) {
                "--dist" -> {
                    val v = tokens.getOrNull(i + 1)?.toIntOrNull() ?: return null
                    dist = v; i += 2
                }
                "--freq" -> {
                    val v = tokens.getOrNull(i + 1)?.toIntOrNull() ?: return null
                    if (v < 1) return null
                    freq = v; i += 2
                }
                "--time" -> {
                    val v = tokens.getOrNull(i + 1)?.toIntOrNull() ?: return null
                    hours = v; i += 2
                }
                else -> return null
            }
        }

        return SubscribeParams(dist, freq, hours)
    }
}
