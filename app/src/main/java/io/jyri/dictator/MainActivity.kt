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
import android.widget.TextView
import io.jyri.dictator.audio.LiveSttRecorder
import io.jyri.dictator.model.SttModelInstaller
import io.jyri.dictator.speech.NativeSttBridge
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : android.app.Activity() {
    private val background: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var modelInstaller: SttModelInstaller
    private lateinit var modelStatus: TextView
    private lateinit var transcript: TextView
    private lateinit var recordButton: Button
    private lateinit var loadModelButton: Button

    private var nativeHandle = 0L
    private var recorder: LiveSttRecorder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        modelInstaller = SttModelInstaller(this)
        modelStatus = findViewById(R.id.modelStatus)
        transcript = findViewById(R.id.transcript)
        recordButton = findViewById(R.id.record)
        loadModelButton = findViewById(R.id.loadModel)

        findViewById<Button>(R.id.openAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.installModel).setOnClickListener { installModel() }
        loadModelButton.setOnClickListener { loadModel() }
        recordButton.setOnClickListener { toggleRecording() }
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
        if (nativeHandle != 0L) {
            val handle = nativeHandle
            nativeHandle = 0L
            background.execute { NativeSttBridge.nativeClose(handle) }
        }
        background.shutdown()
        super.onDestroy()
    }

    private fun installModel() {
        findViewById<Button>(R.id.installModel).isEnabled = false
        loadModelButton.isEnabled = false
        setModelStatus(getString(R.string.model_installing))
        background.execute {
            runCatching {
                modelInstaller.install { progress ->
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
                    setModelStatus(getString(R.string.model_installed))
                    updateModelControls()
                }
            }.onFailure { error ->
                mainHandler.post {
                    setModelStatus(getString(R.string.model_error, error.message ?: error.javaClass.simpleName))
                    updateModelControls()
                }
            }
        }
    }

    private fun loadModel() {
        loadModelButton.isEnabled = false
        setModelStatus(getString(R.string.model_loading))
        background.execute {
            runCatching {
                NativeSttBridge.nativeCreate(modelInstaller.directory().absolutePath)
            }.onSuccess { handle ->
                mainHandler.post {
                    if (handle == 0L) {
                        setModelStatus(getString(R.string.model_error, "Native engine did not start"))
                    } else {
                        nativeHandle = handle
                        setModelStatus(getString(R.string.model_ready))
                    }
                    updateModelControls()
                }
            }.onFailure { error ->
                mainHandler.post {
                    setModelStatus(getString(R.string.model_error, error.message ?: error.javaClass.simpleName))
                    updateModelControls()
                }
            }
        }
    }

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
        check(nativeHandle != 0L) { "The STT model is not loaded" }
        recordButton.isEnabled = false
        recordButton.setText(R.string.stop_recording)
        transcript.text = ""
        setModelStatus(getString(R.string.recording_starting))
        val active = LiveSttRecorder(nativeHandle) { partial ->
            mainHandler.post { transcript.text = partial }
        }
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
                    setModelStatus(
                        getString(
                            R.string.recording_complete,
                            result.audioSeconds,
                            result.inferenceSeconds,
                            result.realTimeFactor,
                            result.droppedFrames,
                        ),
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
        val installed = modelInstaller.isInstalled()
        findViewById<Button>(R.id.installModel).isEnabled = nativeHandle == 0L
        loadModelButton.isEnabled = installed && nativeHandle == 0L
        recordButton.isEnabled = nativeHandle != 0L && recorder == null
        if (nativeHandle == 0L) {
            setModelStatus(
                getString(if (installed) R.string.model_installed else R.string.model_not_installed),
            )
        }
    }

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
