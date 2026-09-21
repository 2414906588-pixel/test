package com.raweditor.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.InputStream

/**
 * Decodes an image (JPEG/PNG or DNG/RAW) from a content Uri into a Bitmap.
 *
 * For DNG files, Android's media stack (ImageDecoder / BitmapFactory) can decode
 * many standard DNGs on modern devices. Some vendor RAW formats (e.g. Xiaomi
 * UltraRAW) may fail here; callers should surface a clear error in that case.
 */
object RawDecoder {

    init {
        System.loadLibrary("rawdecoder")
    }

    /** Native develop pass: exposure gain + contrast, returns a new Bitmap. */
    private external fun nativeDevelop(src: Bitmap, exposure: Float, contrast: Float): Bitmap

    /** Fast native exposure/contrast normalization (used as a pre-pass). */
    fun develop(src: Bitmap, exposure: Float, contrast: Float): Bitmap =
        nativeDevelop(src, exposure, contrast)

    data class DecodeResult(
        val bitmap: Bitmap?,
        val error: String?,
        val wasRaw: Boolean
    )

    fun decode(context: Context, uri: Uri): DecodeResult {
        val name = (uri.lastPathSegment ?: "").lowercase()
        val looksRaw = name.endsWith(".dng") || name.endsWith(".raw") ||
                name.endsWith(".arw") || name.endsWith(".cr2") ||
                name.endsWith(".nef") || name.endsWith(".orf")

        return try {
            val bmp = if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
                    BitmapFactory.decodeStream(input)
                }
            }

            if (bmp == null) {
                DecodeResult(
                    null,
                    if (looksRaw)
                        "无法解码该 RAW 文件（可能是私有 UltraRAW 格式）。请尝试标准 DNG。"
                    else
                        "无法解码该图片文件。",
                    looksRaw
                )
            } else {
                DecodeResult(bmp, null, looksRaw)
            }
        } catch (t: Throwable) {
            DecodeResult(
                null,
                "解码失败：${t.message ?: t.javaClass.simpleName}" +
                        if (looksRaw) "\n该 RAW 格式可能不受系统支持。" else "",
                looksRaw
            )
        }
    }
}
