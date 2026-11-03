# Simple Waypoints — Unified Field Atlas UI

Client-side Fabric waypoint mod for Minecraft 26.3 (Java 25).

## Unified design

The interface combines all four selected visual concepts into one coherent experience:

- **Main menu:** a dimension navigation rail, searchable waypoint directory, and create / edit / delete actions.
- **Waypoint editor:** a dedicated editor with coordinate fields, dimension scope, continuous color selection, HEX entry, recent colors, chroma mode, and expiry.
- **In-world labels:** compact two-line waypoint name and distance labels with a matching color accent and capped distance-aware sizing.
- **Compact inspector:** selecting a waypoint shows its color preview, dimension, coordinates, distance, expiry, and edit / delete actions beside the list. The layout adapts on smaller game windows.

The interface uses a true black surface, restrained gray controls, natural English capitalization, and Minecraft's standard UI font. In-world labels use black panels and automatically move vertically when two labels would overlap. Label drawing uses a small fixed number of fill calls, the color-picker texture is cached, and the beam renderer prioritizes the nearest waypoints to keep rendering work bounded.

## Controls

- **B** — open the create-waypoint editor, prefilled with the current position.
- **J** — open the waypoint directory.
- **/wp menu** — open the waypoint directory.

## Build

Use Java 25 and run `gradle clean build --no-daemon -PwithBeam=true`.
The installable mod JAR is produced in `build/libs/`. GitHub Actions checks that the JAR contains `fabric.mod.json` and uploads only the installable JAR (not the sources JAR).

The mod reduces its own per-frame rendering work, but no mod can guarantee 240 FPS. Actual frame rate still depends on hardware, Minecraft settings, shaders, render distance, and other mods.

## Minecraft 26.3 notes

The mod uses the `GuiGraphicsExtractor` and `MouseButtonEvent` GUI APIs, checks the open screen through `Minecraft.getInstance().gui.screen()`, and submits waypoint beams through Minecraft's native `BeaconRenderer` path.

## Waypoint label depth behaviour

Waypoint labels remain visible, but they now become translucent when a block or an entity is between the camera and the waypoint anchor. Foreground mobs and dropped items reduce label opacity more strongly, allowing the scene to show through the label rather than appearing fully covered by an opaque HUD card. Block obstruction softens the label without hiding it. Entity occlusion checks are limited to the nearest 48 blocks to keep the per-frame work bounded.
