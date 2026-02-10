package com.glyphmatrix.displaycontrol

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import com.nothing.ketchum.GlyphToy

/**
 * Glyph Toy service - appears in Glyph Toys manager when user adds it.
 * Displays the layered background/foreground, with animation support.
 */
class DisplayControlToyService : Service() {

    private var glyphMatrixManager: GlyphMatrixManager? = null
    private var isConnected = false

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private var animationRunnable: Runnable? = null

    private var bgFrameIndex = 0
    private var fgFrameIndex = 0

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

    private val toyHandler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
            msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)?.let { event ->
                when (event) {
                    GlyphToy.EVENT_CHANGE -> { }
                    GlyphToy.EVENT_ACTION_DOWN -> { }
                    GlyphToy.EVENT_ACTION_UP -> { }
                }
            }
        }
        true
    }

    private val messenger = Messenger(toyHandler)

    override fun onBind(intent: Intent?): IBinder {
        glyphMatrixManager = GlyphMatrixManager.getInstance(applicationContext)
        glyphMatrixManager?.init(callback)
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        animationRunnable?.let { handler.removeCallbacks(it) }
        animationRunnable = null
        glyphMatrixManager?.turnOff()
        glyphMatrixManager?.unInit()
        glyphMatrixManager = null
        return false
    }

    private fun scheduleNextFrame() {
        animationRunnable?.let { handler.removeCallbacks(it) }

        val bgJson = prefs.getString(PREF_BACKGROUND_JSON, null)
        val fgJson = prefs.getString(PREF_FOREGROUND_JSON, null)
        val brightness = prefs.getInt(PREF_BACKGROUND_BRIGHTNESS, 255)

        val bgFrames = bgJson?.let { GlyphMatrixJsonParser.parseAnimation(it) }
        val fgFrames = fgJson?.let { GlyphMatrixJsonParser.parseAnimation(it) }

        if (bgFrames == null && fgFrames == null) return

        val bgDuration = bgFrames?.getOrNull(bgFrameIndex)?.durationMs ?: 100
        val fgDuration = fgFrames?.getOrNull(fgFrameIndex)?.durationMs ?: 100
        val delayMs = minOf(bgDuration, fgDuration).coerceAtLeast(16).toLong()

        updateDisplay(bgFrames, fgFrames, brightness)

        animationRunnable = Runnable {
            bgFrames?.let { if (it.size > 1) bgFrameIndex = (bgFrameIndex + 1) % it.size }
            fgFrames?.let { if (it.size > 1) fgFrameIndex = (fgFrameIndex + 1) % it.size }
            scheduleNextFrame()
        }
        handler.postDelayed(animationRunnable!!, delayMs)
    }

    private fun updateDisplay(
        bgFrames: List<AnimationFrame>?,
        fgFrames: List<AnimationFrame>?,
        brightness: Int
    ) {
        val gm = glyphMatrixManager ?: return
        if (!isConnected) return

        val builder = GlyphMatrixFrame.Builder()

        bgFrames?.getOrNull(bgFrameIndex)?.pixels?.let { pixels ->
            val adjusted = GlyphMatrixJsonParser.applyBrightness(pixels, brightness)
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

        fgFrames?.getOrNull(fgFrameIndex)?.pixels?.let { pixels ->
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

        val frame = builder.build(this)
        gm.setMatrixFrame(frame.render())
    }

    companion object {
        const val PREFS_NAME = "glyph_display_prefs"
        const val PREF_BACKGROUND_JSON = "background_json"
        const val PREF_FOREGROUND_JSON = "foreground_json"
        const val PREF_BACKGROUND_BRIGHTNESS = "background_brightness"
    }
}
