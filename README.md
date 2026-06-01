# ModsSync

A Minecraft mod built to target **multiple loaders and game versions**.

## Repository layout

All buildable mod projects live under `multi/`, organized by loader, then by
Minecraft version:

```
multi/
├── neoforge/
│   └── 26-1-2/        ← active project (NeoForge, MC 26.1.2) — fully buildable
├── forge/
├── fabric/
└── quilt/
```

Each `<loader>/<version>/` directory is a **self-contained Gradle project**
(its own `build.gradle`, `gradlew`, `src/`, etc.). They are independent and can
be built in isolation.

## Current status

`multi/neoforge/26-1-2/` is the canonical implementation. The mod is being
developed there first.

- **mod id:** `modssync`
- **package:** `com.modssync`
- **Minecraft:** 26.1.2
- **NeoForge:** 26.1.2.68-beta
- **Java:** 25

## Porting to other loaders / versions

Once the NeoForge 26.1.2 implementation is complete, each port is created by
**cloning** `multi/neoforge/26-1-2/` into the appropriate
`multi/<loader>/<version>/` directory and **translating** the loader-specific
APIs (registration, event bus, mod metadata, etc.) for the target platform.

## Building

```sh
cd multi/neoforge/26-1-2
./gradlew build          # or gradlew.bat build on Windows
```

To run the client/server in a dev environment:

```sh
./gradlew runClient
./gradlew runServer
```
