# Claude Priming Package for PetClinic GraalVM Native

This folder contains everything needed to convert the Codion PetClinic demo to GraalVM native image, based on the successful SDKBOY implementation.

## Contents

- `GRAALVM_NATIVE.md` - Complete guide with lessons learned from SDKBOY
- `graalvm-reference/` - Template configuration files
- `h2-notes.md` - Research on H2 embedded database compatibility
- `quick-reference.md` - Key learnings and gotchas

## Process Overview

1. **Setup**: Use template files to configure GraalVM plugin
2. **Agent**: Run tracing agent to capture PetClinic-specific metadata
3. **Build**: Configure native image with Foreign API support
4. **Test**: Verify all CRUD operations work in native binary

## Key Differences from SDKBOY

- PetClinic has **database integration** (H2 embedded)
- **More complex entity relationships** (owners → pets → visits)
- **Richer UI interactions** (forms, master-detail, validation)
- **Larger reflection surface** (JPA entities, database drivers)

Success criteria: Full CRUD PetClinic app as fast-starting native binary!