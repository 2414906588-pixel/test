package com.raweditor.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var original: Bitmap? = null
    private var preview: Bitmap? = null
    private var selectedPreset: ColorPreset = Presets.ALL.first()
    private var strength: Float = 0.8f
    private var busy = false

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) loadImage(uri)
    }

    private val exportImage = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri: Uri? ->
        if (uri != null) saveTo(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPick.setOnClickListener { pickImage.launch(arrayOf("image/*", "*/*")) }
        binding.btnSave.setOnClickListener {
            if (preview == null) {
                toast("请先选择一张图片")
            } else {
                exportImage.launch("edited_" + System.currentTimeMillis() + ".jpg")
            }
        }

        binding.chipGroup.removeAllViews()
        for (p in Presets.ALL) {
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = p.displayName
                isCheckable = true
                isChecked = p.id == selectedPreset.id
                setOnClickListener {
                    selectedPreset = p
                    binding.tvPresetDesc.text = p.description
                    render()
                }
            }
            binding.chipGroup.addView(chip)
        }
        binding.tvPresetDesc.text = selectedPreset.description

        binding.sliderStrength.value = strength * 100f
        binding.sliderStrength.addOnChangeListener { _, value, _ ->
            strength = value / 100f
            binding.tvStrength.text = "强度 ${value.toInt()}%"
            render()
        }
        binding.tvStrength.text = "强度 ${(strength * 100).toInt()}%"

        binding.presetScroll.visibility = View.GONE
    }

    private fun loadImage(uri: Uri) {
        if (busy) return
        busy = true
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { RawDecoder.decode(this@MainActivity, uri) }
            binding.progress.visibility = View.GONE
            busy = false
            if (result.bitmap == null) {
                toast(result.error ?: "解码失败")
                binding.presetScroll.visibility = View.GONE
                return@launch
            }
            original = result.bitmap
            binding.presetScroll.visibility = View.VISIBLE
            binding.tvStatus.text =
                (if (result.wasRaw) "RAW 文件已载入 · " else "图片已载入 · ") +
                        "${result.bitmap.width}×${result.bitmap.height}"
            render()
        }
    }

    private fun render() {
        val src = original ?: return
        if (busy) return
        busy = true
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val out = withContext(Dispatchers.Default) {
                val scaled = downscaleForPreview(src)
                ColorPipeline.apply(scaled, selectedPreset, strength)
            }
            preview = out
            binding.imageView.setImageBitmap(out)
            binding.progress.visibility = View.GONE
            busy = false
        }
    }

    /** Keep the preview responsive by working on a bounded-size bitmap. */
    private fun downscaleForPreview(src: Bitmap): Bitmap {
        val maxDim = 1600
        val w = src.width
        val h = src.height
        if (w <= maxDim && h <= maxDim) return src
        val scale = maxDim.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(
            src, (w * scale).toInt().coerceAtLeast(1),
            (h * scale).toInt().coerceAtLeast(1), true
        )
    }

    private fun saveTo(uri: Uri) {
        val bmp = preview ?: return
        lifecycleScope.launch {
            binding.progress.visibility = View.VISIBLE
            val ok = withContext(Dispatchers.IO) {
                try {
                    // Re-render at full resolution for export.
                    val src = original ?: return@withContext false
                    val full = ColorPipeline.apply(src, selectedPreset, strength)
                    contentResolver.openOutputStream(uri)?.use { os ->
                        full.compress(Bitmap.CompressFormat.JPEG, 95, os)
                    }
                    true
                } catch (t: Throwable) {
                    false
                }
            }
            binding.progress.visibility = View.GONE
            toast(if (ok) "已保存" else "保存失败")
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
