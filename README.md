# Simple Waypoints — Native Beacon Beam Only (Minecraft 26.3)

A client-side Fabric waypoint mod. Each saved waypoint is marked in the world with Minecraft's own `BeaconRenderer.submitBeaconBeam` renderer. This uses the vanilla beacon beam texture and scrolling animation, using the vanilla beacon's own `SOLID_BEAM_RADIUS` and `BEAM_GLOW_RADIUS` constants. The inner core and outer glow therefore use the same geometry as a normal Minecraft beacon.

There are no world-space waypoint names, distance labels, logo/square markers, or ground rings. The B key opens the new-waypoint editor; J opens the waypoint list. Waypoints are saved locally and separated by world/server and dimension.

## Target versions
- Minecraft Java 26.3
- Java 25
- Fabric Loader 0.19.5
- Fabric API 0.163.0+26.3
- Fabric Loom 1.18-SNAPSHOT (non-remapping Loom)

## Build with GitHub Actions
1. Upload every item in this folder to the root of your GitHub repository, including the hidden `.github` folder.
2. Push to your branch or run the workflow manually from **Actions**.
3. Wait for the workflow to succeed.
4. Download the `simple-waypoints-mc-26.3-beam-only` artifact.
5. Extract the artifact ZIP and put the `.jar` inside your `.minecraft/mods` folder.

## Build locally
Use JDK 25 and Gradle 9.8, then run:

```bash
gradle clean build --no-daemon -PwithBeam=true
```

The installable mod JAR is produced in `build/libs/`. Do not put this source ZIP in the mods folder.

## Verification status
The source and Gradle configuration have been corrected for non-remapping Loom (`implementation`, not `modImplementation`). This environment does not have JDK 25 or Gradle installed, so the project has **not** been compiled or tested in Minecraft here. GitHub Actions must pass before the JAR is considered ready.


## Visual changes in this revision
- Uses the exact vanilla beacon beam radius constants instead of custom beam widths.
- Keeps the vanilla beacon texture, renderer geometry, and scrolling animation.
- Removes the beam thickness selector so the beam cannot be changed away from vanilla proportions.
- Updates the menu panels to larger rounded corners with a soft edge.
- Draws the saturation/brightness picker at one-pixel resolution and handles picker input continuously without creating hundreds of hidden widgets.

The project still requires a successful GitHub Actions build and an in-game check. The native renderer call is confirmed against the Minecraft 26.3 `BeaconRenderer` API documentation, but this archive has not been compiled in this environment.

## Minecraft 26.3 input compatibility fix

The waypoint editor now handles clicks through `MouseButtonEvent` and the Minecraft 26.3 `mouseClicked(MouseButtonEvent, boolean)` signature. The color picker still handles its own click coordinates and delegates all other clicks to the normal screen widget handler.


## Recent rendering and menu fixes

- Waypoint beams are submitted during Fabric's `COLLECT_SUBMITS` phase so Minecraft's renderer can sort and draw the vanilla beacon's solid core and translucent shell in their normal passes.
- The color picker uses SDL3's left mouse button value (`1`) for Minecraft 26.3.
- The saturation/brightness square is cached as a 256x128 dynamic texture displayed at 128x64 GUI size and uploaded only when the hue changes; the previous version queued thousands of individual GUI fills every frame.

The project still needs a successful Java 25 / Gradle 9.8 GitHub Actions build and an in-game check before these changes can be called verified.


## Update: native beam, drag color picker, and menu performance

- Waypoint beams are now submitted only through Minecraft's native `BeaconRenderer` path. The old screen-space beam fallback has been removed because it could appear as a flat billboard and change/disappear as the camera moved.
- Removed the close-range exclusion that hid the beam when the player stood within half a block of its waypoint.
- The waypoint beam is a full 2048-block column from the dimension minimum Y. Its cross-section, scrolling texture, rotation, core radius, translucent shell radius, and alpha are submitted by the same vanilla `BeaconRenderer` helper as a beacon.
- Beam radius follows the modern vanilla distance scaling rule (1x through 96 blocks, increasing up to 2x at this mod's 192-block render limit).
- The saturation/value color square and hue strip support click-and-drag selection using the current GUI-scaled cursor position (not raw SDL pixel deltas).
- The hue strip is a cached texture, so it is drawn with one blit instead of 128 fill calls per frame. The saturation/value texture only regenerates when hue changes and is capped at roughly 60 uploads per second.
- Waypoint labels default to off; the in-world marker is the beam only.


## Update: color dragging and vanilla beam visibility

- Color dragging reads the live GUI-scaled cursor position from `MouseHandler`, avoiding DPI-scale drift from raw SDL movement deltas.
- The editor also follows `mouseMoved` while the left button is held and applies the final release position before ending a drag.
- Both color gradients are textures: the hue strip is generated once; the saturation/value square is refreshed only when hue changes.
- The waypoint beam uses Minecraft's public `BeaconRenderer.submitBeaconBeam` helper with vanilla core/outer radii and vanilla 40-tick animation timing.
- A vanilla beacon beam is intentionally not visible from inside its own beam. A waypoint placed on the player's current block can therefore look as though it vanishes while the camera is inside the narrow beam; stepping outside the beam should reveal it. This is vanilla behavior, not a custom angle-dependent effect.
- The render callback submits the beam through Fabric's `COLLECT_SUBMITS` event so the native solid and translucent beam passes remain in Minecraft's normal render pipeline.

Build and in-game verification are still required. The source package has not been compiled in this environment.



## Notes from the 2026-11-03 recording

- Waypoint beams use Minecraft's public `BeaconRenderer.submitBeaconBeam(...)` method and vanilla texture, animation, core radius, glow radius, and alpha. When comparing to a real beacon, stand outside the beam column. Since Java 1.15 snapshot 19w41a, vanilla beacon beams are not visible from inside their own column; the recording creates the waypoint at the player's current X/Z, placing the camera inside that beam. This is expected vanilla behavior, not an angle-specific waypoint renderer failure. The mod deliberately does not draw a non-vanilla overlay to defeat it.
- Color-picker dragging now samples the live cursor from `Minecraft.mouseHandler.getScaledXPos/getScaledYPos` each frame while a left-button drag is active. It does not rely solely on accumulated or coalesced drag events. The drag is ended by the screen mouse-release callback; it deliberately does not poll `MouseHandler.isLeftPressed()`, because Minecraft 26.3 only updates that flag while no screen is open.
