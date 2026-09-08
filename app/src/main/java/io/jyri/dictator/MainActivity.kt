package io.jyri.dictator

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.jyri.dictator.audio.LiveSttRecorder
import io.jyri.dictator.insert.InsertMode
import io.jyri.dictator.overlay.WaveChipView
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
import kotlin.math.roundToInt

class MainActivity : android.app.Activity() {
    private val background: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var modelInstaller: SttModelInstaller
    private lateinit var modelStatus: TextView
    private lateinit var selectedModelLabel: TextView
    private lateinit var sampleMetrics: TextView
    private lateinit var transcript: TextView
    private lateinit var recordButton: MaterialButton
    private lateinit var installModelButton: MaterialButton
    private lateinit var modelDownloadProgress: ProgressBar
    private lateinit var sampleWaveform: WaveChipView
    private lateinit var microphoneStepHeading: View
    private lateinit var microphoneBanner: View
    private lateinit var accessibilityStepHeading: View
    private lateinit var accessibilityStep: View
    private lateinit var testStepHeading: View
    private lateinit var testStep: View
    private lateinit var preferencesSection: View
    private lateinit var modelMenuButton: MaterialButton
    private lateinit var languageMenuButton: MaterialButton

    private var selectedModel: SttModelProfile = SttModelProfile.default
    private var engine: WhisperSttEngine? = null
    private var recorder: LiveSttRecorder? = null
    private var modelInstallationInProgress = false
    private var modelLoadingInProgress = false
    private var startSampleAfterPermission = false
    private var sampleTestCompleted = false

    private val setupPreferences by lazy {
        getSharedPreferences(SETUP_PREFERENCES, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedModel = ModelSelection.load(this)
        sampleTestCompleted = hasCompletedSampleTest(selectedModel)
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.mainContent).applySystemBarInsets()
        modelStatus = findViewById(R.id.modelStatus)
        selectedModelLabel = findViewById(R.id.selectedModel)
        sampleMetrics = findViewById(R.id.sampleMetrics)
        transcript = findViewById(R.id.transcript)
        recordButton = findViewById(R.id.record)
        installModelButton = findViewById(R.id.installModel)
        modelDownloadProgress = findViewById(R.id.modelDownloadProgress)
        sampleWaveform = findViewById<WaveChipView>(R.id.sampleWaveform).apply {
            setBarColor(getColor(R.color.setup_accent_soft))
        }
        microphoneStepHeading = findViewById(R.id.microphoneStepHeading)
        microphoneBanner = findViewById(R.id.microphoneBanner)
        accessibilityStepHeading = findViewById(R.id.accessibilityStepHeading)
        accessibilityStep = findViewById(R.id.accessibilityStep)
        testStepHeading = findViewById(R.id.testStepHeading)
        testStep = findViewById(R.id.testStep)
        preferencesSection = findViewById(R.id.preferencesSection)
        findViewById<MaterialButton>(R.id.microphoneBannerAction).setOnClickListener {
            openAppSettings()
        }
        modelMenuButton = findViewById(R.id.modelMenu)
        languageMenuButton = findViewById(R.id.languageMenu)
        modelInstaller = installerFor()

        modelMenuButton.setOnClickListener { showModelMenu() }
        languageMenuButton.setOnClickListener { showLanguageDialog() }
        findViewById<MaterialButton>(R.id.openAccessibility).setOnClickListener {
            showAccessibilityDisclosure()
        }
        findViewById<MaterialButton>(R.id.licenses).setOnClickListener {
            startActivity(Intent(this, LicensesActivity::class.java))
        }
        installModelButton.setOnClickListener { installModel() }
        val toggleInsertMode = findViewById<MaterialButton>(R.id.toggleInsertMode)
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
        val togglePartials = findViewById<MaterialButton>(R.id.togglePartials)
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
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val status = findViewById<TextView>(R.id.accessibilityStatus)
        if (isOverlayServiceEnabled()) {
            status.setText(R.string.accessibility_enabled)
            status.setTextColor(getColor(R.color.setup_ok))
        } else {
            status.setText(R.string.accessibility_disabled)
            status.setTextColor(getColor(R.color.setup_muted))
        }
        updateModelControls()
    }

