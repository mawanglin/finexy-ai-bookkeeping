package com.finexy.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchDeleteMessageTest {
    @Test
    fun reportsPlainSuccess() {
        assertEquals("已删除 3 笔流水", batchDeleteMessage(3, 0, null))
    }

    @Test
    fun neverHidesPartialFailure() {
        val message = batchDeleteMessage(2, 1, "你没有权限删除这笔流水")
        assertTrue(message.contains("已删除 2 笔"))
        assertTrue(message.contains("1 笔失败"))
        assertTrue(message.contains("你没有权限删除这笔流水"))
    }

    @Test
    fun totalFailureStartsWithFailureAndCountsEverything() {
        val message = batchDeleteMessage(0, 4, null)
        assertTrue(message.startsWith("删除失败"))
        assertTrue(message.contains("共 4 笔未删除"))
        assertTrue(message.contains("请稍后重试"))
    }
}
