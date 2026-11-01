# Auto Totem – Fabric 26.2

Client-side starter project for a private singleplayer challenge.

## Current state
- Fabric client entrypoint
- Configurable activation key (default: G)
- Opens the normal player inventory
- Scans the player inventory for the first Totem of Undying

## Not implemented yet
The visible cursor movement and GUI click sequence are intentionally left as the next step. Minecraft 26.2's exact mouse callback/mapping surface must be verified against the installed mappings before wiring synthetic GUI clicks. This avoids shipping code that silently changes inventory slots or relies on guessed APIs.

## Requirements
- Minecraft Java 26.2
- Fabric Loader 0.20.1+
- Fabric API 0.161.0+26.2
- Java 25
- Gradle 9.x compatible with the selected Fabric Loom version

## Build
Run from this folder:

```bash
gradle build
```

The mod JAR will be in `build/libs/`.
Copy it to `.minecraft/mods/` together with Fabric API.
