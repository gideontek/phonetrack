package com.gideontek.phonetrack

/** A parsed inbound SMS command (everything after the keyword). */
sealed class SmsCommand {
    /** Bare keyword: reply with a fresh location fix. */
    data object OneShot : SmsCommand()

    data class Subscribe(val params: SubscribeParams) : SmsCommand()

    /** `subscribe` with bad arguments; [message] says what was wrong. */
    data class InvalidSubscribe(val message: String) : SmsCommand()

    data object Unsubscribe : SmsCommand()

    /** Reply with the cached last-known fix without waking the GPS. */
    data object Last : SmsCommand()

    /** `help`, or any word we don't recognise. */
    data object Help : SmsCommand()
}
