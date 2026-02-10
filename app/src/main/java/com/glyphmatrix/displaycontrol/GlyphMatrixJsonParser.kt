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

    // GlyphMatrixEditor shape: pixels per row (diamond shape, 489 total)
    // https://github.com/pauwma/GlyphMatrixEditor
    private val SHAPE_PATTERN = intArrayOf(
        7, 11, 15, 17, 19, 21, 21, 23, 23,
        25, 25, 25, 25, 25, 25, 25,
        23, 23, 21, 21, 19, 17, 15, 11, 7
    )

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
     * Parse frames format from GlyphMatrixEditor: {"v":1, "frames":[{"d":100, "p":[...]}]}
     * Values in p are 0-255 intensity (grayscale).
     * 625 = 25x25 row-major. 489 = diamond shape per GlyphMatrixEditor (shapePattern).
     * @see <a href="https://github.com/pauwma/GlyphMatrixEditor">GlyphMatrixEditor</a>
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
            inputLen == SHAPE_PATTERN.sum() -> {
                // GlyphMatrixEditor format: 489 pixels in diamond shape
                // Each row has shapePattern[row] pixels, centered in 25 cols
                val output = IntArray(PIXEL_COUNT) { Color.argb(255, 0, 0, 0) }
                var idx = 0
                for (row in 0 until MATRIX_SIZE) {
                    val rowWidth = SHAPE_PATTERN[row]
                    val startCol = (MATRIX_SIZE - rowWidth) / 2
                    for (c in 0 until rowWidth) {
                        if (idx >= inputLen) break
                        val col = startCol + c
                        val v = parseFramesIntensity(pArr.get(idx++))
                        output[row * MATRIX_SIZE + col] = Color.argb(255, v, v, v)
                    }
                }
                output
            }
            else -> {
                // Fallback: linear pad (for other sizes)
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
