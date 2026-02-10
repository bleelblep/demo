package com.glyphmatrix.displaycontrol

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.InputStream

class MainActivity : ComponentActivity() {

    private lateinit var displayManager: GlyphMatrixDisplayManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        displayManager = GlyphMatrixDisplayManager(applicationContext)
        displayManager.init()

        setContent {
            GlyphMatrixDisplayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    GlyphMatrixDisplayScreen(
                        displayManager = displayManager,
                        onBackgroundSelected = { uri -> loadJson(uri) { displayManager.setBackground(it) } },
                        onForegroundSelected = { uri -> loadJson(uri) { displayManager.setForeground(it) } }
                    )
                }
            }
        }
    }

    private fun loadJson(uri: Uri, onLoaded: (IntArray?) -> Unit) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val pixels = GlyphMatrixJsonParser.parse(stream)
                if (pixels != null) {
                    onLoaded(pixels)
                    runOnUiThread {
                        Toast.makeText(this, "JSON loaded successfully", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Invalid JSON format", Toast.LENGTH_LONG).show()
                    }
                }
            } ?: runOnUiThread {
                Toast.makeText(this, "Could not read file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::displayManager.isInitialized) {
            displayManager.unInit()
        }
    }
}

@Composable
fun GlyphMatrixDisplayScreen(
    displayManager: GlyphMatrixDisplayManager,
    onBackgroundSelected: (Uri) -> Unit,
    onForegroundSelected: (Uri) -> Unit
) {
    var backgroundBrightness by remember { mutableFloatStateOf(1f) }
    var backgroundFileName by remember { mutableStateOf<String?>(null) }
    var foregroundFileName by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            backgroundFileName = getFileName(context, it)
            onBackgroundSelected(it)
        }
    }

    val foregroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            foregroundFileName = getFileName(context, it)
            onForegroundSelected(it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Glyph Matrix Display Control",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF00FF00),
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Nothing Phone 3",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Background JSON Picker
        Text(
            text = "Background Layer",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Button(
            onClick = { backgroundPicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(backgroundFileName ?: "Select Background JSON")
        }

        // Background Brightness Slider
        Text(
            text = "Background Brightness: ${(backgroundBrightness * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        Slider(
            value = backgroundBrightness,
            onValueChange = {
                backgroundBrightness = it
                displayManager.setBackgroundBrightness((it * 255).toInt())
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00FF00),
                activeTrackColor = Color(0xFF00FF00),
                inactiveTrackColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Foreground JSON Picker
        Text(
            text = "Foreground Layer",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Button(
            onClick = { foregroundPicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(foregroundFileName ?: "Select Foreground JSON")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "JSON Format: 25×25 matrix (625 pixels). Use \"pixels\" array or \"rows\" array. Colors: hex (#RRGGBB) or integer (0xAARRGGBB).",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

private fun getFileName(context: android.content.Context, uri: Uri): String {
    var fileName = "Unknown"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                fileName = cursor.getString(nameIndex) ?: "Unknown"
            }
        }
    }
    return fileName
}

@Composable
fun GlyphMatrixDisplayTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF00FF00),
            onPrimary = Color.Black,
            surface = Color.Black,
            onSurface = Color.White
        ),
        content = content
    )
}
