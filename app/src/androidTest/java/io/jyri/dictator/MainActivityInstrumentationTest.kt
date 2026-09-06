package io.jyri.dictator

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import io.jyri.dictator.insert.InsertMode
import io.jyri.dictator.model.LanguageSelection
import io.jyri.dictator.model.ModelSelection
import io.jyri.dictator.model.SpokenLanguage
import io.jyri.dictator.model.SttModelProfile
import io.jyri.dictator.speech.PartialsSetting
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentationTest {
    private lateinit var device: UiDevice
    private lateinit var scenario: ActivityScenario<MainActivity>

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val targetContext
        get() = instrumentation.targetContext

    private val targetPackage: String
        get() = targetContext.packageName

    private var initialInsertMode: InsertMode? = null
    private var initialPartials: Boolean? = null
    private var initialModel: SttModelProfile? = null
    private var initialLanguages: Set<SpokenLanguage>? = null

    @Before
    fun setUp() {
        device = UiDevice.getInstance(instrumentation)
        initialInsertMode = InsertMode.load(targetContext)
        initialPartials = PartialsSetting.load(targetContext)
        initialModel = ModelSelection.load(targetContext)
        initialLanguages = LanguageSelection.load(targetContext)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForApp()
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        initialInsertMode?.let { InsertMode.store(targetContext, it) }
        initialPartials?.let { PartialsSetting.store(targetContext, it) }
        initialModel?.let { ModelSelection.store(targetContext, it) }
        initialLanguages?.let { LanguageSelection.store(targetContext, it) }
    }

    @Test
    fun launchShowsSetupControls() {
        listOf(
            "modelMenu",
            "installModel",
            "record",
            "transcript",
            "sampleMetrics",
            "accessibilityStatus",
            "openAccessibility",
            "toggleInsertMode",
            "togglePartials",
            "licenses",
        ).forEach { id ->
            assertNotNull("Missing view: $id", view(id))
        }

        val status = view("accessibilityStatus").text
        val enabled = targetContext.getString(R.string.accessibility_enabled)
        val disabled = targetContext.getString(R.string.accessibility_disabled)
        assertTrue("Unexpected accessibility status: $status", status == enabled || status == disabled)
    }

    @Test
    fun licensesButtonOpensLicenseView() {
        view("licenses").click()

        val body = view("licensesBody").text
        assertTrue(body.contains("MIT License"))
        assertTrue(body.contains("Apache License"))
        device.pressBack()
        waitForApp()
    }

    @Test
    fun modelMenuShowsAllPackagedChoices() {
        enabledView("modelMenu").click()
        device.waitForIdle()

        assertNotNull(waitFor(By.textContains("Tiny")))
        assertNotNull(waitFor(By.textContains("Base")))
        assertNotNull(waitFor(By.textContains("Small")))
        assertNotNull(waitFor(By.textContains("Multilingual")))
        device.pressBack()
    }

    @Test
    fun multilingualModelShowsLanguageChoices() {
        enabledView("modelMenu").click()
        val multilingual = requireNotNull(waitFor(By.textContains("Multilingual")))
        multilingual.click()
        device.waitForIdle()

        enabledView("languageMenu").click()
        assertNotNull(waitFor(By.text("English")))
        assertNotNull(waitFor(By.text("Finnish")))
        device.pressBack()
    }

    @Test
    fun settingsTogglesPersistAfterRelaunch() {
        val initialInsertLabel = view("toggleInsertMode").text
        val initialPartialsLabel = view("togglePartials").text

        view("toggleInsertMode").click()
        view("togglePartials").click()
        device.waitForIdle()

        val changedInsertLabel = view("toggleInsertMode").text
        val changedPartialsLabel = view("togglePartials").text
        assertNotEquals(initialInsertLabel, changedInsertLabel)
        assertNotEquals(initialPartialsLabel, changedPartialsLabel)

        relaunch()

        assertEquals(changedInsertLabel, view("toggleInsertMode").text)
        assertEquals(changedPartialsLabel, view("togglePartials").text)
    }

    @Test
    fun accessibilityButtonOpensSystemSettings() {
        view("openAccessibility").click()
        assertNotNull(waitFor(By.text(targetContext.getString(R.string.continue_to_accessibility_settings))))
        device.findObject(By.text(targetContext.getString(R.string.continue_to_accessibility_settings))).click()

        assertTrue(
            "Accessibility settings did not open",
            device.wait(Until.hasObject(By.pkg("com.android.settings")), WAIT_MS),
        )
        assertEquals("com.android.settings", device.currentPackageName)
        device.pressBack()
        waitForApp()
    }

    private fun relaunch() {
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForApp()
    }

    private fun waitForApp() {
        assertTrue(
            "Dictator activity did not reach the foreground",
            device.wait(Until.hasObject(By.pkg(targetPackage)), WAIT_MS),
        )
        device.waitForIdle()
    }

    private fun view(id: String): UiObject2 =
        requireNotNull(waitFor(By.res(targetPackage, id))) { "Missing view: $id" }

    private fun enabledView(id: String): UiObject2 {
        val deadline = SystemClock.uptimeMillis() + MODEL_WAIT_MS
        var candidate = view(id)
        while (!candidate.isEnabled && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
            candidate = view(id)
        }
        assertTrue("View is disabled: $id", candidate.isEnabled)
        return candidate
    }

    private fun waitFor(selector: androidx.test.uiautomator.BySelector): UiObject2? =
        device.wait(Until.findObject(selector), WAIT_MS)

    private companion object {
        const val WAIT_MS = 5_000L
        const val MODEL_WAIT_MS = 30_000L
    }
}
