package com.finexy.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AccountLedgerPayloadTest {
    private val draft = AccountDraft(name = "共享卡", category = 1, currency = "CNY", balanceMinor = 100)

    @Test
    fun sharedLedgerAccountCarriesLedgerIdAsString() {
        assertEquals("9007199254741003", accountCreatePayload(draft, 9007199254741003L).getString("ledgerId"))
    }

    @Test
    fun defaultLedgerAccountOmitsLedgerId() {
        assertFalse(accountCreatePayload(draft, 0).has("ledgerId"))
    }
}
