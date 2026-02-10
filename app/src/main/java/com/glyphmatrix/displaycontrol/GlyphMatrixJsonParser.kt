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
 * Format 3 - Animation frames (GlyphMatrixEditor):
 * {
 *   "v": 1,
 *   "frames": [{"d": 100, "p": [0, 40, 80, ...]}, ...]
 * }
 * Values in "p" are 0-255 intensity (grayscale); "d" is duration in ms.
 *
 * Colors can be: integer (0xAARRGGBB), hex string "#RRGGBB" or "#AARRGGBB"
 */
data class AnimationFrame(val pixels: IntArray, val durationMs: Int)

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
     * For frames format, returns first frame only. Use parseAnimation for all frames.
     */
    fun parse(jsonString: String): IntArray? {
        val frames = parseAnimation(jsonString) ?: return null
        return frames.firstOrNull()?.pixels
    }

    /**
     * Parse JSON and return all animation frames with durations.
     * For non-animation formats (pixels/rows), returns single frame with 100ms duration.
     */
    fun parseAnimation(jsonString: String): List<AnimationFrame>? {
        return try {
            val json = JSONObject(jsonString)
            when {
                json.has("frames") -> parseAllFrames(json.getJSONArray("frames"))
                json.has("pixels") -> {
                    parsePixelsArray(json.getJSONArray("pixels"))?.let { pixels ->
                        listOf(AnimationFrame(pixels, 100))
                    }
                }
                json.has("rows") -> {
                    parseRowsArray(json.getJSONArray("rows"))?.let { pixels ->
                        listOf(AnimationFrame(pixels, 100))
                    }
                }
                else -> null
            }
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

    private fun parseAllFrames(framesArr: JSONArray): List<AnimationFrame>? {
        if (framesArr.length() == 0) return null
        val result = mutableListOf<AnimationFrame>()
        for (i in 0 until framesArr.length()) {
            val frameObj = framesArr.getJSONObject(i)
            val pixels = parseFramePixels(frameObj.optJSONArray("p")) ?: continue
            val durationMs = frameObj.optInt("d", 100).coerceIn(16, 10000) // 16ms-10s
            result.add(AnimationFrame(pixels, durationMs))
        }
        return if (result.isEmpty()) null else result
    }

    private fun parseFramePixels(pArr: JSONArray?): IntArray? {
        if (pArr == null || pArr.length() == 0) return null
        val inputLen = pArr.length()

        return when {
            inputLen == PIXEL_COUNT -> {
                IntArray(PIXEL_COUNT) { index ->
                    val v = parseFramesIntensity(pArr.get(index))
                    Color.argb(255, v, v, v)
                }
            }
            inputLen == SHAPE_PATTERN.sum() -> {
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
