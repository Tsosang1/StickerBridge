package com.example.stickerbridge.processor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.github.penfeizhou.animation.gif.decode.GifDecoder
import com.github.penfeizhou.animation.loader.ByteBufferLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.min

class StickerProcessor(private val context: Context) {

    private val subjectRemover = SubjectRemover()

    suspend fun decodeBitmapFromUri(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        }.getOrNull()
    }

    suspend fun processGifUriToAnimatedPack(uri: Uri, fileName: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            val byteBuffer = ByteBuffer.wrap(bytes)
            val loader = object : ByteBufferLoader() {
                override fun getByteBuffer(): ByteBuffer = byteBuffer
            }
            val decoder = GifDecoder(loader, null)

            val frameCount = decoder.frameCount
            if (frameCount == 0) return@runCatching null

            val frames = mutableListOf<Bitmap>()
            val step = if (frameCount > 20) frameCount / 20 else 1

            for (i in 0 until frameCount step step) {
                val frameBitmap = decoder.getFrameBitmap(i)
                if (frameBitmap != null) {
                    frames.add(frameBitmap)
                }
            }

            processAndSaveAnimated(frames, fileName, frameDelayMs = 100)
        }.getOrNull()
    }

    suspend fun processAndSave(
        sourceBitmap: Bitmap,
        fileName: String,
        removeBackground: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        val targetBitmap = if (removeBackground) subjectRemover.extractSubject(sourceBitmap) ?: sourceBitmap else sourceBitmap
        val standardized = fitToWhatsAppCanvas(targetBitmap)

        // Save to the static pack folder
        val packDir = File(context.filesDir, "stickers/static").apply { mkdirs() }
        val outputFile = File(packDir, "$fileName.webp")

        FileOutputStream(outputFile).use { outStream ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                standardized.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, outStream)
            } else {
                @Suppress("DEPRECATION")
                standardized.compress(Bitmap.CompressFormat.WEBP, 90, outStream)
            }
        }
        outputFile
    }

    suspend fun processAndSaveAnimated(
        frames: List<Bitmap>,
        fileName: String,
        frameDelayMs: Int = 100
    ): File? = withContext(Dispatchers.IO) {
        if (frames.isEmpty()) return@withContext null

        // Save to the animated pack folder
        val packDir = File(context.filesDir, "stickers/animated").apply { mkdirs() }
        val outputFile = File(packDir, "$fileName.webp")

        val standardizedFrames = frames.map { fitToWhatsAppCanvas(it) }
        val webpBytes = compileAnimatedWebP(standardizedFrames, 512, 512, frameDelayMs)

        if (webpBytes.isNotEmpty()) {
            outputFile.writeBytes(webpBytes)
            outputFile
        } else null
    }

    private fun fitToWhatsAppCanvas(src: Bitmap): Bitmap {
        val targetSize = 512
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val scale = min(targetSize.toFloat() / src.width, targetSize.toFloat() / src.height)
        val scaledWidth = src.width * scale
        val scaledHeight = src.height * scale

        val left = (targetSize - scaledWidth) / 2f
        val top = (targetSize - scaledHeight) / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(left, top)
        }

        canvas.drawBitmap(src, matrix, paint)
        return output
    }

    fun ensureTrayIconsExist() {
        val staticDir = File(context.filesDir, "stickers/static").apply { mkdirs() }
        val animDir = File(context.filesDir, "stickers/animated").apply { mkdirs() }

        listOf(File(staticDir, "tray_icon.png"), File(animDir, "tray_icon.png")).forEach { trayFile ->
            if (!trayFile.exists()) {
                val iconBitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(iconBitmap)
                val paint = Paint().apply { color = Color.parseColor("#6200EE") }
                canvas.drawCircle(48f, 48f, 44f, paint)
                FileOutputStream(trayFile).use { out ->
                    iconBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        }
    }

    private fun compileAnimatedWebP(
        frames: List<Bitmap>,
        width: Int,
        height: Int,
        frameDelayMs: Int
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val body = ByteArrayOutputStream()

        // 1. VP8X Chunk (10 bytes)
        body.write("VP8X".toByteArray(Charsets.US_ASCII))
        body.write(intToLE32(10))
        body.write(byteArrayOf(0x12.toByte(), 0x00, 0x00, 0x00))
        body.write(intToLE24(width - 1))
        body.write(intToLE24(height - 1))

        // 2. ANIM Chunk (6 bytes)
        body.write("ANIM".toByteArray(Charsets.US_ASCII))
        body.write(intToLE32(6))
        body.write(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        body.write(shortToLE16(0))

        // 3. ANMF Chunks for frames
        for (frame in frames) {
            val frameStream = ByteArrayOutputStream()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                frame.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, frameStream)
            } else {
                @Suppress("DEPRECATION")
                frame.compress(Bitmap.CompressFormat.WEBP, 90, frameStream)
            }
            val frameBytes = frameStream.toByteArray()
            val frameChunks = extractWebPSubChunks(frameBytes)

            val anmfHeader = ByteArrayOutputStream()
            anmfHeader.write(intToLE24(0))
            anmfHeader.write(intToLE24(0))
            anmfHeader.write(intToLE24(width - 1))
            anmfHeader.write(intToLE24(height - 1))
            anmfHeader.write(intToLE24(frameDelayMs))
            anmfHeader.write(0x00)

            val anmfData = anmfHeader.toByteArray() + frameChunks

            body.write("ANMF".toByteArray(Charsets.US_ASCII))
            body.write(intToLE32(anmfData.size))
            body.write(anmfData)
            if (anmfData.size % 2 != 0) {
                body.write(0x00)
            }
        }

        val bodyBytes = body.toByteArray()
        baos.write("RIFF".toByteArray(Charsets.US_ASCII))
        baos.write(intToLE32(bodyBytes.size + 4))
        baos.write("WEBP".toByteArray(Charsets.US_ASCII))
        baos.write(bodyBytes)

        return baos.toByteArray()
    }

    private fun extractWebPSubChunks(frameBytes: ByteArray): ByteArray {
        if (frameBytes.size < 12) return byteArrayOf()
        val out = ByteArrayOutputStream()
        var offset = 12
        while (offset + 8 <= frameBytes.size) {
            val chunkTag = String(frameBytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = (frameBytes[offset + 4].toInt() and 0xFF) or
                    ((frameBytes[offset + 5].toInt() and 0xFF) shl 8) or
                    ((frameBytes[offset + 6].toInt() and 0xFF) shl 16) or
                    ((frameBytes[offset + 7].toInt() and 0xFF) shl 24)

            val totalChunkLength = 8 + chunkSize + (if (chunkSize % 2 != 0) 1 else 0)
            if (offset + totalChunkLength > frameBytes.size) break

            if (chunkTag == "VP8 " || chunkTag == "VP8L" || chunkTag == "ALPH") {
                out.write(frameBytes, offset, totalChunkLength)
            }
            offset += totalChunkLength
        }
        return out.toByteArray()
    }

    private fun intToLE32(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte()
    )

    private fun intToLE24(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte()
    )

    private fun shortToLE16(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte()
    )
}
