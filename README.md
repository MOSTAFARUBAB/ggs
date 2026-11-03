# Simple Waypoints — Unified Field Atlas UI

Client-side Fabric waypoint mod for Minecraft 26.3 (Java 25).

## Unified design

The interface combines all four selected visual concepts into one coherent experience:

- **Main menu:** a dimension navigation rail, searchable waypoint directory, and create / edit / delete actions.
- **Waypoint editor:** a dedicated editor with coordinate fields, dimension scope, continuous color selection, HEX entry, recent colors, chroma mode, and expiry.
- **In-world labels:** compact two-line waypoint name and distance labels with a matching color accent and capped distance-aware sizing.
- **Compact inspector:** selecting a waypoint shows its color preview, dimension, coordinates, distance, expiry, and edit / delete actions beside the list. The layout adapts on smaller game windows.

The interface uses a midnight-blue surface with cyan and violet accents. Rendering is static and avoids continuous decorative animation or large per-frame drawing loops. The color picker texture is cached and refreshed only when its hue changes.

## Controls

- **B** — open the create-waypoint editor, prefilled with the current position.
- **J** — open the waypoint directory.
- **/wp menu** — open the waypoint directory.

## Build

Use Java 25 and run `gradle clean build --no-daemon -PwithBeam=true`.
The installable mod JAR is produced in `build/libs/`. GitHub Actions checks that the JAR contains `fabric.mod.json` and uploads only the installable JAR (not the sources JAR).

A 240 FPS result cannot be guaranteed by a mod alone; actual frame rate depends on hardware, Minecraft settings, shaders, render distance, and other mods.

## Minecraft 26.3 notes

The mod uses the `GuiGraphicsExtractor` and `MouseButtonEvent` GUI APIs, checks the open screen through `Minecraft.getInstance().gui.screen()`, and submits waypoint beams through Minecraft's native `BeaconRenderer` path.
