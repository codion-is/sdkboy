# GraalVM Native Image Configuration for SDKBOY

This document describes the GraalVM native image configuration for SDKBOY.

## Prerequisites

1. GraalVM 21 or later with Native Image component
2. SDKMAN installed on the system
3. Required native libraries and headers (for Linux):
   - libfreetype6-dev (TrueType fonts)
   - libgl1-mesa-dev (OpenGL)
   - libx11-dev, libxext-dev, libxrender-dev, libxtst-dev (X11)
   
   Install with: `sudo apt install libfreetype6-dev libgl1-mesa-dev libx11-dev libxext-dev libxrender-dev libxtst-dev`

## Installing GraalVM

Using SDKMAN:
```bash
# For older GraalVM versions (with gu tool):
sdk install java 21.0.2-graal
sdk use java 21.0.2-graal
gu install native-image

# For newer GraalVM versions (21.0.7+):
sdk install java 21.0.7-graal
sdk use java 21.0.7-graal
# native-image is already included
```

## Building Native Image

### Step 1: Generate/Update Configuration with Tracing Agent
```bash
./gradlew runWithAgent
```
**IMPORTANT**: When the app launches, interact with EVERY UI component:
- Click all buttons and menu items
- Open all dialogs and panels
- Test all features to trigger JNI/Reflection calls
- Close the app normally when done

### Step 2: Configure Gradle to use GraalVM
Create a `gradle.properties` file in the project root:
```properties
org.gradle.java.home=/home/YOUR_USER/.sdkman/candidates/java/21.0.7-graal
```

### Step 3: Build the native executable:
```bash
./gradlew clean build nativeCompile
```

### Step 4: Run the native executable:
```bash
./build/native/nativeCompile/sdkboy
```

## Configuration Files

The native image configuration files are located in:
`src/main/resources/META-INF/native-image/is.codion.sdkboy/`

- `reflect-config.json` - Reflection configuration for Swing and Codion components
- `jni-config.json` - JNI configuration for native libraries
- `resource-config.json` - Resource inclusion patterns
- `native-image.properties` - Native image build arguments

## Known Issues

1. **Swing Support**: Swing support in GraalVM native image is experimental but functional
   - Works on Linux (as of GraalVM 21.0+)
   - Windows and macOS support is still in development
   - Requires proper native library dependencies
2. **JNA Libraries**: The sdkman-api uses JNA which requires special handling
3. **Dynamic Class Loading**: Some FlatLaf themes may not work without proper configuration
4. **Tracing Agent**: Must interact with ALL UI components during agent run

## Alternative Approach

Due to current GraalVM limitations with Swing applications, consider using:
1. The existing jlink image approach (smaller than full JDK, faster than native)
2. Project Leyden (future JDK feature for fast startup)
3. AppCDS (Application Class Data Sharing) for improved startup

## Important Implementation Details

### Key Build Arguments
- `--initialize-at-run-time` - Critical for AWT/Swing classes
- `-H:+JNI` - Enables JNI support for native libraries
- `-H:+AddAllCharsets` - Includes all character sets
- `-H:+IncludeAllLocales` - Includes all locales

## Troubleshooting

### Common Issues and Solutions

1. **"java.home property not set" error**
   - Solution: Use the NativeMain wrapper class
   - Ensure gradle.properties points to GraalVM

2. **"static createUI() method not found" errors**
   - Solution: Add missing UI classes to reflect-config.json
   - Run with tracing agent to capture all UI interactions

3. **"Could not allocate library name" error**
   - Solution: Install required native library headers
   - Check library paths in native-image configuration

4. **Gradle can't find native-image**
   - Solution: Set org.gradle.java.home in gradle.properties
   - Or use: `JAVA_HOME=/path/to/graalvm ./gradlew nativeCompile`

### Debug Tips
- Use `--verbose` flag for detailed build output
- Check generated configurations in `build/native/agent-output`
- Run with `--report-unsupported-elements-at-runtime` for debugging

## Expected Results

Based on similar Swing apps (FlatLaf demo):
- Binary size: ~50-60 MB (can be compressed to ~20 MB with UPX)
- Startup time: Near instant ("blink of an eye")
- Memory usage: Significantly lower than JVM
- Distribution: Single standalone executable

## Performance

Native image provides:
- Faster startup time (milliseconds vs seconds)
- Lower memory footprint (~30-50MB vs ~200MB+ with JVM)
- No JVM warmup required
- Predictable performance from start

Trade-offs:
- Larger binary size (~50-60MB)
- Longer build times (2-5 minutes)
- Some dynamic features may not work
- Platform-specific binaries needed

## Distribution Options

1. **Standalone Binary**
   ```bash
   cp build/native/nativeCompile/sdkboy ~/bin/
   ```

2. **Compressed with UPX**
   ```bash
   upx -9 build/native/nativeCompile/sdkboy
   # Reduces size to ~20MB
   ```

3. **AppImage (Linux)**
   - Package with desktop integration
   - Single portable file

4. **Include in existing packages**
   - Update jpackage configuration to use native binary