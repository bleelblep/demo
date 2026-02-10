# Glyph Matrix Display Control

An Android app for the **Nothing Phone 3** that uses the [GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit) to display layered custom designs on the Glyph Matrix LED display.

## Features

- **Dual JSON file picker**: Choose separate JSON files for background and foreground layers
- **Layered display**: Background and foreground are composited (background on bottom, foreground on top)
- **Background brightness slider**: Adjust the dimness/brightness of the background layer only (0-100%)
- **Live preview**: Changes apply immediately to the Glyph Matrix

## Requirements

- Nothing Phone 3 (or compatible Glyph Matrix device)
- Android 14+ (minSdk 34)
- GlyphMatrix Developer Kit SDK (included via AAR)

## JSON Format

Each JSON file represents a 25×25 pixel matrix for the Glyph Matrix display. Supported formats:

### Format 1: Flat pixels array
```json
{
  "width": 25,
  "height": 25,
  "pixels": [0, 0, "#FF00FF00", 0, ...]
}
```
Exactly 625 values (25×25). Row-major order.

### Format 2: Row-based
```json
{
  "rows": [
    [0, 0, 0, ...],
    ["#FF00FF00", 0, 0, ...],
    ...
  ]
}
```
25 rows of 25 values each.

### Color formats
- **Hex string**: `"#RRGGBB"` or `"#AARRGGBB"` (e.g. `"#FF00FF00"` for green)
- **Integer**: Decimal ARGB value (e.g. `-16711936` for green)

## Sample Files

Sample JSON files are included in `app/src/main/assets/samples/`:
- `background_example.json` - Soft gradient background
- `foreground_example.json` - Simple foreground overlay

## Building

### Prerequisites
- Android Studio or command-line Android SDK
- JDK 17

### Local build
```bash
./gradlew assembleDebug
```
The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### CI Build (GitHub Actions)
The project includes a GitHub Actions workflow that builds on push/PR. If `app/libs/glyph-matrix-sdk-1.0.aar` is not present, it will be downloaded from the official GlyphMatrix Developer Kit repository.

## Usage

1. Install the app on your Nothing Phone 3
2. Grant the required Glyph Matrix permission when prompted
3. Tap **Select Background JSON** to choose a JSON file (or use a file manager)
4. Use the **Background Brightness** slider to dim/brighten the background
5. Tap **Select Foreground JSON** to choose an overlay
6. The composite image appears on the Glyph Matrix

## License

This project uses the GlyphMatrix Developer Kit from Nothing. See the [GlyphMatrix Developer Kit repository](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit) for SDK terms.
