# Waypoint Manager 1.5.0

A client-side Fabric waypoint manager for Minecraft 26.3. The in-game screens use the supplied Waypoint Manager reference: a compact graphite workspace with a slim sidebar, world breadcrumb, quiet lime accents, dense waypoint cards, a two-column create/edit form, and focused settings panels.

## Screens

- **Waypoints** — world summary, total waypoint / dimension / last-added stats, search by name or coordinates, sort order, grid/list display, dimension filters, compact coordinate cards, pagination, import, and export.
- **Create / edit waypoint** — waypoint name, X/Y/Z coordinates, dimension selector, label and beacon toggles, HSV color square, hue slider, HEX value, block-color eyedropper, recent colors, Chroma mode, and an in-game preview. Editing also provides discard and delete actions.
- **Settings** — waypoint label, distance and coordinate display, label size, beacon beam and color matching, beam opacity, current-dimension filtering, HUD visibility, maximum display distance, and a live preview. Preferences save automatically.

The sidebar contains only **Waypoints** and **Settings**. The interface uses the supplied graphite workspace design throughout; there are no Map or About destinations.

## Controls

- **J** opens the Waypoints dashboard.
- **B** opens the Create waypoint form prefilled with the player's current coordinates.
- Select a waypoint card to edit it. The copy control on a card copies its coordinates.
- Import reads `config/simple-waypoints-import.json`; Export writes `config/simple-waypoints-export.json`.
- `/wp stop` stops active waypoint guidance.

## Storage and compatibility

Waypoints are stored in `config/simple-waypoints.json`, scoped to the active world/server. Global display preferences are stored in `config/simple-waypoints-settings.properties`. Existing waypoint records retain their color, marker, dimension, visibility, and expiry data when loaded by this version.

A maximum display distance of `0` means unlimited. Beacon columns remain subject to Minecraft's normal chunk and render-distance limits. Waypoint labels use the existing camera-relative rendering path for distant waypoints.

## UI rendering

UI text is rasterized as complete SansSerif runs with antialiasing and high-density supersampling. Cached text textures are sized for Minecraft's active GUI scale and sampled 1:1. Static preview contour artwork is shipped as high-density PNG resources; it is not drawn as hundreds of individual primitives every frame. The screens follow Minecraft's GUI Scale and do not change desktop resolution.

A 4K desktop requires a 3840×2160 display/window setting. The mod cannot guarantee 120 FPS; that depends on the PC, Minecraft render settings, shaders, resource packs, and world load.

## Build

- Minecraft: **26.3**
- Java: **25**
- Fabric Loader: **0.19.5**
- Fabric API: **0.163.0+26.3**
- Fabric Loom: **1.17.21**
- Gradle: **9.8.0**

Run from the project root:

```bash
gradle clean build --no-daemon -PwithBeam=true
```

The installable mod JAR is produced in `build/libs/`.
