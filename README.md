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

## Waypoint label behaviour

Labels are real world-space text, drawn the way vanilla name tags are.

- **Clear view:** letters, dark plate and outline use normal depth testing, so a mob, player, dropped item or block
  in front of the label hides exactly the pixels it covers. The label never draws on top of them.
- **Terrain in the way:** every frame the mod checks whether a solid block is between you and the label. If so, the
  label is drawn through the terrain (hills, placed blocks, walls) so it can always guide you back when you are lost.
- **Size:** small when you are close, growing smoothly with distance so it stays big and readable far away
  (about 13 px per line near, about 34 px far). The "Logo" size setting scales it (Small 0.8x, Large 1.25x).
- **Outline:** a 1-pixel outline around the plate in the same color as the beam. A rainbow/chroma beam gives a rainbow outline.
- The label sits toward you from the beam so it is not half-hidden inside it. It is submitted facing both ways; text
  that faces away is culled, so only the side facing you is drawn.

If `.minecraft/logs/latest.log` contains `[Simple Waypoints] submitting N world-space label(s)`, labels are being sent
to the renderer.
