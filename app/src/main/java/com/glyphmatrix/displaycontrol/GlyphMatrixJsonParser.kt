package com.glyphmatrix.displaycontrol

import android.graphics.Color
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/**
 * Parses JSON files representing a 25x25 Glyph Matrix display.
 *
 * Supported JSON formats:
 *
 * Format 1 - Flat pixels array (625 integers or hex strings):
 * {
 *   "width": 25,
 *   "height": 25,
 *   "pixels": [0xFF000000, "#FFFFFF", 255, ...]
 * }
 *
 * Format 2 - Row-based:
 * {
 *   "rows": [
 *     [0xFF000000, 0xFF00FF00, ...],
 *     ...
 *   ]
 * }
 *
 * Colors can be: integer (0xAARRGGBB), hex string "#RRGGBB" or "#AARRGGBB"
 */
object GlyphMatrixJsonParser {

    private const val MATRIX_SIZE = 25
    private const val PIXEL_COUNT = MATRIX_SIZE * MATRIX_SIZE

    /**
     * Parse JSON from input stream and return 625 color integers (25x25 matrix).
     * Returns null if parsing fails.
     */
    fun parse(inputStream: InputStream): IntArray? {
        return try {
            val json = inputStream.bufferedReader().use { it.readText() }
            parse(json)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse JSON string and return 625 color integers (25x25 matrix).
     * Returns null if parsing fails.
     */
    fun parse(jsonString: String): IntArray? {
        return try {
            val json = JSONObject(jsonString)
            val pixels = when {
                json.has("pixels") -> parsePixelsArray(json.getJSONArray("pixels"))
                json.has("rows") -> parseRowsArray(json.getJSONArray("rows"))
                else -> null
            }
            pixels
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePixelsArray(arr: JSONArray): IntArray? {
        if (arr.length() != PIXEL_COUNT) return null
        return IntArray(PIXEL_COUNT) { index ->
            parseColor(arr.get(index))
        }
    }

    private fun parseRowsArray(arr: JSONArray): IntArray? {
        if (arr.length() != MATRIX_SIZE) return null
        val result = IntArray(PIXEL_COUNT)
        for (row in 0 until MATRIX_SIZE) {
            val rowArr = arr.getJSONArray(row)
            if (rowArr.length() != MATRIX_SIZE) return null
            for (col in 0 until MATRIX_SIZE) {
                result[row * MATRIX_SIZE + col] = parseColor(rowArr.get(col))
            }
        }
        return result
    }

    private fun parseColor(value: Any): Int {
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Double -> value.toInt()
            is String -> {
                val s = value.trim()
                when {
                    s.startsWith("#") -> try {
                        Color.parseColor(s)
                    } catch (_: Exception) {
                        Color.BLACK
                    }
                    s.startsWith("0x", ignoreCase = true) ->
                        s.substring(2).toLongOrNull(16)?.toInt() ?: Color.BLACK
                    else -> s.toIntOrNull() ?: Color.BLACK
                }
            }
            else -> Color.BLACK
        }
    }

    /**
     * Apply brightness multiplier to pixel array (0-255).
     * 255 = full brightness, 0 = black
     */
    fun applyBrightness(pixels: IntArray, brightness: Int): IntArray {
        if (brightness >= 255) return pixels
        val factor = brightness / 255f
        return IntArray(pixels.size) { index ->
            val color = pixels[index]
            val a = Color.alpha(color)
            val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
            val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
            val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
            Color.argb(a, r, g, b)
        }
    }

    /**
     * Convert pixel array to Bitmap for GlyphMatrixObject
     */
    fun pixelsToBitmap(pixels: IntArray): Bitmap {
        val bitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, MATRIX_SIZE, 0, 0, MATRIX_SIZE, MATRIX_SIZE)
        return bitmap
    }
}
