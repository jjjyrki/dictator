package io.jyri.dictator

import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
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
import org.junit.Assert.assertNull
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
        val requiredIds = listOf(
            R.id.microphoneStepHeading,
            R.id.microphoneBanner,
            R.id.accessibilityStepHeading,
            R.id.accessibilityStep,
            R.id.testStepHeading,
            R.id.testStep,
            R.id.preferencesSection,
            R.id.modelDownloadProgress,
            R.id.modelMenu,
            R.id.installModel,
            R.id.record,
            R.id.sampleWaveform,
            R.id.transcript,
            R.id.sampleMetrics,
            R.id.accessibilityStatus,
            R.id.openAccessibility,
            R.id.toggleInsertMode,
            R.id.togglePartials,
            R.id.licenses,
        )
        scenario.onActivity { activity ->
            requiredIds.forEach { id ->
                assertNotNull(
                    "Missing view: ${activity.resources.getResourceEntryName(id)}",
                    activity.findViewById<View>(id),
                )
            }

            val status = activity.findViewById<TextView>(R.id.accessibilityStatus).text
            val enabled = targetContext.getString(R.string.accessibility_enabled)
            val disabled = targetContext.getString(R.string.accessibility_disabled)
            assertTrue("Unexpected accessibility status: $status", status == enabled || status == disabled)
        }
    }

    @Test
    fun licensesButtonOpensLicenseView() {
        clickView("licenses")
        assertNotNull(waitFor(By.res(targetPackage, "licensesBody")))

        val body = view("licensesBody").text
        assertTrue(body.contains("MIT License"))
        assertTrue(body.contains("Apache License"))
        assertTrue(body.contains("MATERIAL COMPONENTS FOR ANDROID"))
        device.pressBack()
        waitForApp()
    }

    @Test
    fun modelMenuShowsAllPackagedChoices() {
        enabledView("modelMenu")
        clickView("modelMenu")
        device.waitForIdle()

        assertNotNull(waitFor(By.textContains("Tiny")))
        assertNotNull(waitFor(By.textContains("Base")))
        assertNotNull(waitFor(By.textContains("Small")))
        assertNotNull(waitFor(By.textContains("Multilingual")))
        device.pressBack()
    }

    @Test
    fun multilingualModelShowsLanguageChoices() {
        enabledView("modelMenu")
        clickView("modelMenu")
        assertNotNull(waitFor(By.textContains("Multilingual")))
        click(By.descContains("Multilingual"))
        device.waitForIdle()

        enabledView("languageMenu")
        clickView("languageMenu")
        assertNotNull(waitFor(By.text("English")))
        assertNotNull(waitFor(By.text("Finnish")))
        device.pressBack()
    }

    @Test
    fun languageListCanBeSearched() {
        enabledView("modelMenu")
        clickView("modelMenu")
        click(By.descContains("Multilingual"))
        device.waitForIdle()

        enabledView("languageMenu")
        clickView("languageMenu")
        val search = view("languageSearch")
        search.click()
        search.setText("Cantonese")
        device.waitForIdle()

        assertNotNull(waitFor(By.text("Cantonese")))
        assertNull(device.findObject(By.text("English")))
        device.pressBack()
    }

    @Test
    fun multilingualModelAllowsAtMostFourLanguages() {
        LanguageSelection.store(
            targetContext,
            setOf(SpokenLanguage.ENGLISH, SpokenLanguage.FINNISH),
        )
        enabledView("modelMenu")
        clickView("modelMenu")
        click(By.descContains("Multilingual"))
        device.waitForIdle()

        enabledView("languageMenu")
        clickView("languageMenu")
        click(By.text("Chinese"))
        click(By.text("German"))
        click(By.text("Spanish"))

        assertNotNull(
            waitFor(By.text(targetContext.getString(R.string.language_selection_maximum))),
        )
        click(By.text(targetContext.getString(R.string.done)))
        device.waitForIdle()

        assertEquals(SpokenLanguage.MAX_SELECTED_LANGUAGES, LanguageSelection.load(targetContext).size)
    }

    @Test
    fun settingsTogglesPersistAfterRelaunch() {
        val initialInsertLabel = view("toggleInsertMode").text
        val initialPartialsLabel = view("togglePartials").text

        clickView("toggleInsertMode")
        clickView("togglePartials")
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
        clickView("openAccessibility")
        val continueSelector = By.text(targetContext.getString(R.string.continue_to_accessibility_settings))
        assertNotNull(waitFor(continueSelector))
        click(continueSelector)

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
            device.wait(Until.hasObject(By.res(targetPackage, "mainContent")), WAIT_MS),
        )
        assertEquals(targetPackage, device.currentPackageName)
        device.waitForIdle()
    }

    private fun view(id: String): UiObject2 {
        val selector = By.res(targetPackage, id)
        val deadline = SystemClock.uptimeMillis() + WAIT_MS
        var scrollable: UiObject2? = null
        while (SystemClock.uptimeMillis() < deadline) {
            device.findObject(selector)?.let { return it }
            if (scrollable == null) scrollable = device.findObject(By.scrollable(true))
            if (scrollable?.scroll(Direction.DOWN, 0.8f) != true) {
                SystemClock.sleep(100)
            } else {
                device.waitForIdle()
            }
        }
        throw IllegalArgumentException("Missing view: $id")
    }

    private fun clickView(id: String) {
        view(id)
        click(By.res(targetPackage, id))
    }

    private fun click(selector: androidx.test.uiautomator.BySelector) {
        val deadline = SystemClock.uptimeMillis() + WAIT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val candidate = device.findObject(selector)
            if (candidate != null) {
                try {
                    candidate.click()
                    return
                } catch (_: StaleObjectException) {
                    device.waitForIdle()
                }
            }
            SystemClock.sleep(100)
        }
        throw IllegalArgumentException("Missing clickable object: $selector")
    }

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