    override fun onDestroy() {
        recorder?.let { active ->
            active.setLevelListener(null)
            sampleWaveform.setActive(false)
            sampleWaveform.visibility = View.GONE
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
        modelDownloadProgress.progress = 0
        updateModelControls()
        setModelStatus(getString(R.string.model_installing))
        val installer = modelInstaller
        background.execute {
            runCatching {
                installer.install { progress ->
                    mainHandler.post {
                        val percent = if (progress.totalBytes > 0) {
                            (progress.downloadedBytes * 100L / progress.totalBytes)
                                .toInt()
                                .coerceIn(0, 100)
                        } else {
                            0
                        }
                        modelDownloadProgress.setProgress(percent, true)
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
                    updateModelControls()
                    setModelStatus(getString(R.string.model_installed))
                    loadSelectedModel()
                }
            }.onFailure { error ->
                mainHandler.post {
                    modelInstallationInProgress = false
                    updateModelControls()
                    setModelStatus(getString(R.string.model_error, error.message ?: error.javaClass.simpleName))
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
        updateModelControls()
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
                    updateModelControls()
                    setModelStatus(getString(R.string.model_error, error.message ?: error.javaClass.simpleName))
                }
            }
        }
    }

    private fun showModelMenu() {
        if (modelInstallationInProgress || modelLoadingInProgress || recorder != null) return

        val modelList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        val modelsByLanguage = modelMenuModels().groupBy { it.asset.languageName }
        var dialog: androidx.appcompat.app.AlertDialog? = null
        modelsByLanguage.entries.forEachIndexed { groupIndex, (language, models) ->
            if (groupIndex > 0) {
                modelList.addView(
                    View(this).apply { setBackgroundColor(getColor(R.color.setup_border)) },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1),
                    ).apply {
                        topMargin = dp(8)
                        bottomMargin = dp(8)
                    },
                )
            }
            modelList.addView(
                TextView(this).apply {
                    text = language
                    setTextColor(getColor(R.color.setup_accent_soft))
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            models.forEach { model ->
                val selected = model == selectedModel
                val title = "${model.asset.modelName} · ${model.asset.sizeLabel}"
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    isFocusable = true
                    minimumHeight = dp(52)
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    background = modelRowBackground(selected)
                    contentDescription = getString(
                        if (selected) R.string.model_menu_selected else R.string.model_menu_option,
                        language,
                        title,
                        model.asset.usageDescription,
                    )
                }
                val labels = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                labels.addView(
                    TextView(this).apply {
                        text = title
                        setTextColor(getColor(if (selected) R.color.setup_bg else R.color.setup_text))
                        textSize = 16f
                        typeface = Typeface.DEFAULT_BOLD
                    },
                )
                labels.addView(
                    TextView(this).apply {
                        text = model.asset.usageDescription
                        setTextColor(getColor(if (selected) R.color.setup_bg else R.color.setup_muted))
                        textSize = 12f
                    },
                )
                row.addView(
                    labels,
                    LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f,
                    ),
                )
                row.addView(
                    TextView(this).apply {
                        text = "✓"
                        setTextColor(getColor(R.color.setup_bg))
                        textSize = 18f
                        visibility = if (selected) View.VISIBLE else View.INVISIBLE
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                row.setOnClickListener {
                    selectModel(model)
                    dialog?.dismiss()
                }
                modelList.addView(
                    row,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(4) },
                )
            }
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(modelList)
        }
        val createdDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.model_selection_title)
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog = createdDialog
        createdDialog.show()
    }

    private fun modelRowBackground(selected: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(14).toFloat()
        setColor(getColor(if (selected) R.color.setup_accent else R.color.setup_inset))
        setStroke(
            dp(1),
            getColor(if (selected) R.color.setup_accent_soft else R.color.setup_border),
        )
    }

