# gradle-revapi Fork Context

## Project Overview
🚧 **gradle-revapi** is a simplified fork of the original gradle-revapi plugin focused on ease of use and configurability. This fork removes Git integration and provides explicit JAR support for API/ABI compatibility checking.

**Original Plugin**: https://github.com/palantir/gradle-revapi  
**Forked from**: https://github.com/revapi/gradle-revapi  
**Status**: Early development fork - expect breaking changes  
**Goal**: Create a more configurable and simpler API compatibility checker

## Key Information

### Plugin Details
- **Group ID**: `com.natigbabayev`
- **Plugin ID**: `com.natigbabayev.gradle-revapi`
- **Main Class**: `com.natigbabayev.revapi.gradle.RevapiPlugin`
- **Java Compatibility**: 1.8+
- **Current Branch**: `nb/gradle-revapi-android`
- **Latest Version**: `0.1.0`
- **Package Structure**: `com.natigbabayev.revapi.gradle`

### Purpose
The simplified plugin prevents accidental API/ABI breaks in Java libraries by:
- Comparing explicit JAR files or Maven artifacts
- Detecting breaking changes in public APIs
- Providing mechanisms to accept intentional breaks with justification
- Integrating with the Gradle build lifecycle
- **Key Difference**: No automatic Git-based version detection - explicit configuration required

### Core Dependencies
- **Revapi Core**: 
  - `org.revapi:revapi-basic-features` (0.8.1)
  - `org.revapi:revapi-java` (0.19.1)
  - `org.revapi:revapi-reporter-text` (0.10.1)
- **Utilities**:
  - `com.fasterxml.jackson` for JSON/YAML processing
  - `org.freemarker:freemarker` for templating
  - `com.google.guava:guava` for collections
  - `org.immutables` for immutable data structures

### Main Components

#### Core Classes
- **RevapiPlugin**: Main plugin class that registers tasks and configurations
- **RevapiExtension**: Configuration extension for customizing plugin behavior
- **RevapiAnalyzeTask**: Task that performs the actual API comparison
- **RevapiReportTask**: Task that generates reports from analysis results

#### Configuration Management
- **ConfigManager**: Handles loading/saving configuration from `.revapi/revapi.yml`
- **AcceptedBreak**: Represents intentionally accepted API breaks
- **GroupNameVersion**: Represents artifact coordinates with versioning

#### Version Resolution
- **ResolveOldApi**: Resolves previous API versions for comparison (Git integration removed)
- **Explicit JAR Support**: Direct file-based comparison via `oldJar`/`newJar` properties

### Key Features

#### Explicit Configuration
- **No automatic version detection** - users must explicitly specify versions or JAR files
- Supports direct JAR file comparison: `oldJar = file('path/to/old.jar')`
- Mixed mode: explicit old JAR with auto-resolved new JAR
- Traditional Maven artifact resolution still supported

#### Break Acceptance Workflow
Three main tasks for handling API breaks:
1. `revapiAcceptBreak` - Accept a single specific break
2. `revapiAcceptAllBreaks` - Accept all breaks in a single project
3. `revapiVersionOverride` - Override version detection when needed

#### Integration Points
- Runs as part of `./gradlew check` by default
- Can be executed independently with `./gradlew revapi`
- Integrates with CI/CD through JUnit XML output
- Supports Circle CI test reporting

### Configuration
The plugin stores configuration in `.revapi/revapi.yml` with:
- `acceptedBreaks`: List of intentionally accepted API breaks
- `versionOverrides`: Version mappings for resolution issues

### Build and Release
- Uses Gradle Plugin Portal for distribution
- Automated releases via GitHub Actions
- Supports manual releases with environment variables
- Comprehensive test suite with Spock and JUnit

### Project Structure
```
src/main/java/com/natigbabayev/revapi/gradle/
├── RevapiPlugin.java           # Main plugin class
├── RevapiExtension.java        # Configuration extension
├── RevapiAnalyzeTask.java      # Analysis task
├── RevapiReportTask.java       # Reporting task
├── ConfigManager.java          # Configuration management
├── GitVersionUtils.java        # Version detection (Git integration removed)
└── config/                     # Configuration data classes
    ├── AcceptedBreak.java
    ├── GroupNameVersion.java
    └── ...
```

### Usage

#### Explicit JAR Comparison (Recommended)
```gradle
plugins {
    id 'com.natigbabayev.gradle-revapi' version '0.1.0'
}

revapi {
    oldJar = file('libs/mylib-1.0.0.jar')
    newJar = file('libs/mylib-2.0.0.jar')
}
```

#### Traditional Maven Artifact Comparison
```gradle
plugins {
    id 'com.natigbabayev.gradle-revapi' version '0.1.0'
    id 'java-library'
}

revapi {
    oldGroup = 'com.example'
    oldName = 'my-library'
    oldVersion = '1.0.0'  // Must be explicitly set - no Git auto-detection
}
```

The plugin integrates with the build lifecycle and runs API compatibility checks during the `check` phase.