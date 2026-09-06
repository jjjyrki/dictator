package io.jyri.dictator

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import io.jyri.dictator.audio.LiveSttRecorder
import io.jyri.dictator.insert.InsertMode
import io.jyri.dictator.model.FinnishSetting
import io.jyri.dictator.model.ModelSelection
import io.jyri.dictator.model.SttModelInstaller
import io.jyri.dictator.model.SttModelVariant
import io.jyri.dictator.speech.PartialsSetting
import io.jyri.dictator.speech.SttEngineHolder
import io.jyri.dictator.speech.WhisperSttEngine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : android.app.Activity() {
    private val background: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var modelInstaller: SttModelInstaller
    private lateinit var modelStatus: TextView
    private lateinit var selectedModelLabel: TextView
    private lateinit var sampleMetrics: TextView
    private lateinit var transcript: TextView
    private lateinit var recordButton: Button
    private lateinit var installModelButton: Button
    private lateinit var modelMenuButton: Button
    private lateinit var finnishToggle: Button

    private var selectedVariant: SttModelVariant = SttModelVariant.SMALL
    private var engine: WhisperSttEngine? = null
    private var recorder: LiveSttRecorder? = null
    private var modelInstallationInProgress = false
    private var modelLoadingInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedVariant = ModelSelection.load(this)
        setContentView(R.layout.activity_main)
        modelStatus = findViewById(R.id.modelStatus)
        selectedModelLabel = findViewById(R.id.selectedModel)
        sampleMetrics = findViewById(R.id.sampleMetrics)
        transcript = findViewById(R.id.transcript)
        recordButton = findViewById(R.id.record)
        installModelButton = findViewById(R.id.installModel)
        modelMenuButton = findViewById(R.id.modelMenu)
        finnishToggle = findViewById(R.id.toggleFinnish)
        modelInstaller = installerFor()

        modelMenuButton.setOnClickListener { showModelMenu() }
        findViewById<Button>(R.id.openAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        installModelButton.setOnClickListener { installModel() }
        val toggleInsertMode = findViewById<Button>(R.id.toggleInsertMode)
        val refreshInsertModeLabel = {
            toggleInsertMode.setText(
                if (InsertMode.load(this) == InsertMode.REPLACE) R.string.mode_replace else R.string.mode_merge,
            )
        }
        toggleInsertMode.setOnClickListener {
            val next = if (InsertMode.load(this) == InsertMode.MERGE) InsertMode.REPLACE else InsertMode.MERGE
            InsertMode.store(this, next)
            refreshInsertModeLabel()
        }
        refreshInsertModeLabel()
        val togglePartials = findViewById<Button>(R.id.togglePartials)
        val refreshPartialsLabel = {
            togglePartials.setText(
                if (PartialsSetting.load(this)) R.string.partials_on else R.string.partials_off,
            )
        }
        togglePartials.setOnClickListener {
            val next = !PartialsSetting.load(this)
            PartialsSetting.store(this, next)
            refreshPartialsLabel()
        }
        refreshPartialsLabel()
        val refreshFinnishLabel = {
            finnishToggle.setText(
                if (finnishEnabled()) R.string.finnish_on else R.string.finnish_off,
            )
        }
        finnishToggle.setOnClickListener { toggleFinnish() }
        refreshFinnishLabel()
        recordButton.setOnClickListener { toggleRecording() }
        ensureEngineLoaded()
        updateModelControls()
    }

    override fun onResume() {
        super.onResume()
        val status = findViewById<TextView>(R.id.accessibilityStatus)
        if (isOverlayServiceEnabled()) {
            status.setText(R.string.accessibility_enabled)
            status.setTextColor(getColor(R.color.setup_ok))
        } else {
            status.setText(R.string.accessibility_disabled)
            status.setTextColor(getColor(R.color.setup_warn))
        }
    }

    override fun onDestroy() {
        recorder?.let { active ->
            background.execute { runCatching { active.stopAndFinish() } }
        }
        // The engine stays in SttEngineHolder: the accessibility service
        // reuses the same native context, so it must survive this activity.
        background.shutdown()
        super.onDestroy()
    }

    private fun installModel() {
        if (modelInstallationInProgress || modelLoadingInProgress || recorder != null) return
        if (modelInstaller.isInstalled()) {
            loadSelectedModel()
            return
        }
        modelInstallationInProgress = true
        installModelButton.isEnabled = false
        modelMenuButton.isEnabled = false
        finnishToggle.isEnabled = false
        setModelStatus(getString(R.string.model_installing))
        val installer = modelInstaller
        background.execute {
            runCatching {
                installer.install { progress ->
                    mainHandler.post {
                        setModelStatus(
                            getString(
                                R.string.model_install_progress,
                                progress.assetIndex,
                                progress.assetCount,
                                progress.fileName,
                                progress.downloadedBytes / BYTES_PER_MEBIBYTE,
                                progress.totalBytes / BYTES_PER_MEBIBYTE,
                            ),
                        )
                    }
                }
            }.onSuccess {
                mainHandler.post {
                    modelInstallationInProgress = false
                    setModelStatus(getString(R.string.model_installed))
                    loadSelectedModel()
                }
            }.onFailure { error ->
                mainHandler.post {
                    modelInstallationInProgress = false
                    setModelStatus(getString(R.string.model_error, error.message ?: error.javaClass.simpleName))
                    updateModelControls()
                }
            }
        }
    }

    /** Loads the persisted model, or reuses the process-wide engine if it matches. */
    private fun ensureEngineLoaded() {
        val finnish = finnishEnabled()
        val shared = SttEngineHolder.engine
        if (shared != null && SttEngineHolder.matches(selectedVariant, finnish)) {
            engine = shared
            return
        }
        unloadCurrentEngine()
        modelInstaller = installerFor()
        if (!modelInstaller.isInstalled()) return
        loadSelectedModel()
    }

    private fun loadSelectedModel() {
        if (modelLoadingInProgress) return
        val variant = selectedVariant
        val finnish = finnishEnabled()
        val installer = installerFor(variant)
        modelInstaller = installer
        if (!installer.isInstalled()) {
            updateModelControls()
            return
        }
        unloadCurrentEngine()
        modelLoadingInProgress = true
        setModelStatus(getString(R.string.model_loading))
        background.execute {
            runCatching {
                WhisperSttEngine(installer.modelFile(), installer.language())
            }.onSuccess { created ->
                mainHandler.post {
                    // Model choices are disabled while loading, but avoid
                    // installing a stale result if the Activity is recreated.
                    if (selectedVariant != variant || finnishEnabled() != finnish) {
                        modelLoadingInProgress = false
                        created.close()
                        updateModelControls()
                        return@post
                    }
                    SttEngineHolder.install(variant, finnish, created)
                    engine = created
                    modelLoadingInProgress = false
                    setModelStatus(getString(R.string.model_ready))
                    updateModelControls()
                }
            }.onFailure { error ->
                mainHandler.post {
                    modelLoadingInProgress = false
                    setModelStatus(getString(R.string.model_error, error.message ?: error.javaClass.simpleName))
                    updateModelControls()
                }
            }
        }
    }

    private fun showModelMenu() {
        val popup = PopupMenu(this, modelMenuButton)
        popup.menuInflater.inflate(R.menu.model_menu, popup.menu)
        val finnish = finnishEnabled()
        modelMenuItems().forEach { (variant, itemId) ->
            val item = popup.menu.findItem(itemId)
            item.isChecked = variant == selectedVariant
            val asset = variant.asset(finnish)
            val installed = SttModelInstaller(this, variant, finnish).isInstalled()
            item.title = getString(
                if (installed) R.string.model_menu_installed else R.string.model_menu_not_installed,
                asset.displayName,
            )
        }
        popup.setOnMenuItemClickListener { item ->
            val variant = modelMenuItems().firstOrNull { it.second == item.itemId }?.first
                ?: return@setOnMenuItemClickListener false
            item.isChecked = true
            selectModel(variant)
            true
        }
        popup.show()
    }

    private fun selectModel(variant: SttModelVariant) {
        if (variant == selectedVariant || modelInstallationInProgress || modelLoadingInProgress || recorder != null) {
            return
        }
        selectedVariant = variant
        ModelSelection.store(this, variant)
        unloadCurrentEngine()
        val installer = installerFor(variant)
        if (installer.isInstalled()) {
            loadSelectedModel()
        } else {
            updateModelControls()
        }
    }

    private fun toggleFinnish() {
        if (modelInstallationInProgress || modelLoadingInProgress || recorder != null) return
        FinnishSetting.store(this, !finnishEnabled())
        unloadCurrentEngine()
        val installer = installerFor()
        if (installer.isInstalled()) {
            loadSelectedModel()
        } else {
            updateModelControls()
        }
    }

    private fun unloadCurrentEngine() {
        val local = engine
        val shared = SttEngineHolder.engine
        SttEngineHolder.clear()
        if (local != null && local !== shared) local.close()
        engine = null
    }

    private fun modelMenuItems(): List<Pair<SttModelVariant, Int>> = listOf(
        SttModelVariant.TINY to R.id.menu_model_tiny,
        SttModelVariant.BASE to R.id.menu_model_base,
        SttModelVariant.SMALL to R.id.menu_model_small,
    )

    private fun toggleRecording() {
        val active = recorder
        if (active == null) {
            if (!hasMicrophonePermission()) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
                return
            }
            startRecording()
        } else {
            stopRecording(active)
        }
    }

    private fun startRecording() {
        check(engine != null) { "The STT model is not loaded" }
        recordButton.isEnabled = false
        recordButton.setText(R.string.stop_recording)
        transcript.text = ""
        setModelStatus(getString(R.string.recording_starting))
        val active = LiveSttRecorder(checkNotNull(engine))
        recorder = active
        background.execute {
            runCatching { active.start() }.onSuccess {
                mainHandler.post {
                    recordButton.isEnabled = true
                    setModelStatus(getString(R.string.recording))
                }
            }.onFailure { error ->
                recorder = null
                mainHandler.post {
                    recordButton.setText(R.string.start_recording)
                    setModelStatus(getString(R.string.model_error, error.message ?: error.javaClass.simpleName))
                    updateModelControls()
                }
            }
        }
    }

    private fun stopRecording(active: LiveSttRecorder) {
        recordButton.isEnabled = false
        recorder = null
        setModelStatus(getString(R.string.finalizing))
        background.execute {
            runCatching { active.stopAndFinish() }.onSuccess { result ->
                mainHandler.post {
                    transcript.text = result.transcript
                    sampleMetrics.text = getString(
                        R.string.recording_complete,
                        result.audioSeconds,
                        result.inferenceSeconds,
                        result.realTimeFactor,
                        result.droppedFrames,
                    )
                    recordButton.setText(R.string.start_recording)
                    updateModelControls()
                }
            }.onFailure { error ->
                mainHandler.post {
                    recordButton.setText(R.string.start_recording)
                    setModelStatus(getString(R.string.model_error, error.message ?: error.javaClass.simpleName))
                    updateModelControls()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        }
    }

    private fun updateModelControls() {
        modelInstaller = installerFor()
        val installed = modelInstaller.isInstalled()
        selectedModelLabel.text = getString(R.string.selected_model, modelInstaller.displayName())
        finnishToggle.setText(
            if (finnishEnabled()) R.string.finnish_on else R.string.finnish_off,
        )
        val busy = modelInstallationInProgress || modelLoadingInProgress
        installModelButton.isEnabled = !busy && recorder == null && engine == null
        installModelButton.setText(if (installed) R.string.load_model else R.string.install_model)
        modelMenuButton.isEnabled = !busy && recorder == null
        finnishToggle.isEnabled = !busy && recorder == null
        recordButton.isEnabled = !busy && engine != null && recorder == null
        if (busy || recorder != null) return
        when {
            engine != null -> setModelStatus(getString(R.string.model_ready))
            installed -> setModelStatus(getString(R.string.model_installed))
            else -> setModelStatus(getString(R.string.model_not_installed))
        }
    }

    private fun finnishEnabled(): Boolean = FinnishSetting.load(this)

    private fun installerFor(variant: SttModelVariant = selectedVariant): SttModelInstaller =
        SttModelInstaller(this, variant, finnishEnabled())

    private fun setModelStatus(value: String) {
        modelStatus.text = value
    }

    private fun hasMicrophonePermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun isOverlayServiceEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC,
        )
        val expected = "$packageName/${DictationAccessibilityService::class.java.name}"
        return enabled.any { info ->
            info.resolveInfo.serviceInfo.let { service ->
                "${service.packageName}/${service.name}" == expected
            }
        }
    }

    private companion object {
        const val REQUEST_RECORD_AUDIO = 1
        const val BYTES_PER_MEBIBYTE = 1024L * 1024L
    }
}
