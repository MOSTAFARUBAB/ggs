# Simple Waypoints — Waypoint Station

Client-side Fabric waypoint mod for Minecraft 26.3 (Java 25).

## Included
- Native Minecraft `BeaconRenderer` 3D waypoint beams.
- Floating waypoint name + distance labels, enabled by default and toggleable from the menu.
- Dark deep-ocean / mint / violet Waypoint Station menu theme.
- Create, edit, and delete waypoints; color selection; dimension filters; save persistence.
- Compact, fixed-cost HUD label drawing and a static menu layout designed to avoid per-frame texture generation or animation work.

## Controls
- **B** — create a waypoint.
- **J** — open the waypoint list.
- **/wp menu** — open the waypoint list.

## Build
Use Java 25 and run `gradle clean build --no-daemon -PwithBeam=true`.
The mod JAR is produced in `build/libs/`. GitHub Actions uploads the JAR as an artifact after a successful build.

A 240 FPS result cannot be guaranteed by a mod alone; actual frame rate depends on the game, GPU, CPU, shaders, render distance, and other mods. The menu avoids continuous animation and regenerates the color gradient only when its hue changes.


## Minecraft 26.3 compile fix

The waypoint-label HUD now checks `Minecraft.getInstance().gui.screen()` (the 26.3 screen manager API) instead of the removed `Minecraft.screen` field. This keeps waypoint labels hidden while a menu/screen is open.
