# GraalVM Native Image Quick Reference

## Essential Steps (In Order)

1. **Install GraalVM 24**
   ```bash
   sdk install java 24.0.1-graal
   sdk use java 24.0.1-graal
   ```

2. **Configure gradle.properties**
   ```properties
   org.gradle.java.home=/path/to/graalvm-24
   ```

3. **Add Native Plugin to build.gradle.kts**
   ```kotlin
   plugins {
       id("org.graalvm.buildtools.native") version "0.10.4"
   }
   ```

4. **Create NativeMain wrapper class**
   - Sets system properties needed at runtime
   - Delegates to actual main class

5. **Run tracing agent FIRST**
   ```bash
   ./gradlew runWithAgent
   ```

6. **Build native image**
   ```bash
   ./gradlew nativeCompile
   ```

## Critical Configuration

### native-image.properties
```properties
Args = --no-fallback \
       -Djava.awt.headless=false \
       -H:+ForeignAPISupport \
       -H:+JNI
```

### System Properties (in NativeMain)
```java
System.setProperty("java.home", javaHome);
System.setProperty("java.awt.headless", "false");
System.setProperty("sun.java2d.fontpath", "/usr/share/fonts");
```

## Key Learnings from SDKBOY

### ✅ What Works
- **GraalVM 24**: Best compatibility with Foreign API
- **Tracing agent**: Captures 95% of requirements automatically
- **Swing/AWT**: Works great with proper initialization
- **FlatLaF themes**: All 41 themes work perfectly
- **Progressive build**: Can build while downloads are active

### ⚠️ Common Gotchas
- **Empty initialize-at-build-time**: Causes build failure (remove empty arg)
- **Missing java.home**: Causes runtime crash (set in NativeMain)
- **Wrong GraalVM path**: Build fails silently (check gradle.properties)
- **Skipping tracing agent**: Missing reflection config causes runtime errors

### 🚀 Performance Results
- **Startup**: 2-3 seconds → 100-200ms
- **Memory**: ~50% reduction
- **Binary size**: ~130-150MB (all dependencies included)
- **Theme switching**: Works seamlessly even during background tasks

## Database Specific (for PetClinic)

### H2 Considerations
- Set `h2.bindAddress=localhost` system property
- Ensure all CRUD operations are exercised during agent tracing
- Test connection lifecycle (open/close/reconnect)
- Verify transaction handling works

### Reflection Hints
H2 typically needs reflection for:
- JDBC driver classes
- SQL engine components  
- Connection pool implementations

## Troubleshooting Commands

### Check GraalVM Version
```bash
java -version
native-image --version
```

### Debug Build Issues
```bash
./gradlew nativeCompile --info
```

### Test Native Binary
```bash
JAVA_HOME=/path/to/graalvm ./build/native/nativeCompile/app-name
```

### Analyze Binary Size
```bash
ls -lh build/native/nativeCompile/
```

## Success Criteria for PetClinic

- [ ] **Application starts** without errors
- [ ] **Database connects** and initializes schema
- [ ] **All CRUD operations** work (owners, pets, visits)
- [ ] **Master-detail navigation** functions correctly
- [ ] **Forms validate** and save data properly
- [ ] **UI themes** can be switched dynamically
- [ ] **Startup time** under 500ms
- [ ] **Binary runs** on systems without JVM

## Emergency Fallbacks

If native build fails:
1. **Check logs**: Look for specific missing classes/resources
2. **Re-run agent**: Ensure all code paths were exercised
3. **Add manual hints**: Create reflect-config.json entries
4. **Simplify config**: Remove optional build args
5. **Use GraalVM 21**: If 24 has issues
6. **External database**: If H2 embedded is problematic

## Final Notes

- **Be patient**: First build takes 5-10 minutes
- **Test thoroughly**: Exercise all features during agent tracing
- **Keep it simple**: Start with minimal config, add as needed
- **Document issues**: Note any PetClinic-specific problems for future reference

The SDKBOY implementation proves Codion + GraalVM native works beautifully. PetClinic should be very achievable! 🚀