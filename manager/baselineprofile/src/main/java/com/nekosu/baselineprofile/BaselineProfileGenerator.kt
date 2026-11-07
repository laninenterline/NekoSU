package com.nekosu.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = "com.nekosu",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun homePagerUserScroll() = baselineProfileRule.collect(
        packageName = "com.nekosu",
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait()
        scrollHomePagerBackAndForth()
    }
}