    private fun showLanguageDialog() {
        if (!selectedModel.isMultilingual || modelInstallationInProgress || modelLoadingInProgress || recorder != null) {
            return
        }
        val selected = activeLanguages().toMutableSet()
        val languages = SpokenLanguage.entries
        val searchInput = TextInputEditText(this).apply {
            id = R.id.languageSearch
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
        }
        val searchField = TextInputLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            hint = getString(R.string.language_selection_search_hint)
            addView(searchInput)
        }
        val languageList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val languageScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(
                languageList,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(searchField)
            addView(
                languageScroll,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(360),
                ).apply {
                    topMargin = dp(8)
                },
            )
        }
        var refreshDoneButton: (() -> Unit)? = null

        fun renderLanguages(query: String) {
            languageList.removeAllViews()
            val normalizedQuery = query.trim()
            val matches = languages.filter { language ->
                normalizedQuery.isBlank() ||
                    language.displayName.contains(normalizedQuery, ignoreCase = true) ||
                    language.whisperCode.contains(normalizedQuery, ignoreCase = true)
            }
            if (matches.isEmpty()) {
                languageList.addView(
                    TextView(this).apply {
                        setText(R.string.language_selection_no_matches)
                        setTextColor(getColor(R.color.setup_muted))
                        setPadding(dp(16), dp(16), dp(16), dp(16))
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                return
            }
            matches.forEach { language ->
                val checkbox = CheckBox(this).apply {
                    text = languageLabel(language)
                    isChecked = language in selected
                    minHeight = dp(48)
                }
                checkbox.setOnCheckedChangeListener { button, isChecked ->
                    when {
                        isChecked && language !in selected &&
                            selected.size >= SpokenLanguage.MAX_SELECTED_LANGUAGES -> {
                            button.isChecked = false
                            Toast.makeText(
                                this,
                                R.string.language_selection_maximum,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        isChecked -> selected += language
                        else -> selected -= language
                    }
                    refreshDoneButton?.invoke()
                }
                languageList.addView(
                    checkbox,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        }

        renderLanguages("")
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderLanguages(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.language_selection_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.done, null)
            .create()
        refreshDoneButton = {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = selected.isNotEmpty()
        }
        dialog.setOnShowListener {
            refreshDoneButton?.invoke()
            val done = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
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
        sampleTestCompleted = hasCompletedSampleTest(model)
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

    private fun modelMenuModels(): List<SttModelProfile> = listOf(
        SttModelProfile.ENGLISH_TINY,
        SttModelProfile.ENGLISH_BASE,
        SttModelProfile.ENGLISH_SMALL,
        SttModelProfile.MULTILINGUAL_TINY,
        SttModelProfile.MULTILINGUAL_BASE,
        SttModelProfile.MULTILINGUAL_SMALL,
    )

    private fun toggleRecording() {
        val active = recorder
        if (active == null) {
            if (!hasMicrophonePermission()) {
                requestMicrophonePermission(startSampleAfterPermission = true)
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
        active.setLevelListener { level ->
            mainHandler.post {
                if (recorder === active) sampleWaveform.setLevel(level)
            }
        }
        recorder = active
        sampleWaveform.visibility = View.VISIBLE
        sampleWaveform.setActive(true)
        background.execute {
            runCatching { active.start() }.onSuccess {
                mainHandler.post {
                    recordButton.isEnabled = true
                    setModelStatus(getString(R.string.recording))
                }
            }.onFailure { error ->
                recorder = null
                active.setLevelListener(null)
                mainHandler.post {
                    sampleWaveform.setActive(false)
                    sampleWaveform.visibility = View.GONE
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
        active.setLevelListener(null)
        sampleWaveform.setActive(false)
        sampleWaveform.visibility = View.GONE
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
                    sampleTestCompleted = true
                    setupPreferences.edit()
                        .putString(TESTED_MODEL_KEY, selectedModel.name)
                        .apply()
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
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        val startSample = startSampleAfterPermission
        startSampleAfterPermission = false
        if (granted) {
            if (startSample) startRecording() else updateModelControls()
        } else {
            setModelStatus(getString(R.string.microphone_permission_denied))
            updateModelControls()
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_REQUEST_MICROPHONE_PERMISSION, false) != true) return
        intent.removeExtra(EXTRA_REQUEST_MICROPHONE_PERMISSION)
        if (hasMicrophonePermission()) return
        setModelStatus(getString(R.string.microphone_permission_required))
        requestMicrophonePermission(startSampleAfterPermission = false)
    }

    private fun requestMicrophonePermission(startSampleAfterPermission: Boolean) {
        this.startSampleAfterPermission = startSampleAfterPermission
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            MaterialAlertDialogBuilder(this)
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
        MaterialAlertDialogBuilder(this)
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
        installModelButton.visibility = if (!busy && engine == null) View.VISIBLE else View.GONE
        modelDownloadProgress.visibility =
            if (modelInstallationInProgress) View.VISIBLE else View.GONE
        installModelButton.isEnabled = !busy && recorder == null && engine == null
        installModelButton.setText(if (installed) R.string.load_model else R.string.install_model)
        modelMenuButton.isEnabled = !busy && recorder == null
        languageMenuButton.isEnabled = !busy && recorder == null
        recordButton.isEnabled = !busy && engine != null && recorder == null
        updateSetupSteps(installed)
        if (busy || recorder != null) return
        when {
            engine != null -> setModelStatus(getString(R.string.model_ready))
            installed -> setModelStatus(getString(R.string.model_installed))
            else -> setModelStatus(getString(R.string.model_not_installed))
        }
    }

    private fun updateSetupSteps(modelDownloaded: Boolean) {
        val microphoneGranted = hasMicrophonePermission()
        val accessibilityEnabled = isOverlayServiceEnabled()
        val showMicrophoneStep = modelDownloaded && !microphoneGranted
        val showAccessibilityStep = modelDownloaded && microphoneGranted && !accessibilityEnabled
        val showTestStep = modelDownloaded && microphoneGranted && accessibilityEnabled

        microphoneStepHeading.visibility =
            if (showMicrophoneStep) View.VISIBLE else View.GONE
        microphoneBanner.visibility =
            if (showMicrophoneStep) View.VISIBLE else View.GONE
        accessibilityStepHeading.visibility =
            if (showAccessibilityStep) View.VISIBLE else View.GONE
        accessibilityStep.visibility =
            if (showAccessibilityStep) View.VISIBLE else View.GONE
        testStepHeading.visibility = if (showTestStep) View.VISIBLE else View.GONE
        testStep.visibility = if (showTestStep) View.VISIBLE else View.GONE
        preferencesSection.visibility =
            if (showTestStep && sampleTestCompleted) View.VISIBLE else View.GONE
    }

    private fun hasCompletedSampleTest(model: SttModelProfile): Boolean =
        setupPreferences.getString(TESTED_MODEL_KEY, null) == model.name

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun activeLanguages(): Set<SpokenLanguage> =
        LanguageSelection.loadFor(this, selectedModel)

    private fun languageSummary(languages: Set<SpokenLanguage>): String =
        SpokenLanguage.entries
            .filter { it in languages }
            .joinToString(", ", transform = ::languageLabel)

    private fun languageLabel(language: SpokenLanguage): String = language.displayName

    private fun installerFor(model: SttModelProfile = selectedModel): SttModelInstaller =
        SttModelInstaller(this, model.asset)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

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

    companion object {
        const val EXTRA_REQUEST_MICROPHONE_PERMISSION =
            "io.jyri.dictator.request_microphone_permission"
        private const val REQUEST_RECORD_AUDIO = 1
        private const val BYTES_PER_MEBIBYTE = 1024L * 1024L
        private const val SETUP_PREFERENCES = "setup_progress"
        private const val TESTED_MODEL_KEY = "tested_model"
    }
}
