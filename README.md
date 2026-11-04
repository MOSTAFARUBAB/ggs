# Simple Waypoints — Unified Field Atlas UI

Client-side Fabric waypoint mod for Minecraft 26.3 (Java 25).

## Unified design

The interface combines all four selected visual concepts into one coherent experience:

- **Main menu:** a dimension navigation rail, searchable waypoint directory, and create / edit / delete actions.
- **Waypoint editor:** a dedicated editor with coordinate fields, dimension scope, continuous color selection, HEX entry, recent colors, chroma mode, and expiry.
- **In-world labels:** compact two-line waypoint name and distance labels rendered as actual world-space text with scene depth testing.
- **Compact inspector:** selecting a waypoint shows its color preview, dimension, coordinates, distance, expiry, and edit / delete actions beside the list. The layout adapts on smaller game windows.

The interface uses a true black surface, restrained gray controls, natural English capitalization, and Minecraft's standard UI font. The label renderer submits native world-space text rather than a 2D HUD overlay. It draws at most 16 nearby labels and 12 beams per frame; the color-picker texture is cached.

## Controls

- **B** — open the create-waypoint editor, prefilled with the current position.
- **J** — open the waypoint directory.
- **/wp menu** — open the waypoint directory.

## Build

Use Java 25 and run `gradle clean build --no-daemon -PwithBeam=true`.
The installable mod JAR is produced in `build/libs/`. GitHub Actions checks that the JAR contains `fabric.mod.json` and uploads only the installable JAR (not the sources JAR).

The mod reduces its own per-frame rendering work, but no mod can guarantee 240 FPS. Actual frame rate still depends on hardware, Minecraft settings, shaders, render distance, and other mods.

## Minecraft 26.3 notes

The mod uses the `GuiGraphicsExtractor` and `MouseButtonEvent` GUI APIs, checks the open screen through `Minecraft.getInstance().gui.screen()`, submits waypoint beams through Minecraft's native `BeaconRenderer` path, and submits labels through the 26.3 world-text feature renderer.

## Waypoint label depth behaviour

Labels are real world-space text, drawn the way vanilla name tags are. Both the letters and the dark strip behind
them use normal depth testing, so a mob, player, dropped item or block in front of the label hides exactly the
pixels it covers; the label never draws on top of them.

The label floats 1.85 blocks above the waypoint and sits half a block toward you, so it is in front of its own beam
instead of half-hidden inside it. It is submitted facing both ways, and only the side facing you is drawn (text that
faces away is culled, which is what made the previous build show only the beam).

If Minecraft's log (`.minecraft/logs/latest.log`) contains `[Simple Waypoints] submitting N world-space label(s)`,
the labels are being sent to the renderer.
