package com.gideontek.phonetrack

data class SubscribeParams(val dist: Int, val freq: Int, val hours: Int)

sealed class SubscribeResult {
    data class Ok(val params: SubscribeParams) : SubscribeResult()
    data class Error(val message: String) : SubscribeResult()
}

object SmsCommandParser {

    /**
     * Parses the tokens that follow the keyword. A bare keyword is a one-shot request;
     * an unrecognised word gets the help reply rather than a location.
     */
    fun parse(args: List<String>): SmsCommand = when (args.firstOrNull()?.lowercase()) {
        null -> SmsCommand.OneShot
        "subscribe" -> when (val result = parseSubscribeDetailed(args.drop(1))) {
            is SubscribeResult.Ok -> SmsCommand.Subscribe(result.params)
            is SubscribeResult.Error -> SmsCommand.InvalidSubscribe(result.message)
        }
        "unsubscribe" -> SmsCommand.Unsubscribe
        "last" -> SmsCommand.Last
        else -> SmsCommand.Help
    }

    /** Convenience wrapper: the params, or null on any error. */
    fun parseSubscribe(tokens: List<String>): SubscribeParams? =
        (parseSubscribeDetailed(tokens) as? SubscribeResult.Ok)?.params

    /**
     * Parses "--dist N --freq N --time N" tokens (any order, all optional, flags
     * case-insensitive). Phone keyboards often turn "--" into an em/en dash, so those are
     * accepted too. Out-of-range values are rejected, never clamped.
     */
    fun parseSubscribeDetailed(tokens: List<String>): SubscribeResult {
        var dist = 200
        var freq = 15
        var hours = 4
        val seen = mutableSetOf<String>()

        var i = 0
        while (i < tokens.size) {
            val flag = normalizeDashes(tokens[i]).lowercase()
            when (flag) {
                "--dist", "--freq", "--time" -> {
                    if (!seen.add(flag)) return SubscribeResult.Error("$flag given twice")
                    val v = tokens.getOrNull(i + 1)?.toIntOrNull()
                        ?: return SubscribeResult.Error("$flag needs a whole number")
                    when (flag) {
                        "--dist" -> {
                            if (v !in SmsLimits.MIN_DIST..SmsLimits.MAX_DIST) {
                                return SubscribeResult.Error(
                                    "--dist must be ${SmsLimits.MIN_DIST}-${SmsLimits.MAX_DIST} (metres)"
                                )
                            }
                            dist = v
                        }
                        "--freq" -> {
                            if (v !in SmsLimits.MIN_FREQ..SmsLimits.MAX_FREQ) {
                                return SubscribeResult.Error(
                                    "--freq must be ${SmsLimits.MIN_FREQ}-${SmsLimits.MAX_FREQ} (minutes)"
                                )
                            }
                            freq = v
                        }
                        else -> {
                            if (v !in SmsLimits.MIN_TIME..SmsLimits.MAX_TIME) {
                                return SubscribeResult.Error(
                                    "--time must be ${SmsLimits.MIN_TIME}-${SmsLimits.MAX_TIME} (hours)"
                                )
                            }
                            hours = v
                        }
                    }
                    i += 2
                }
                else -> return SubscribeResult.Error("Unknown option ${tokens[i].take(20)}")
            }
        }

        return SubscribeResult.Ok(SubscribeParams(dist, freq, hours))
    }

    private fun normalizeDashes(token: String): String =
        token.replace("—", "--").replace("–", "--")
}
