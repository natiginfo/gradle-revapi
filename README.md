# gradle-revapi-simple

🚧 **Early Stage Fork** - This is a simplified fork of the gradle-revapi plugin focused on ease of use and configurability.

**Original Plugin**: https://github.com/palantir/gradle-revapi  
**Forked from**: https://github.com/revapi/gradle-revapi  
**Status**: Early development - expect breaking changes  
**Goal**: Create a more configurable and simpler API compatibility checker, with future plans for Android library support

_A simplified Gradle plugin that runs [Revapi](https://revapi.org) to detect API/ABI breaks in your Java libraries._

## Key Differences from Original

- ✅ **Explicit JAR Support**: Direct file-based API comparison without Git dependency
- ✅ **Simplified Configuration**: Reduced complexity for common use cases  
- ✅ **Removed Git Integration**: No automatic Git tag detection - explicit configuration required
- 🚧 **Future Android Support**: Planned enhanced support for Android libraries

## Quick Start

### Basic Usage (Explicit JAR Comparison)

```gradle
plugins {
    id 'com.natigbabayev.gradle-revapi' version '0.1.0'
}

revapi {
    oldJar = file('libs/mylib-1.0.0.jar')
    newJar = file('libs/mylib-2.0.0.jar')
}
```

Run: `./gradlew revapi`

### Traditional Usage (Maven Repository)

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

Run as part of `./gradlew check` or directly with `./gradlew revapi`.

**Note**: Unlike the original plugin, this fork does not automatically detect previous versions from Git tags. You must explicitly specify the `oldVersion`.

### Advanced: Mixed Mode

You can also mix explicit JARs with automatic resolution:

```gradle
revapi {
    oldJar = file('path/to/old-version.jar')  // Explicit old JAR
    // newJar will be auto-resolved from current project
}
```

## Motivation

Accidentally releasing API or ABI breaks in java libraries has bad consequences for library consumers.
In the case of API breaks, consumers have to perform some kind of manual action to upgrade to newer library
versions, which may be difficult.

With ABI breaks, the situation can be even worse, as uses of the library compile but uses in jars of the old API fail at
runtime. An example from [Tritium](https://github.com/palantir/tritium) is where a
[method was changed from a `Map` to a `SortedMap`](https://github.com/palantir/tritium/pull/272#issuecomment-496526307).
This compiles against direct dependencies but transitive dependencies using the older API
would produce a `NoSuchMethodError` at runtime, which has caused a number of problems in production code. Similarly,
there was a covariant return type change to `docker-compose-rule` (`ImmutableDockerComposeRule` -> `DockerComposeRule`)
which [caused ABI breaks in `docker-proxy-rule`](https://github.com/palantir/docker-proxy-rule/releases/tag/0.8.0),
among projects.

## Configuration

`gradle-revapi` should work out of the box for most uses cases once applied. By default it compares against the previous
version of the jar from the project it is applied in by finding the last tag using `git describe`. However, if you need
to need to override the artifact to compare against, you can do so:

```gradle
revapi {
    oldGroup = '<artifact-group>'
    oldNamed = '<artifact-name>'
    oldVersion = '<artifact-version>'
}
```

### Accepting breaks

Sometimes you may wish to break your API, or feel that the particular API break identified by revapi is acceptable to
release. In these cases, there is an escape hatch you can use which should be automatically recommended to you in the
error message `gradle-revapi` produces.

* To accept a single break, run:
  ```
  ./gradlew revapiAcceptBreak --justification "{why this is ok}" \
          --code "{revapi check code}" \
          --old "{optional revapi description of old element}" \
          --new "{optional revapi description of new element}"
  ```

* To accept all the breaks in a gradle project run:
  ```
  ./gradlew :project:revapiAcceptAllBreaks
  ```

* To accept all the breaks in all gradle projects run:
  ```
  ./gradlew revapiAcceptAllBreaks
  ```

Running any of these tasks will add the breaks to the `.revapi/revapi.yml` file in the format"

```yml
acceptedBreaks:
  version:
    group:name:
    - code: "class"
      old: "class OldClass"
      new: null
      justification: "No one was using this"
```

### Version overrides

Sometimes the previous release will have a successfully applied a git tag but a failed publish build. In this
case `gradle-revapi` will fail as it cannot resolve the previous API to compare against. To resolve this, you can
possible to set a *version override* that will use a different version instead of the last git tag. To do so,
use the

```
./gradle revapiVersionOverride --replacement-version <last-published-version>
```

task to use correctly published version instead. This will creare an entry in `.revapi/revapi.yml` of the following
format:

```yml
versionOverrides:
  group:name:version: versionOverride
```

## Publishing This Fork

This section explains how to publish your own version of this plugin.

### Prerequisites

1. **Gradle Plugin Portal Account**: Sign up at https://plugins.gradle.org/
2. **API Keys**: Get your API key and secret from your account settings
3. **GitHub Repository**: Fork this repository to your own GitHub account

### Steps to Publish

1. **Update Repository URLs**: 
   ```gradle
   // In build.gradle, update these lines:
   website = 'https://github.com/yourusername/gradle-revapi-simple'
   vcsUrl = 'https://github.com/yourusername/gradle-revapi-simple'
   ```

2. **Set Environment Variables**:
   ```bash
   export GRADLE_KEY="your-api-key"
   export GRADLE_SECRET="your-api-secret"
   export RELEASE_VERSION="1.0.0"  # Your desired version
   ```

3. **Publish to Gradle Plugin Portal**:
   ```bash
   ./gradlew publishPlugins
   ```

### Alternative: Local Publishing

For testing or private use:
```bash
./gradlew publishToMavenLocal
```

### Publishing Checklist

- [ ] Update plugin version in `gradle.properties` or via `RELEASE_VERSION`
- [ ] Update repository URLs in `build.gradle`
- [ ] Test the plugin locally (`./gradlew build`)
- [ ] Update README with your plugin ID and usage instructions
- [ ] Publish to Gradle Plugin Portal
- [ ] Tag the release in Git
- [ ] Update GitHub release notes
