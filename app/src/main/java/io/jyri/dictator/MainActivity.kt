package io.jyri.dictator

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import io.jyri.dictator.audio.LiveSttRecorder
import io.jyri.dictator.insert.InsertMode
import io.jyri.dictator.model.LanguageSelection
import io.jyri.dictator.model.ModelSelection
import io.jyri.dictator.model.SpokenLanguage
import io.jyri.dictator.model.SttModelInstaller
import io.jyri.dictator.model.SttModelProfile
import io.jyri.dictator.speech.PartialsSetting
import io.jyri.dictator.speech.SttEngineHolder
import io.jyri.dictator.speech.WhisperLanguageConfig
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
    private lateinit var languageMenuButton: Button

    private var selectedModel: SttModelProfile = SttModelProfile.default
    private var engine: WhisperSttEngine? = null
    private var recorder: LiveSttRecorder? = null
    private var modelInstallationInProgress = false
    private var modelLoadingInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedModel = ModelSelection.load(this)
        setContentView(R.layout.activity_main)
        modelStatus = findViewById(R.id.modelStatus)
        selectedModelLabel = findViewById(R.id.selectedModel)
        sampleMetrics = findViewById(R.id.sampleMetrics)
        transcript = findViewById(R.id.transcript)
        recordButton = findViewById(R.id.record)
        installModelButton = findViewById(R.id.installModel)
        modelMenuButton = findViewById(R.id.modelMenu)
        languageMenuButton = findViewById(R.id.languageMenu)
        modelInstaller = installerFor()

        modelMenuButton.setOnClickListener { showModelMenu() }
        languageMenuButton.setOnClickListener { showLanguageDialog() }
        findViewById<Button>(R.id.openAccessibility).setOnClickListener {
            showAccessibilityDisclosure()
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
        languageMenuButton.isEnabled = false
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
        val languages = activeLanguages()
        val shared = SttEngineHolder.engine
        if (shared != null && SttEngineHolder.matches(selectedModel, languages)) {
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
        val model = selectedModel
        val languages = activeLanguages()
        val installer = installerFor(model)
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
                WhisperSttEngine(
                    installer.modelFile(),
                    WhisperLanguageConfig.forModel(model, languages),
                )
            }.onSuccess { created ->
                mainHandler.post {
                    // Model choices are disabled while loading, but avoid
                    // installing a stale result if the Activity is recreated.
                    if (selectedModel != model || activeLanguages() != languages) {
                        modelLoadingInProgress = false
                        created.close()
                        updateModelControls()
                        return@post
                    }
                    SttEngineHolder.install(model, languages, created)
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
        modelMenuItems().forEach { (model, itemId) ->
            val item = popup.menu.findItem(itemId)
            item.isChecked = model == selectedModel
            val installed = SttModelInstaller(this, model.asset).isInstalled()
            item.title = getString(
                if (installed) R.string.model_menu_installed else R.string.model_menu_not_installed,
                model.displayName,
            )
        }
        popup.setOnMenuItemClickListener { item ->
            val model = modelMenuItems().firstOrNull { it.second == item.itemId }?.first
                ?: return@setOnMenuItemClickListener false
            item.isChecked = true
            selectModel(model)
            true
        }
        popup.show()
    }

    private fun showLanguageDialog() {
        if (!selectedModel.isMultilingual || modelInstallationInProgress || modelLoadingInProgress || recorder != null) {
            return
        }
        val selected = activeLanguages().toMutableSet()
        val languages = SpokenLanguage.entries
        val labels = languages.map(::languageLabel).toTypedArray()
        val checked = languages.map { it in selected }.toBooleanArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.language_selection_title)
            .setMessage(R.string.language_selection_body)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val language = languages[which]
                if (isChecked) selected += language else selected -= language
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.done, null)
            .create()
        dialog.setOnShowListener {
            val done = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            done.isEnabled = selected.isNotEmpty()
            done.setOnClickListener {
                if (selected.isEmpty()) {
                    Toast.makeText(this, R.string.language_selection_requires_one, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                LanguageSelection.store(this, selected)
                unloadCurrentEngine()
                if (modelInstaller.isInstalled()) loadSelectedModel() else updateModelControls()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun selectModel(model: SttModelProfile) {
        if (model == selectedModel || modelInstallationInProgress || modelLoadingInProgress || recorder != null) {
            return
        }
        selectedModel = model
        ModelSelection.store(this, model)
        unloadCurrentEngine()
        val installer = installerFor(model)
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

    private fun modelMenuItems(): List<Pair<SttModelProfile, Int>> = listOf(
        SttModelProfile.ENGLISH_TINY to R.id.menu_model_english_tiny,
        SttModelProfile.ENGLISH_BASE to R.id.menu_model_english_base,
        SttModelProfile.ENGLISH_SMALL to R.id.menu_model_english_small,
        SttModelProfile.MULTILINGUAL_TINY to R.id.menu_model_multilingual_tiny,
        SttModelProfile.MULTILINGUAL_BASE to R.id.menu_model_multilingual_base,
        SttModelProfile.MULTILINGUAL_SMALL to R.id.menu_model_multilingual_small,
    )

    private fun toggleRecording() {
        val active = recorder
        if (active == null) {
            if (!hasMicrophonePermission()) {
                requestMicrophonePermission()
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
                    transcript.text = result.transcript.ifBlank { getString(R.string.no_speech_detected) }
                    sampleMetrics.text = getString(
                        R.string.recording_complete,
                        result.audioSeconds,
                        result.inferenceSeconds,
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
        if (requestCode != REQUEST_RECORD_AUDIO) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            setModelStatus(getString(R.string.microphone_permission_denied))
        }
    }

    private fun requestMicrophonePermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.microphone_permission_rationale_title)
                .setMessage(R.string.microphone_permission_rationale)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
                }
                .show()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }
    }

    private fun showAccessibilityDisclosure() {
        AlertDialog.Builder(this)
            .setTitle(R.string.accessibility_disclosure_title)
            .setMessage(R.string.accessibility_disclosure_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.continue_to_accessibility_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .show()
    }

    private fun updateModelControls() {
        modelInstaller = installerFor()
        val installed = modelInstaller.isInstalled()
        val languages = activeLanguages()
        selectedModelLabel.text = getString(R.string.selected_model, modelInstaller.displayName())
        languageMenuButton.visibility = if (selectedModel.isMultilingual) View.VISIBLE else View.GONE
        if (selectedModel.isMultilingual) {
            languageMenuButton.text = getString(R.string.selected_languages, languageSummary(languages))
        }
        val busy = modelInstallationInProgress || modelLoadingInProgress
        installModelButton.isEnabled = !busy && recorder == null && engine == null
        installModelButton.setText(if (installed) R.string.load_model else R.string.install_model)
        modelMenuButton.isEnabled = !busy && recorder == null
        languageMenuButton.isEnabled = !busy && recorder == null
        recordButton.isEnabled = !busy && engine != null && recorder == null
        if (busy || recorder != null) return
        when {
            engine != null -> setModelStatus(getString(R.string.model_ready))
            installed -> setModelStatus(getString(R.string.model_installed))
            else -> setModelStatus(getString(R.string.model_not_installed))
        }
    }

    private fun activeLanguages(): Set<SpokenLanguage> =
        LanguageSelection.loadFor(this, selectedModel)

    private fun languageSummary(languages: Set<SpokenLanguage>): String =
        SpokenLanguage.entries
            .filter { it in languages }
            .joinToString(", ", transform = ::languageLabel)

    private fun languageLabel(language: SpokenLanguage): String =
        getString(
            when (language) {
                SpokenLanguage.ENGLISH -> R.string.language_english
                SpokenLanguage.FINNISH -> R.string.language_finnish
            },
        )

    private fun installerFor(model: SttModelProfile = selectedModel): SttModelInstaller =
        SttModelInstaller(this, model.asset)

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
