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
