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
 * Displays the layered background/foreground configured in the app.
 */
class DisplayControlToyService : Service() {

    private var glyphMatrixManager: GlyphMatrixManager? = null
    private var isConnected = false

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

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

    private val handler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
            msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)?.let { event ->
                when (event) {
                    GlyphToy.EVENT_CHANGE -> { /* long press - could toggle animation etc */ }
                    GlyphToy.EVENT_ACTION_DOWN -> { }
                    GlyphToy.EVENT_ACTION_UP -> { }
                }
            }
        }
        true
    }

    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder {
        glyphMatrixManager = GlyphMatrixManager.getInstance(applicationContext)
        glyphMatrixManager?.init(callback)
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        glyphMatrixManager?.turnOff()
        glyphMatrixManager?.unInit()
        glyphMatrixManager = null
        return false
    }

    private fun updateDisplay() {
        val gm = glyphMatrixManager ?: return
        if (!isConnected) return

        val bgJson = prefs.getString(PREF_BACKGROUND_JSON, null)
        val fgJson = prefs.getString(PREF_FOREGROUND_JSON, null)
        val brightness = prefs.getInt(PREF_BACKGROUND_BRIGHTNESS, 255)

        if (bgJson == null && fgJson == null) return

        val builder = GlyphMatrixFrame.Builder()

        bgJson?.let { json ->
            GlyphMatrixJsonParser.parse(json)?.let { pixels ->
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
        }

        fgJson?.let { json ->
            GlyphMatrixJsonParser.parse(json)?.let { pixels ->
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
