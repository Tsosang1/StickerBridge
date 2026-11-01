package com.example.stickerbridge.processor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ExportHelper {

    fun saveToGallery(context: Context, file: File) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/webp")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/StickerBridge")
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().copyTo(out)
            }
            Toast.makeText(context, "Saved to Gallery / Pictures!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Failed to save to Gallery", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareToInstagramStory(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "com.example.stickerbridge.fileprovider", file)
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(uri, "image/webp")
            putExtra("interactive_asset_uri", uri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.grantUriPermission("com.instagram.android", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Instagram app not found", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyToClipboard(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "com.example.stickerbridge.fileprovider", file)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newUri(context.contentResolver, "Sticker", uri)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied! Paste directly into Gboard/Samsung Keyboard chats", Toast.LENGTH_SHORT).show()
    }

    fun shareToAnyApp(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "com.example.stickerbridge.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/webp"
            putExtra(Intent.EXTRA_STREAM, uri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(Intent.createChooser(intent, "Send sticker to..."))
    }
}