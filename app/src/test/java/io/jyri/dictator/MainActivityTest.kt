package io.jyri.dictator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityTest {
    @Test
    fun accessibilityControlsRemainAvailableAfterBubbleIsEnabled() {
        assertTrue(MainActivity.shouldShowAccessibilityControls(modelDownloaded = true, microphoneGranted = true))
        assertEquals(
            R.string.open_accessibility_settings,
            MainActivity.accessibilityButtonLabel(accessibilityEnabled = true),
        )
    }

    @Test
    fun accessibilityControlsRequireModelAndMicrophone() {
        assertFalse(MainActivity.shouldShowAccessibilityControls(modelDownloaded = false, microphoneGranted = true))
        assertFalse(MainActivity.shouldShowAccessibilityControls(modelDownloaded = true, microphoneGranted = false))
        assertEquals(
            R.string.enable_accessibility,
            MainActivity.accessibilityButtonLabel(accessibilityEnabled = false),
        )
    }
}
