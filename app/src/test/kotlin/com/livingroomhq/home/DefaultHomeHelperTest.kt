package com.livingroomhq.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultHomeHelperTest {
    @Test
    fun `prompts only when not default and not dismissed`() {
        assertTrue(shouldPromptForDefault(isDefault = false, dismissed = false))
        assertFalse(shouldPromptForDefault(isDefault = true, dismissed = false))
        assertFalse(shouldPromptForDefault(isDefault = false, dismissed = true))
        assertFalse(shouldPromptForDefault(isDefault = true, dismissed = true))
    }
}
