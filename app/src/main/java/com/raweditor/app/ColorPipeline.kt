package com.raweditor.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RenderScript
import kotlin.math.max
import kotlin.math.min

/**
 * CPU colour pipeline. Applies a [ColorPreset] to a Bitmap in a fixed order:
 *
 *   exposure -> contrast -> highlights/shadows -> temperature/tint
 *            -> vibrance/saturation -> clarity/sharpness
 *
 * `strength` (0..1) blends the preset's effect against the original image so the
 * user has one simple slider.
 */
object ColorPipeline {

    fun apply(source: Bitmap, preset: ColorPreset, strength: Float): Bitmap {
        val s = strength.coerceIn(0f, 1f)
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        if (s <= 0.001f) return out

        val p = blend(source, preset, s)

        // 1) exposure / contrast / white balance / saturation via ColorMatrix
        val matrix = buildColorMatrix(p)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        val canvas = Canvas(out)
        canvas.drawBitmap(source, 0f, 0f, paint)

        // 2) highlights / shadows / clarity as a per-pixel tone pass
        if (p.highlights != 0f || p.shadows != 0f || p.clarity != 0f) {
            tonePass(out, p)
        }

        // 3) optional sharpening
        if (p.sharpness > 0.01f) {
            sharpen(out, p.sharpness)
        }
        return out
    }

    /** Linearly interpolate preset parameters toward neutral by `s`. */
    private fun blend(src: Bitmap, p: ColorPreset, s: Float): ColorPreset {
        fun lerp(a: Float, neutral: Float) = neutral + (a - neutral) * s
        return p.copy(
            exposure = lerp(p.exposure, 1f),
            contrast = lerp(p.contrast, 1f),
            highlights = lerp(p.highlights, 0f),
            shadows = lerp(p.shadows, 0f),
            temperature = lerp(p.temperature, 0f),
            tint = lerp(p.tint, 0f),
            saturation = lerp(p.saturation, 1f),
            vibrance = lerp(p.vibrance, 0f),
            clarity = lerp(p.clarity, 0f),
            sharpness = lerp(p.sharpness, 0f)
        )
    }

    private fun buildColorMatrix(p: ColorPreset): ColorMatrix {
        val cm = ColorMatrix()

        // Contrast around mid-grey
        val c = p.contrast
        val t = (1f - c) * 127.5f
        cm.postConcat(ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f
        )))

        // White balance: temperature (R up / B down) + tint (G down / R+B up)
        val rGain = 1f + p.temperature * 0.15f + p.tint * 0.04f
        val gGain = 1f - p.tint * 0.10f
        val bGain = 1f - p.temperature * 0.15f + p.tint * 0.04f
        cm.postConcat(ColorMatrix(floatArrayOf(
            rGain, 0f, 0f, 0f, 0f,
            0f, gGain, 0f, 0f, 0f,
            0f, 0f, bGain, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))

        // Saturation
        val sat = p.saturation
        val invSat = 1f - sat
        val lr = 0.213f * invSat
        val lg = 0.715f * invSat
        val lb = 0.072f * invSat
        cm.postConcat(ColorMatrix(floatArrayOf(
            lr + sat, lg, lb, 0f, 0f,
            lr, lg + sat, lb, 0f, 0f,
            lr, lg, lb + sat, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))

        // Global exposure (slight, bounded) folded into the matrix
        val e = p.exposure
        if (e != 1f) {
            cm.postConcat(ColorMatrix(floatArrayOf(
                e, 0f, 0f, 0f, 0f,
                0f, e, 0f, 0f, 0f,
                0f, 0f, e, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        return cm
    }

    /** Per-pixel highlight recovery, shadow lift and local-contrast (clarity). */
    private fun tonePass(bitmap: Bitmap, p: ColorPreset) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val hi = p.highlights      // -1..1
        val sh = p.shadows         // -1..1
        val cl = p.clarity         // -1..1

        for (i in pixels.indices) {
            var px = pixels[i]
            var r = (px shr 16) and 0xFF
            var g = (px shr 8) and 0xFF
            var b = px and 0xFF

            var lr = r / 255f
            var lg = g / 255f
            var lb = b / 255f

            val luma = 0.213f * lr + 0.715f * lg + 0.072f * lb

            // Highlight recovery: pull down the top end only
            if (hi < 0f) {
                val mask = smoothstep(0.6f, 1.0f, luma)
                val k = 1f + hi * mask * 0.6f
                lr *= k; lg *= k; lb *= k
            }
            // Shadow lift: raise the bottom end only
            if (sh > 0f) {
                val mask = 1f - smoothstep(0.0f, 0.45f, luma)
                val add = sh * mask * 0.25f
                lr += add; lg += add; lb += add
            }
            // Clarity: push mid-tones away from 0.5 for local snap
            if (cl != 0f) {
                val mask = 1f - kotlin.math.abs(luma - 0.5f) * 2f
                val factor = 1f + cl * mask * 0.5f
                lr = (lr - 0.5f) * factor + 0.5f
                lg = (lg - 0.5f) * factor + 0.5f
                lb = (lb - 0.5f) * factor + 0.5f
            }

            r = (lr * 255f).toInt().coerceIn(0, 255)
            g = (lg * 255f).toInt().coerceIn(0, 255)
            b = (lb * 255f).toInt().coerceIn(0, 255)

            pixels[i] = (px and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** 3x3 unsharp mask, strength 0..1. */
    private fun sharpen(bitmap: Bitmap, strength: Float) {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 3 || h < 3) return
        val src = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        val dst = src.copyOf()
        val amt = strength.coerceIn(0f, 1f) * 0.8f

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                for (c in 0..2) {
                    val shift = when (c) { 0 -> 16; 1 -> 8; else -> 0 }
                    fun ch(px: Int) = (px shr shift) and 0xFF
                    val center = ch(src[idx])
                    val sum =
                        ch(src[idx - 1]) + ch(src[idx + 1]) +
                        ch(src[idx - w]) + ch(src[idx + w])
                    val blurred = sum / 4f
                    val v = (center + (center - blurred) * amt)
                        .toInt().coerceIn(0, 255)
                    dst[idx] = (dst[idx] and (0xFF shl shift).inv()) or (v shl shift)
                }
            }
        }
        bitmap.setPixels(dst, 0, w, 0, 0, w, h)
    }
}
