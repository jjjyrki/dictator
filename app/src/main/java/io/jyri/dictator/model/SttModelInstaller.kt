package io.jyri.dictator.model

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class SttModelInstaller(context: Context) {
    private val modelDirectory = File(context.filesDir, "models/stt-1b-en_fr-q8")

    fun directory(): File = modelDirectory

    fun isInstalled(): Boolean = assets.all { asset ->
        File(modelDirectory, asset.fileName).length() == asset.sizeBytes
    }

    fun install(onProgress: (Progress) -> Unit) {
        modelDirectory.mkdirs()
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
            setRequestProperty("User-Agent", "Dictator/0.1")
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

        const val OFFICIAL_REVISION = "095e38f6242006a93c2541149b181988397f5c7c"
        const val QUANTIZED_REVISION = "177aac749e0afc58d20dc5ec20814ef7b766a1d2"

        val assets = listOf(
            Asset(
                fileName = "model.q8_0.gguf",
                sizeBytes = 1_051_290_688,
                sha256 = "7bbceaf823610ba1d33bc6b1218105ade3ebd02177a3234f3950e0ca5e7c5c0c",
                url = "https://huggingface.co/stephvax/kyutai-stt-1b-en_fr-candle-gguf/resolve/$QUANTIZED_REVISION/model.q8_0.gguf?download=true",
            ),
            Asset(
                fileName = "mimi-pytorch-e351c8d8@125.safetensors",
                sizeBytes = 384_644_900,
                sha256 = "09b782f0629851a271227fb9d36db65c041790365f11bbe5d3d59369cf863f50",
                url = "https://huggingface.co/kyutai/stt-1b-en_fr-candle/resolve/$OFFICIAL_REVISION/mimi-pytorch-e351c8d8%40125.safetensors?download=true",
            ),
            Asset(
                fileName = "config.json",
                sizeBytes = 1_315,
                sha256 = "a3f1c6f7a39fca1fb1bbff68eaabc560b8037d2cdc68aa1f489859949a4223de",
                url = "https://huggingface.co/kyutai/stt-1b-en_fr-candle/resolve/$OFFICIAL_REVISION/config.json?download=true",
            ),
            Asset(
                fileName = "tokenizer_en_fr_audio_8000.model",
                sizeBytes = 120_378,
                sha256 = "cd87dd5d17169151782ac700280ec057e5d658a9afbe238a048ea5ff318cce69",
                url = "https://huggingface.co/kyutai/stt-1b-en_fr-candle/resolve/$OFFICIAL_REVISION/tokenizer_en_fr_audio_8000.model?download=true",
            ),
        )
    }
}
