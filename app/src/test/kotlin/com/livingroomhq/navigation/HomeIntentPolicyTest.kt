package com.livingroomhq.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeIntentPolicyTest {

    @Test
    fun `HOME intent while returning from an external app goes Home`() {
        val shouldGo = HomeIntentPolicy.shouldGoHome(
            hasHomeCategory = true,
            returningFromExternal = true,
            suppressHomeReset = false,
            wasAlreadyResumed = false,
        )
        assertTrue("Expected to return Home when returning from external app", shouldGo)
    }

    @Test
    fun `HOME intent while returning from external app even if already marked resumed goes Home`() {
        val shouldGo = HomeIntentPolicy.shouldGoHome(
            hasHomeCategory = true,
            returningFromExternal = true,
            suppressHomeReset = false,
            wasAlreadyResumed = true,
        )
        assertTrue("Expected to return Home when returning from external app", shouldGo)
    }

    @Test
    fun `HOME intent while LRHQ is already foregrounded on Apps stays on Apps`() {
        val shouldGo = HomeIntentPolicy.shouldGoHome(
            hasHomeCategory = true,
            returningFromExternal = false,
            suppressHomeReset = false,
            wasAlreadyResumed = true,
        )
        assertFalse("Expected to stay on current screen when spurious HOME intent arrives in foreground", shouldGo)
    }

    @Test
    fun `Return from app-info settings stays on Apps`() {
        val shouldGo = HomeIntentPolicy.shouldGoHome(
            hasHomeCategory = true,
            returningFromExternal = false,
            suppressHomeReset = true,
            wasAlreadyResumed = false,
        )
        assertFalse("Expected to suppress home reset when returning from app settings", shouldGo)
    }

    @Test
    fun `Normal navigation to Apps does not get reset by lifecycle resume`() {
        // When user navigates to Apps, activity is already resumed. A redelivered HOME intent is ignored.
        val shouldGo = HomeIntentPolicy.shouldGoHome(
            hasHomeCategory = true,
            returningFromExternal = false,
            suppressHomeReset = false,
            wasAlreadyResumed = true,
        )
        assertFalse("Expected no home reset during normal navigation/resumed state", shouldGo)
    }

    @Test
    fun `Multiple duplicate HOME intents do not unexpectedly change zones`() {
        // 1st intent when returning from external app -> goes Home
        val firstIntent = HomeIntentPolicy.shouldGoHome(
            hasHomeCategory = true,
            returningFromExternal = true,
            suppressHomeReset = false,
            wasAlreadyResumed = false,
        )
        assertTrue("First intent from external app should go Home", firstIntent)

        // After the first intent, activity is resumed and returningFromExternal is false.
        // Subsequent duplicate/redelivered HOME intents must not change zones.
        val secondIntent = HomeIntentPolicy.shouldGoHome(
            hasHomeCategory = true,
            returningFromExternal = false,
            suppressHomeReset = false,
            wasAlreadyResumed = true,
        )
        assertFalse("Duplicate intent while foregrounded must not go Home", secondIntent)

        val thirdIntent = HomeIntentPolicy.shouldGoHome(
            hasHomeCategory = true,
            returningFromExternal = false,
            suppressHomeReset = false,
            wasAlreadyResumed = true,
        )
        assertFalse("Subsequent duplicate intent while foregrounded must not go Home", thirdIntent)
    }

    @Test
    fun `Non-HOME intent does not go Home`() {
        val shouldGo = HomeIntentPolicy.shouldGoHome(
            hasHomeCategory = false,
            returningFromExternal = true,
            suppressHomeReset = false,
            wasAlreadyResumed = false,
        )
        assertFalse("Non-home category should not go Home", shouldGo)
    }
}
