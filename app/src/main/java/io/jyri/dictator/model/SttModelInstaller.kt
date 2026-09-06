package io.jyri.dictator.model

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class SttModelInstaller(context: Context, private val model: SttModelAsset) {
    constructor(context: Context, variant: SttModelVariant, finnish: Boolean) :
        this(context, variant.asset(finnish))

    private val modelDirectory = File(context.filesDir, "models/${model.directoryName}")

    fun directory(): File = modelDirectory

    fun modelFile(): File = File(modelDirectory, model.fileName)

    fun language(): String = model.language

    fun displayName(): String = model.displayName

    fun isInstalled(): Boolean = assets.all { asset ->
        File(modelDirectory, asset.fileName).length() == asset.sizeBytes
    }

    fun install(onProgress: (Progress) -> Unit) {
        modelDirectory.mkdirs()
        val expected = assets.map { it.fileName }.toSet()
        modelDirectory.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in expected) file.delete()
        }
        assets.forEachIndexed { index, asset ->
            val destination = File(modelDirectory, asset.fileName)
            if (destination.length() == asset.sizeBytes && sha256(destination) == asset.sha256) {
                onProgress(Progress(index + 1, assets.size, asset.fileName, asset.sizeBytes, asset.sizeBytes))
            } else {
                download(asset, destination) { downloaded ->
                    onProgress(Progress(index + 1, assets.size, asset.fileName, downloaded, asset.sizeBytes))
                }
            }
        }
    }

    private fun download(asset: Asset, destination: File, onProgress: (Long) -> Unit) {
        val partial = File(destination.parentFile, "${destination.name}.partial")
        if (partial.length() >= asset.sizeBytes) partial.delete()
        val existingBytes = partial.length()
        val connection = (URL(asset.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Dictator/0.2")
            if (existingBytes > 0) setRequestProperty("Range", "bytes=$existingBytes-")
        }
        try {
            val append = existingBytes > 0 && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            val startingBytes = if (append) existingBytes else 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var downloaded = startingBytes
                    onProgress(downloaded)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded)
                    }
                    output.fd.sync()
                }
            }
            if (partial.length() != asset.sizeBytes || sha256(partial) != asset.sha256) {
                partial.delete()
                error("${asset.fileName} failed its size or SHA-256 check")
            }
            if (destination.exists()) destination.delete()
            check(partial.renameTo(destination)) { "Could not finish ${asset.fileName}" }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    data class Progress(
        val assetIndex: Int,
        val assetCount: Int,
        val fileName: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
    )

    private val assets = listOf(
        Asset(
            fileName = model.fileName,
            sizeBytes = model.sizeBytes,
            sha256 = model.sha256,
            url = model.downloadUrl,
        ),
    )

    private data class Asset(
        val fileName: String,
        val sizeBytes: Long,
        val sha256: String,
        val url: String,
    )

    private companion object {
        const val BUFFER_BYTES = 1024 * 1024
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
