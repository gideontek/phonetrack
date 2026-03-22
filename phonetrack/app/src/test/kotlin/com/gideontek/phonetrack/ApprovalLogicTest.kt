package com.gideontek.phonetrack

import org.junit.Assert.*
import org.junit.Test

class ApprovalLogicTest {

    @Test fun `PENDING to APPROVED allowed`() {
        assertTrue(ApprovalLogic.canTransition(ApprovalState.PENDING, ApprovalState.APPROVED))
    }

    @Test fun `PENDING to BLOCKED allowed`() {
        assertTrue(ApprovalLogic.canTransition(ApprovalState.PENDING, ApprovalState.BLOCKED))
    }

    @Test fun `APPROVED to BLOCKED allowed`() {
        assertTrue(ApprovalLogic.canTransition(ApprovalState.APPROVED, ApprovalState.BLOCKED))
    }

    @Test fun `BLOCKED to APPROVED allowed`() {
        assertTrue(ApprovalLogic.canTransition(ApprovalState.BLOCKED, ApprovalState.APPROVED))
    }

    @Test fun `APPROVED to PENDING not allowed`() {
        assertFalse(ApprovalLogic.canTransition(ApprovalState.APPROVED, ApprovalState.PENDING))
    }

    @Test fun `BLOCKED to PENDING not allowed`() {
        assertFalse(ApprovalLogic.canTransition(ApprovalState.BLOCKED, ApprovalState.PENDING))
    }

    @Test fun `PENDING to PENDING not allowed`() {
        assertFalse(ApprovalLogic.canTransition(ApprovalState.PENDING, ApprovalState.PENDING))
    }

    @Test fun `same state APPROVED to APPROVED allowed`() {
        assertTrue(ApprovalLogic.canTransition(ApprovalState.APPROVED, ApprovalState.APPROVED))
    }
}
