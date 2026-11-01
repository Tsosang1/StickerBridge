package com.example.stickerbridge.whatsapp

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast

object WhatsAppLauncher {

    const val ADD_PACK_REQUEST_CODE = 200

    fun addStickerPack(
        activity: Activity,
        packIdentifier: String = "tiktok_imports",
        packName: String = "TikTok Imports"
    ) {
        val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
            putExtra("sticker_pack_id", packIdentifier)
            putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
            putExtra("sticker_pack_name", packName)
        }

        try {
            activity.startActivityForResult(intent, ADD_PACK_REQUEST_CODE)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
        }
    }
}
