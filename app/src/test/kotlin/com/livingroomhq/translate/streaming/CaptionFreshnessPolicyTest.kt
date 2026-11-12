package com.livingroomhq.translate.streaming

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionFreshnessPolicyTest {
    @Test fun queuedRecognitionCannotCatchUpByDisplayingOldAdverts() {
        assertTrue(CaptionFreshnessPolicy.canStartRecognition(1_000L, 2_000L))
        assertFalse(CaptionFreshnessPolicy.canStartRecognition(1_000L, 2_001L))
        assertFalse(CaptionFreshnessPolicy.canStartRecognition(1_000L, 999L))
    }

    @Test fun slowRecognitionOrTranslationCannotPublishLateCaptions() {
        assertTrue(CaptionFreshnessPolicy.canPublish(1_000L, 6_000L))
        assertFalse(CaptionFreshnessPolicy.canPublish(1_000L, 6_001L))
        assertFalse(CaptionFreshnessPolicy.canPublish(1_000L, 999L))
    }
}
