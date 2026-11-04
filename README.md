# Simple Waypoints 1.2.0 — Waypoint Manager UI

Client-side Fabric waypoint manager for Minecraft 26.3 (Java 25).

## Menu

- **J** opens the full-screen Waypoint Manager dashboard.
- **B** opens the Create Waypoint editor prefilled with the player's coordinates.
- The dashboard has dimension filters, search, recently-added/name sorting, grid/list layouts, cards, pagination, and JSON import/export.
- Export writes the active world's waypoints to `config/simple-waypoints-export.json`. Import reads `config/simple-waypoints-import.json`, maps entries into the active world, and updates matching name/dimension pairs.
- The editor has location details, dimension selection, HEX and visual color selection, Chroma, recent colors, expiry, an in-game preview, and per-waypoint label/beam switches.
- Settings contains global label, distance, coordinate, label-size, beacon-beam, beam-color, beam-intensity, dimension-scope, HUD-visibility, and maximum display-distance preferences.

## Storage

Waypoints remain in `config/simple-waypoints.json`. Per-waypoint visibility fields are backward-compatible: waypoints from earlier versions default to showing both labels and beams.

Global display preferences are saved in `config/simple-waypoints-settings.properties` and survive restarts.

A maximum display distance of `0` means unlimited. This preserves the long-range direction labels for waypoints tens of millions of blocks away unless the user chooses a finite limit. Beacon columns remain subject to Minecraft's native chunk/render-distance limits.

## Rendering

- Labels retain camera-relative proxy rendering for extreme waypoint distances.
- The label plate has a fully opaque matte-black inset and a continuous waypoint-colored border.
- Labels remain depth-tested against mobs, players, items, and nearby terrain according to the existing renderer's visibility behavior.
- Distance and coordinate lines are controlled by the Settings page.
- The beam opacity control adjusts beam color intensity; color matching can be disabled independently.

## Build

Use Java 25 and Gradle 9.8 or compatible Loom tooling:

```bash
gradle clean build --no-daemon -PwithBeam=true
```

The installable mod JAR is produced in `build/libs/`.
