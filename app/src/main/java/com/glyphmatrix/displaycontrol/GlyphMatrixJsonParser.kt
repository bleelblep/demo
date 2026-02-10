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
 * Format 3 - Animation frames (uses first frame):
 * {
 *   "v": 1,
 *   "frames": [{"d": 100, "p": [0, 40, 80, ...]}, ...]
 * }
 * Values in "p" are 0-255 intensity (grayscale); converted to white for display.
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
                json.has("frames") -> parseFramesFormat(json.getJSONArray("frames"))
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

    /**
     * Parse frames format: {"v":1, "frames":[{"d":100, "p":[...]}]}
     * Uses first frame. Values in p are 0-255 intensity (grayscale).
     * 625 = 25x25 row-major. 489 = 21x23 centered in 25x25 (weather/glyph format).
     */
    private fun parseFramesFormat(framesArr: JSONArray): IntArray? {
        if (framesArr.length() == 0) return null
        val firstFrame = framesArr.getJSONObject(0)
        if (!firstFrame.has("p")) return null
        val pArr = firstFrame.getJSONArray("p")
        val inputLen = pArr.length()
        if (inputLen == 0) return null

        return when {
            inputLen == PIXEL_COUNT -> {
                // Standard 25x25 row-major
                IntArray(PIXEL_COUNT) { index ->
                    val v = parseFramesIntensity(pArr.get(index))
                    Color.argb(255, v, v, v)
                }
            }
            inputLen >= 483 -> {
                // 489-style: 21 rows x 23 cols (483 used). Center in 25x25.
                val srcW = 23
                val srcH = 21
                val offsetX = (MATRIX_SIZE - srcW) / 2
                val offsetY = (MATRIX_SIZE - srcH) / 2
                IntArray(PIXEL_COUNT) { index ->
                    val outRow = index / MATRIX_SIZE
                    val outCol = index % MATRIX_SIZE
                    val srcRow = outRow - offsetY
                    val srcCol = outCol - offsetX
                    val v = if (srcRow in 0 until srcH && srcCol in 0 until srcW) {
                        val srcIndex = srcRow * srcW + srcCol
                        if (srcIndex < inputLen) parseFramesIntensity(pArr.get(srcIndex)) else 0
                    } else 0
                    Color.argb(255, v, v, v)
                }
            }
            else -> {
                // Fallback: linear pad
                IntArray(PIXEL_COUNT) { index ->
                    val v = if (index < inputLen) parseFramesIntensity(pArr.get(index)) else 0
                    Color.argb(255, v, v, v)
                }
            }
        }
    }

    private fun parseFramesIntensity(value: Any): Int = when (value) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> value.toInt()
        else -> value.toString().toIntOrNull() ?: 0
    }.coerceIn(0, 255)

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
