# Simple Waypoints — Unified Field Atlas UI

Client-side Fabric waypoint mod for Minecraft 26.3 (Java 25).

## Unified design

The interface combines all four selected visual concepts into one coherent experience:

- **Main menu:** a dimension navigation rail, searchable waypoint directory, and create / edit / delete actions.
- **Waypoint editor:** a dedicated editor with coordinate fields, dimension scope, continuous color selection, HEX entry, recent colors, chroma mode, and expiry.
- **In-world labels:** compact two-line waypoint name and distance labels rendered as actual world-space text with scene depth testing.
- **Compact inspector:** selecting a waypoint shows its color preview, dimension, coordinates, distance, expiry, and edit / delete actions beside the list. The layout adapts on smaller game windows.

The interface uses a true black surface, restrained gray controls, natural English capitalization, and Minecraft's standard UI font. The label renderer submits native world-space text rather than a 2D HUD overlay. It draws up to 16 labels and 12 beams per frame; the color-picker texture is cached. Label visibility is independent of the chunk render-distance setting.

## Controls

- **B** — open the create-waypoint editor, prefilled with the current position.
- **J** — open the waypoint directory.
- **/wp menu** — open the waypoint directory.

## Build

Use Java 25 and run `gradle clean build --no-daemon`.
The installable mod JAR is produced in `build/libs/`. GitHub Actions checks that the JAR contains `fabric.mod.json` and uploads only the installable JAR (not the sources JAR).

The mod reduces its own per-frame rendering work, but no mod can guarantee 240 FPS. Actual frame rate still depends on hardware, Minecraft settings, shaders, render distance, and other mods.

## Minecraft 26.3 notes

The mod uses the `GuiGraphicsExtractor` and `MouseButtonEvent` GUI APIs, checks the open screen through `Minecraft.getInstance().gui.screen()`, submits waypoint beams through Minecraft's native `BeaconRenderer` path, and submits labels through the 26.3 world-text feature renderer.

## Waypoint label behaviour

Labels are world-space text with a camera-relative rendering position. Their on-screen direction follows the saved waypoint, while the renderer keeps the text close enough to the camera to avoid Minecraft's far-world clipping.

- **Long-range visibility:** labels are not removed at the edge of the chunk render distance. They remain directionally positioned even for waypoints tens of millions of blocks away, provided they are saved in the active world and dimension.
- **Clear nearby view:** letters, matte-black plate and colored outline use normal depth testing, so a mob, player, dropped item or block in front of the label hides it.
- **Terrain in the way:** within 256 blocks, a terrain check switches the label to see-through rendering when a solid block blocks the view. Beyond 256 blocks, the label is deliberately see-through to avoid expensive ray casts through unloaded chunks.
- **Plate and outline:** the inset is fully opaque matte black. A continuous 1-pixel rectangular frame uses the waypoint/beam color, including chroma colors.
- **Size:** constant on screen at every distance (about 13 px per line); distance does not enlarge the text or plate.
- **Position:** the label floats 2.5 blocks above the waypoint and sits slightly toward the camera from its beam.

The native vertical beacon beam remains subject to Minecraft's normal world/chunk render distance. The long-range visibility behaviour described above applies to labels.

If `.minecraft/logs/latest.log` contains `[Simple Waypoints] submitting N world-space label(s)`, labels are being sent
to the renderer.

## 1.1.3 build fix

- Fixed Java compilation: the mob query now uses `Mob::isAlive` without comparing `Mob` to `LocalPlayer`. The query is already restricted to `Mob.class`, so the player is not included.
