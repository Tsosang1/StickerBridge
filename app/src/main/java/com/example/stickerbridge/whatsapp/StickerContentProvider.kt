package com.example.stickerbridge.whatsapp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

class StickerContentProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.example.stickerbridge.stickercontentprovider"
        private const val METADATA_CODE = 1
        private const val METADATA_PACK_CODE = 2
        private const val STICKERS_CODE = 3
        private const val STICKERS_ASSET_CODE = 4

        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "metadata", METADATA_CODE)
            addURI(AUTHORITY, "metadata/*", METADATA_PACK_CODE)
            addURI(AUTHORITY, "stickers/*", STICKERS_CODE)
            addURI(AUTHORITY, "stickers_asset/*/*", STICKERS_ASSET_CODE)
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val match = MATCHER.match(uri)
        return when (match) {
            METADATA_CODE -> getPackMetadataCursor()
            STICKERS_CODE -> getStickersCursor(uri.lastPathSegment ?: "")
            else -> null
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        if (MATCHER.match(uri) == STICKERS_ASSET_CODE) {
            val segments = uri.pathSegments
            val packId = segments[1]
            val fileName = segments[2]

            val subFolder = if (packId == "bridge_animated") "animated" else "static"
            val file = File(context?.filesDir, "stickers/$subFolder/$fileName")

            if (file.exists()) {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
            }
        }
        return super.openAssetFile(uri, mode)
    }

    override fun getType(uri: Uri): String? = when (MATCHER.match(uri)) {
        METADATA_CODE -> "vnd.android.cursor.dir/vnd.$AUTHORITY.metadata"
        METADATA_PACK_CODE -> "vnd.android.cursor.item/vnd.$AUTHORITY.metadata"
        STICKERS_CODE -> "vnd.android.cursor.dir/vnd.$AUTHORITY.stickers"
        STICKERS_ASSET_CODE -> "image/webp"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, sArgs: Array<out String>?): Int = 0

    private fun getPackMetadataCursor(): Cursor {
        val cursor = MatrixCursor(arrayOf(
            "sticker_pack_identifier",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_store_link",
            "is_animated_pack"
        ))

        // Expose both packs to WhatsApp
        cursor.addRow(arrayOf<Any>("bridge_static", "StickerBridge Static", "StickerBridge", "tray_icon.png", "", "", 0))
        cursor.addRow(arrayOf<Any>("bridge_animated", "StickerBridge Animated", "StickerBridge", "tray_icon.png", "", "", 1))
        return cursor
    }

    private fun getStickersCursor(packId: String): Cursor {
        val cursor = MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji"))
        val subFolder = if (packId == "bridge_animated") "animated" else "static"
        val dir = File(context?.filesDir, "stickers/$subFolder")

        dir.listFiles { f -> f.extension == "webp" }?.forEach { file ->
            cursor.addRow(arrayOf(file.name, "😎"))
        }
        return cursor
    }
}
