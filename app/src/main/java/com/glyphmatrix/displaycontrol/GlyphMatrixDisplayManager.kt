package com.glyphmatrix.displaycontrol

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject

/**
 * Manages the Glyph Matrix display with layered background and foreground.
 * Supports animation: multiple frames cycle with their specified durations.
 */
class GlyphMatrixDisplayManager(private val context: Context) {

    private var glyphMatrixManager: GlyphMatrixManager? = null
    private var isConnected = false

    private var backgroundFrames: List<AnimationFrame>? = null
    private var foregroundFrames: List<AnimationFrame>? = null
    private var backgroundBrightness = 255

    private val handler = Handler(Looper.getMainLooper())
    private var animationRunnable: Runnable? = null

    private var bgFrameIndex = 0
    private var fgFrameIndex = 0
    private var isPaused = false

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(componentName: ComponentName?) {
            isConnected = true
            glyphMatrixManager?.register(Glyph.DEVICE_23112)
            scheduleNextFrame()
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
        animationRunnable?.let { handler.removeCallbacks(it) }
        animationRunnable = null
        glyphMatrixManager?.closeAppMatrix()
        glyphMatrixManager?.unInit()
        glyphMatrixManager = null
    }

    fun setBackground(pixels: IntArray?) {
        backgroundFrames = pixels?.let { listOf(AnimationFrame(it, 100)) }
        bgFrameIndex = 0
        scheduleNextFrame()
    }

    fun setBackgroundAnimation(frames: List<AnimationFrame>?) {
        backgroundFrames = frames
        bgFrameIndex = 0
        scheduleNextFrame()
    }

    fun setForeground(pixels: IntArray?) {
        foregroundFrames = pixels?.let { listOf(AnimationFrame(it, 100)) }
        fgFrameIndex = 0
        scheduleNextFrame()
    }

    fun setForegroundAnimation(frames: List<AnimationFrame>?) {
        foregroundFrames = frames
        fgFrameIndex = 0
        scheduleNextFrame()
    }

    fun setBackgroundBrightness(brightness: Int) {
        backgroundBrightness = brightness.coerceIn(0, 255)
        scheduleNextFrame()
    }

    /** Reset both layers to frame 0 so they start in sync */
    fun sync() {
        bgFrameIndex = 0
        fgFrameIndex = 0
        updateDisplay()
        if (!isPaused) scheduleNextFrame()
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
        if (paused) {
            animationRunnable?.let { handler.removeCallbacks(it) }
            animationRunnable = null
        } else {
            scheduleNextFrame()
        }
    }

    private fun scheduleNextFrame() {
        if (isPaused) return
        animationRunnable?.let { handler.removeCallbacks(it) }

        animationRunnable?.let { handler.removeCallbacks(it) }

        val bgFrames = backgroundFrames
        val fgFrames = foregroundFrames
        if (bgFrames == null && fgFrames == null) return

        val bgDuration = bgFrames?.getOrNull(bgFrameIndex)?.durationMs ?: 100
        val fgDuration = fgFrames?.getOrNull(fgFrameIndex)?.durationMs ?: 100
        val delayMs = minOf(bgDuration, fgDuration).coerceAtLeast(16)

        updateDisplay()

        animationRunnable = Runnable {
            if (isPaused) return@Runnable
            bgFrames?.let { if (it.size > 1) bgFrameIndex = (bgFrameIndex + 1) % it.size }
            fgFrames?.let { if (it.size > 1) fgFrameIndex = (fgFrameIndex + 1) % it.size }
            scheduleNextFrame()
        }
        handler.postDelayed(animationRunnable!!, delayMs.toLong())
    }

    private fun updateDisplay() {
        val gm = glyphMatrixManager ?: return
        if (!isConnected) return
        if (backgroundFrames == null && foregroundFrames == null) return

        val builder = GlyphMatrixFrame.Builder()

        backgroundFrames?.getOrNull(bgFrameIndex)?.pixels?.let { pixels ->
            val adjusted = GlyphMatrixJsonParser.applyBrightness(pixels, backgroundBrightness)
            val bitmap = GlyphMatrixJsonParser.pixelsToBitmap(adjusted)
            builder.addLow(
                GlyphMatrixObject.Builder()
                    .setImageSource(bitmap)
                    .setPosition(0, 0)
                    .setScale(100)
                    .setBrightness(255)
                    .build()
            )
        }

        foregroundFrames?.getOrNull(fgFrameIndex)?.pixels?.let { pixels ->
            val bitmap = GlyphMatrixJsonParser.pixelsToBitmap(pixels)
            builder.addTop(
                GlyphMatrixObject.Builder()
                    .setImageSource(bitmap)
                    .setPosition(0, 0)
                    .setScale(100)
                    .setBrightness(255)
                    .build()
            )
        }

        val frame = builder.build(context)
        gm.setAppMatrixFrame(frame.render())
    }

    fun hasContent(): Boolean = backgroundFrames != null || foregroundFrames != null
}
