package com.yumiru11.githubapp.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 铃铛未读角标数字格式化单测（issue #85 / audit 缺陷 #14：99+ 上限）。 */
class AppTopBarBadgeTest {
    @Test
    fun badgeCount_singleDigit_showsAsIs() {
        assertEquals("1", formatBadgeCount(1))
    }

    @Test
    fun badgeCount_ninetyNine_showsAsIs() {
        assertEquals("99", formatBadgeCount(99))
    }

    @Test
    fun badgeCount_oneHundred_capsAtNinetyNinePlus() {
        assertEquals("99+", formatBadgeCount(100))
    }

    @Test
    fun badgeCount_largeNumber_capsAtNinetyNinePlus() {
        assertEquals("99+", formatBadgeCount(12345))
    }
}
