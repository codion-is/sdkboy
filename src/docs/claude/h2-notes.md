# H2 Database with GraalVM Native Image

## Overview

H2 embedded database generally works with GraalVM native image but may require specific configuration for optimal compatibility.

## Known Compatibility

- **H2 2.x**: Generally compatible with GraalVM 
- **Embedded mode**: Works with proper reflection configuration
- **JDBC**: Standard JDBC operations work fine
- **File-based**: Persistent file databases work
- **In-memory**: Memory databases work well

## Configuration Requirements

### 1. System Properties

```java
// In NativeMain.java
System.setProperty("h2.bindAddress", "localhost");
System.setProperty("h2.serverCachedObjects", "256");
```

### 2. Reflection Configuration

H2 may need reflection hints for:
- Database engine classes
- JDBC driver initialization
- SQL parser components

These should be captured by the tracing agent when running database operations.

### 3. Resource Inclusion

H2 may need SQL resources and configuration files:
```properties
-H:IncludeResources=.*\.sql$
-H:IncludeResources=.*h2.*\.properties$
```

## Testing Strategy

When running the tracing agent, ensure you exercise:

1. **Database connection** - Opening/closing connections
2. **Table creation** - DDL operations
3. **CRUD operations** - Insert, update, delete, select
4. **Transactions** - Commit/rollback scenarios
5. **Schema operations** - Any schema modifications

## Common Issues

### Connection Problems

**Symptom**: Cannot connect to H2 database in native image
**Solution**: Check system properties and ensure database URL is correct

### Missing SQL Functions

**Symptom**: SQL functions not found at runtime
**Solution**: Ensure tracing agent captures all SQL usage patterns

### File Locking

**Symptom**: Database file locked errors
**Solution**: Proper database URL configuration and shutdown handling

## Alternative Databases

If H2 proves problematic:

### SQLite
- **sqlite-jdbc** driver generally works well with GraalVM
- Single file database
- No server process needed
- Good native image compatibility

### Derby
- Java-based embedded database
- May need additional reflection configuration
- Larger footprint than H2/SQLite

### File-based Solutions
- Consider JSON/XML files for simple data storage
- Perfect GraalVM compatibility
- May be sufficient for demo applications

## Build Configuration

Example gradle configuration for H2:

```kotlin
dependencies {
    runtimeOnly("com.h2database:h2:2.2.224")
}

graalvmNative {
    binaries {
        named("main") {
            buildArgs.addAll(
                "-H:+ReportExceptionStackTraces",
                "-H:IncludeResources=.*\\.sql$"
            )
        }
    }
}
```

## Testing Checklist

Before declaring H2 + native image successful:

- [ ] Database file creation
- [ ] Schema initialization
- [ ] Entity persistence (save/update/delete)
- [ ] Complex queries with joins
- [ ] Transaction handling
- [ ] Connection pooling (if used)
- [ ] Application startup/shutdown cycles
- [ ] Database file portability

## Performance Considerations

- **Startup**: H2 initialization should be fast in native image
- **Query performance**: Should be comparable to JVM version
- **Memory usage**: May be lower due to native image optimizations
- **File I/O**: Database file operations should work normally

## Fallback Strategy

If H2 embedded proves too complex:

1. **External H2 server**: Run H2 as separate process, connect via TCP
2. **Different database**: Switch to SQLite or PostgreSQL
3. **Mock data**: Use in-memory collections for demo purposes
4. **Hybrid approach**: JVM version for development, investigate native later

## Resources

- [H2 Database Documentation](http://h2database.com/)
- [GraalVM Native Image JDBC](https://www.graalvm.org/latest/reference-manual/native-image/guides/use-native-image-with-jdbi/)
- [H2 Configuration Options](http://h2database.com/html/features.html)

The key is thorough testing with the tracing agent to capture all H2's runtime requirements.