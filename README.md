# Simple Waypoints — Field Atlas UI

Client-side Fabric waypoint mod for Minecraft 26.3 (Java 25).

## Included
- Native Minecraft `BeaconRenderer` 3D waypoint beams.
- Floating, two-line waypoint labels: waypoint name and distance, with distance-aware sizing capped at a readable maximum.
- A redesigned Field Atlas interface with a left navigation rail, scope filters, beam/name switches, and a separate saved-location workspace.
- Custom Create, Edit, Delete, page navigation, and close controls.
- Create, edit, delete, color selection, dimension filters, and persistent waypoint saving.
- A matching warm graphite / brass / ice accent palette on the editor.
- Fixed-cost UI drawing: no continuous UI animation or per-frame texture generation.

## Controls
- **B** — create a waypoint.
- **J** — open the waypoint directory.
- **/wp menu** — open the waypoint directory.

## Build
Use Java 25 and run `gradle clean build --no-daemon -PwithBeam=true`.
The installable mod JAR is produced in `build/libs/`. GitHub Actions verifies the JAR contains `fabric.mod.json` and uploads only the installable JAR (not the sources JAR).

A 240 FPS result cannot be guaranteed by a mod alone. Actual frame rate depends on the game, GPU, CPU, shaders, render distance, and other mods. The menu avoids continuous animation and regenerates the color gradient only when its hue changes.

## Minecraft 26.3 compile note
The waypoint-label HUD uses `Minecraft.getInstance().gui.screen()` to detect an open screen. The UI uses the Minecraft 26.3 `GuiGraphicsExtractor` and `MouseButtonEvent` APIs.
