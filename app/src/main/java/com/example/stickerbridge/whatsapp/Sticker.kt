package com.example.stickerbridge.whatsapp

import android.os.Parcel
import android.os.Parcelable

data class Sticker(
    val imageFileName: String,
    val emojis: List<String> = emptyList()
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.createStringArrayList() ?: emptyList()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(imageFileName)
        parcel.writeStringList(emojis)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Sticker> {
        override fun createFromParcel(parcel: Parcel): Sticker = Sticker(parcel)
        override fun newArray(size: Int): Array<Sticker?> = arrayOfNulls(size)
    }
}

data class StickerPack(
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayImageFile: String,
    val publisherEmail: String = "",
    val publisherWebsite: String = "",
    val privacyPolicyWebsite: String = "",
    val licenseAgreementWebsite: String = "",
    val imageDataVersion: String = "1",
    val avoidCache: Boolean = false,
    val animatedStickerPack: Boolean = false,
    var stickers: List<Sticker> = emptyList()
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.createTypedArrayList(Sticker.CREATOR) ?: emptyList()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(identifier)
        parcel.writeString(name)
        parcel.writeString(publisher)
        parcel.writeString(trayImageFile)
        parcel.writeString(publisherEmail)
        parcel.writeString(publisherWebsite)
        parcel.writeString(privacyPolicyWebsite)
        parcel.writeString(licenseAgreementWebsite)
        parcel.writeString(imageDataVersion)
        parcel.writeByte(if (avoidCache) 1 else 0)
        parcel.writeByte(if (animatedStickerPack) 1 else 0)
        parcel.writeTypedList(stickers)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<StickerPack> {
        override fun createFromParcel(parcel: Parcel): StickerPack = StickerPack(parcel)
        override fun newArray(size: Int): Array<StickerPack?> = arrayOfNulls(size)
    }
}
