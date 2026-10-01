package com.gideontek.phonetrack.smoke

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import com.gideontek.phonetrack.support.KnownIssues
import com.gideontek.phonetrack.support.Loopback
import com.gideontek.phonetrack.support.ResetStateRule
import com.gideontek.phonetrack.support.SyntheticSms
import com.gideontek.phonetrack.support.TestState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Layer 2 smoke: a message through the real receiver, observed in state and in the loopback inbox. */
class SmokeReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(
            Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS
        ))
        .around(ResetStateRule())

    @Test
    fun approvedNumberGetsAHelpReply() {
        KnownIssues.assumeOutboundSmsWorks()
        TestState.enableListening()
        TestState.seedApprovals(Triple(Loopback.ownNumber, "APPROVED", System.currentTimeMillis()))
        val before = Loopback.lastId()

        SyntheticSms.deliver(context, Loopback.ownNumber, "phonetrack help")

        val reply = Loopback.await(before) { it.startsWith("[PhoneTrack]") }
        assertNotNull("no help reply arrived in the loopback inbox", reply)
        assertTrue(reply!!, reply.contains("subscribe"))
    }

    @Test
    fun unknownNumberIsRecordedAsPendingWithoutAReply() {
        TestState.enableListening()
        val stranger = "+15550001111"
        val before = Loopback.lastId()

        SyntheticSms.deliver(context, stranger, "phonetrack")

        assertTrue(TestState.approvalsJson(), TestState.approvalsJson().contains(stranger))
        assertTrue(TestState.approvalsJson().contains("PENDING"))
        assertTrue("a pending number must not get a reply", Loopback.staysQuiet(before) { it.startsWith("[PhoneTrack]") })
    }

    @Test
    fun parcelStringDecoding() {
        val raw = "Result: Parcel(\n0x00000000: 00000000 0000000c 0031002b 00350035 '........+.1.5.5.'\n" +
            "0x00000010: 00310035 00330032 00350034 00370036 '5.1.2.3.4.5.6.7.')"
        assertEquals("+15551234567", Loopback.parseParcelString(raw))
    }
}
