package com.raweditor.app

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var progress: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvPresetDesc: TextView
    private lateinit var tvStrength: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var sliderStrength: Slider
    private lateinit var presetScroll: View
    private lateinit var btnPick: MaterialButton
    private lateinit var btnSave: MaterialButton

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
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        progress = findViewById(R.id.progress)
        tvStatus = findViewById(R.id.tvStatus)
        tvPresetDesc = findViewById(R.id.tvPresetDesc)
        tvStrength = findViewById(R.id.tvStrength)
        chipGroup = findViewById(R.id.chipGroup)
        sliderStrength = findViewById(R.id.sliderStrength)
        presetScroll = findViewById(R.id.presetScroll)
        btnPick = findViewById(R.id.btnPick)
        btnSave = findViewById(R.id.btnSave)

        btnPick.setOnClickListener { pickImage.launch(arrayOf("image/*", "*/*")) }
        btnSave.setOnClickListener {
            if (preview == null) {
                toast("请先选择一张图片")
            } else {
                exportImage.launch("edited_" + System.currentTimeMillis() + ".jpg")
            }
        }

        chipGroup.removeAllViews()
        for (p in Presets.ALL) {
            val chip = Chip(this).apply {
                text = p.displayName
                isCheckable = true
                isChecked = p.id == selectedPreset.id
                setOnClickListener {
                    selectedPreset = p
                    tvPresetDesc.text = p.description
                    render()
                }
            }
            chipGroup.addView(chip)
        }
        tvPresetDesc.text = selectedPreset.description

        sliderStrength.value = strength * 100f
        sliderStrength.addOnChangeListener { _, value, _ ->
            strength = value / 100f
            tvStrength.text = "强度 ${value.toInt()}%"
            render()
        }
        tvStrength.text = "强度 ${(strength * 100).toInt()}%"

        presetScroll.visibility = View.GONE
    }

    private fun loadImage(uri: Uri) {
        if (busy) return
        busy = true
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { RawDecoder.decode(this@MainActivity, uri) }
            progress.visibility = View.GONE
            busy = false
            if (result.bitmap == null) {
                toast(result.error ?: "解码失败")
                presetScroll.visibility = View.GONE
                return@launch
            }
            original = result.bitmap
            presetScroll.visibility = View.VISIBLE
            tvStatus.text =
                (if (result.wasRaw) "RAW 文件已载入 · " else "图片已载入 · ") +
                        "${result.bitmap.width}×${result.bitmap.height}"
            render()
        }
    }

    private fun render() {
        val src = original ?: return
        if (busy) return
        busy = true
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val out = withContext(Dispatchers.Default) {
                val scaled = downscaleForPreview(src)
                ColorPipeline.apply(scaled, selectedPreset, strength)
            }
            preview = out
            imageView.setImageBitmap(out)
            progress.visibility = View.GONE
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
        val src = original ?: return
        lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            val ok = withContext(Dispatchers.IO) {
                try {
                    // Re-render at full resolution for export.
                    val full = ColorPipeline.apply(src, selectedPreset, strength)
                    contentResolver.openOutputStream(uri)?.use { os ->
                        full.compress(Bitmap.CompressFormat.JPEG, 95, os)
                    }
                    true
                } catch (t: Throwable) {
                    false
                }
            }
            progress.visibility = View.GONE
            toast(if (ok) "已保存" else "保存失败")
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
