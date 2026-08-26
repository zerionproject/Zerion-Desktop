# opus-jvm: vendored Opus codec

This module contains the pure-Java Opus audio codec `org.concentus`, vendored
verbatim from the Concentus project so that group voice messages can be encoded
and decoded on the desktop without any native library.

- Upstream project: Concentus (a pure-Java/C# port of the reference Opus codec)
- Upstream repository: https://github.com/lostromb/concentus
- Vendored commit: `6c2328dc19044601e33a9c11628b8d60e1f3011c`
- Vendored path: `Java/Concentus/src/main/java/org/concentus`
- Files: 124 `.java` source files, unmodified
- License: BSD-3-Clause (see `LICENSE`), the standard Opus / Xiph license
- Transitive dependencies: none
- Native code: none
- Network / telemetry: none (it is a codec; it performs no I/O)

## Why vendored rather than a Maven/JitPack dependency

Concentus is not published on Maven Central, and its JitPack builds are
produced on demand and are not reproducible. Vendoring the source at a fixed
commit gives an exact, inspectable, reproducible pin with no additional build
repositories, which is the safest option for a security-sensitive project.

## Maintenance note

The upstream codec is stable but not actively developed. This is acceptable
because the Opus bitstream format is frozen (RFC 6716); a codec that decodes
and encodes it correctly does not need ongoing changes. The Ogg container is
handled separately by `org.gagravarr:vorbis-java-core` from Maven Central.

## Companion dependency: Ogg container

The Ogg container is handled by gagravarr's vorbis-java, resolved from Maven
Central (not vendored). It is pure-JVM with no transitive dependencies and no
native code.

- Coordinate: `org.gagravarr:vorbis-java-core:0.8`
- License: Apache-2.0
- Transitive dependencies: none
- SHA-256 (jar): `879bb0c8923fea686609e207fd9050ab246e001868341c725929405e755cf68e`
- SHA-256 (pom): `7f6ac4671c2e0aae25f8f813d86d19899fa63fb261be787c7b56e5be94ed2513`

The desktop build runs with `--dependency-verification lenient` (its
established workflow), so these hashes are recorded here for auditability and
pinning. The strict `gradle/verification-metadata.xml` is used by the mobile
build and is intentionally not modified here.

## Updating

To update, re-extract `Java/Concentus/src/main/java/org/concentus` from a newer
upstream commit, record the new commit here, and re-run the group-voice codec
tests.
