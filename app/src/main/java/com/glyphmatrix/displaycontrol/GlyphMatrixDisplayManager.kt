package com.glyphmatrix.displaycontrol

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject

/**
 * Manages the Glyph Matrix display with layered background and foreground.
 */
class GlyphMatrixDisplayManager(private val context: Context) {

    private var glyphMatrixManager: GlyphMatrixManager? = null
    private var isConnected = false

    private var backgroundPixels: IntArray? = null
    private var foregroundPixels: IntArray? = null
    private var backgroundBrightness = 255

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            isConnected = true
            glyphMatrixManager?.register(Glyph.DEVICE_23112)
            updateDisplay()
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            isConnected = false
        }
    }

    fun init() {
        glyphMatrixManager = GlyphMatrixManager.getInstance(context)
        glyphMatrixManager?.init(callback)
    }

    fun unInit() {
        glyphMatrixManager?.closeAppMatrix()
        glyphMatrixManager?.unInit()
        glyphMatrixManager = null
    }

    fun setBackground(pixels: IntArray?) {
        backgroundPixels = pixels
        updateDisplay()
    }

    fun setForeground(pixels: IntArray?) {
        foregroundPixels = pixels
        updateDisplay()
    }

    fun setBackgroundBrightness(brightness: Int) {
        backgroundBrightness = brightness.coerceIn(0, 255)
        updateDisplay()
    }

    private fun updateDisplay() {
        val gm = glyphMatrixManager ?: return
        if (!isConnected) return
        if (backgroundPixels == null && foregroundPixels == null) return

        val builder = GlyphMatrixFrame.Builder()

        // Add background as low layer (bottom)
        backgroundPixels?.let { pixels ->
            val adjustedPixels = GlyphMatrixJsonParser.applyBrightness(pixels, backgroundBrightness)
            val bitmap = GlyphMatrixJsonParser.pixelsToBitmap(adjustedPixels)
            val bgObject = GlyphMatrixObject.Builder()
                .setImageSource(bitmap)
                .setPosition(0, 0)
                .setScale(100)
                .setBrightness(255)
                .build()
            builder.addLow(bgObject)
        }

        // Add foreground as top layer
        foregroundPixels?.let { pixels ->
            val bitmap = GlyphMatrixJsonParser.pixelsToBitmap(pixels)
            val fgObject = GlyphMatrixObject.Builder()
                .setImageSource(bitmap)
                .setPosition(0, 0)
                .setScale(100)
                .setBrightness(255)
                .build()
            builder.addTop(fgObject)
        }

        val frame = builder.build(context)
        gm.setAppMatrixFrame(frame.render())
    }

    fun hasContent(): Boolean = backgroundPixels != null || foregroundPixels != null
}
