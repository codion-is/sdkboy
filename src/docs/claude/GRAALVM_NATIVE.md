# GraalVM Native Image Support

This guide documents the complete process for building Codion applications as GraalVM native images, based on the successful SDKBOY implementation.

## Overview

Codion applications can be compiled to native executables using GraalVM, providing:
- **Instant startup times** (milliseconds vs seconds)
- **Smaller memory footprint** 
- **No JVM dependency** for deployment
- **Single binary distribution**

## Prerequisites

- GraalVM 24+ (recommended for Foreign API support)
- Gradle 8.14+
- Linux/macOS/Windows (tested on Linux)

## Setup Process

### 1. Install GraalVM 24

```bash
# Using SDKMAN
sdk install java 24.0.1-graal
sdk use java 24.0.1-graal
```

### 2. Configure Gradle

Add to `build.gradle.kts`:
```kotlin
plugins {
    id("org.graalvm.buildtools.native") version "0.10.4"
}

graalvmNative {
    binaries {
        named("main") {
            imageName = project.name
            mainClass = "your.main.Class"
            buildArgs.add("-H:+ForeignAPISupport")
        }
    }
}
```

Create `gradle.properties`:
```properties
org.gradle.java.home=/path/to/graalvm-24
```

### 3. Native Image Configuration

Create `src/main/resources/META-INF/native-image/your.package/native-image.properties`:
```properties
Args = --no-fallback \
       -Djava.awt.headless=false \
       --initialize-at-run-time=sun.awt,com.sun.jna,sun.java2d,sun.font,java.awt.Toolkit,sun.awt.AWTAccessor,javax.swing \
       -H:+ReportExceptionStackTraces \
       -H:+AddAllCharsets \
       -H:+IncludeAllLocales \
       -H:+EnableAllSecurityServices \
       -H:+JNI \
       -H:+ForeignAPISupport \
       --enable-url-protocols=http,https \
       --add-modules=ALL-MODULE-PATH
```

### 4. System Properties Wrapper

Create `NativeMain.java`:
```java
public final class NativeMain {
    public static void main(String[] args) {
        System.setProperty("java.home", System.getenv("JAVA_HOME") != null ? 
                System.getenv("JAVA_HOME") : "/usr/lib/jvm/java-21-openjdk-amd64");
        System.setProperty("java.awt.headless", "false");
        System.setProperty("sun.java2d.fontpath", "/usr/share/fonts");
        YourMainClass.main(args);
    }
}
```

## Building with Tracing Agent

### 1. Run with Tracing Agent

```bash
./gradlew runWithAgent
```

This captures runtime reflection, JNI, and resource usage.

### 2. Build Native Image

```bash
./gradlew nativeCompile
```

### 3. Test the Executable

```bash
./build/native/nativeCompile/your-app
```

## Common Issues & Solutions

### Foreign API Support (Java 24+)

**Problem**: Runtime error about Foreign API not being supported
**Solution**: Add `-H:+ForeignAPISupport` to build args

### Missing UI Classes

**Problem**: Missing Swing UI classes in reflection config
**Solution**: Re-run tracing agent, ensure all UI interactions are exercised

### Java Home Property

**Problem**: "java.home property not set" at runtime
**Solution**: Use NativeMain wrapper to set system properties

### Build Timeout

**Problem**: Native image build times out
**Solution**: Increase memory with `-J-Xmx8G` or use shorter timeout

## Database Considerations

For applications with embedded databases:
- **H2**: Generally works but may need additional configuration
- **SQLite**: Usually works well with JDBC drivers
- **Derby**: May require specific reflection hints

Test database connectivity thoroughly after native compilation.

## Performance Results

Typical improvements with native image:
- **Startup time**: 2-3 seconds → 100-200 milliseconds
- **Memory usage**: ~50% reduction
- **Binary size**: ~130-150MB (including all dependencies)

## Deployment

The native executable includes all dependencies and can be deployed as a single file:
```bash
# Copy binary to target system
scp ./build/native/nativeCompile/your-app target-server:/usr/local/bin/
```

No JVM installation required on target system.

## Troubleshooting

### Build Fails with Missing Classes

1. Ensure tracing agent captured all code paths
2. Check reflect-config.json for missing entries
3. Run application thoroughly during agent tracing

### Runtime Crashes

1. Check native-image.properties for missing initialization settings
2. Verify all required system properties are set
3. Test with `-H:+ReportExceptionStackTraces` for detailed errors

### Performance Issues

1. Profile with `-H:+PrintGC` to check garbage collection
2. Consider `-H:+UseG1GC` for better latency
3. Optimize image heap with `--optimize=2`

## Advanced Configuration

### Custom Resource Patterns

```properties
-H:IncludeResources=.*\.(properties|xml|png|ico|icns)$
```

### Specific Module Inclusion

```properties
--add-modules=java.base,java.desktop,java.sql
```

### Build Optimization

```properties
--optimize=2
-H:+UnlockExperimentalVMOptions
```

## Version Compatibility

- **GraalVM 21**: Basic support, may need workarounds
- **GraalVM 24**: Recommended, full Foreign API support
- **Codion 0.18.x**: Fully tested and supported

## Documentation Links

- [GraalVM Native Image Guide](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Gradle Native Plugin](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html)
- [Codion Framework Documentation](https://codion.is)

---

This guide is based on the successful native image implementation of SDKBOY and other Codion applications.