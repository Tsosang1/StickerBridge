package com.example.stickerbridge.processor


import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await

class SubjectRemover {

    private val options = SubjectSegmenterOptions.Builder()
        .enableForegroundBitmap()
        .build()

    private val segmenter = SubjectSegmentation.getClient(options)

    suspend fun extractSubject(bitmap: Bitmap): Bitmap? {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val result = segmenter.process(inputImage).await()
        return result.foregroundBitmap
    }
}