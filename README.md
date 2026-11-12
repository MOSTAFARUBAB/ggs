# Waypoint Manager 1.5.9

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

## Version 1.5.2 UI fixes

- Reduced the Waypoint Manager UI typography and sidebar/header footprint for a denser layout.
- Corrected text width measurement for large headings so badges and long names no longer overlap.
- Fixed dashboard scroll-range calculation so it is based on the filtered waypoints loaded for the active page.
- Mouse-wheel scrolling now works across the main workspace, not only when the pointer is directly over a waypoint card.
- Editor and Settings wheel scrolling now work across the workspace while keeping the sidebar and pinned preview controls stable.
- Adjusted editor card position and panel heights to prevent the title from colliding with the form.

## Version 1.5.3 build fix
- Removed a duplicate `@Override` annotation on the waypoint editor scroll handler. This fixes the Java compiler error reported by the 1.5.2 build.


## Version 1.5.5 layout fixes
- Increased compact dashboard card height so coordinate tiles no longer collide with dimension labels or card actions.
- Made create/edit form, visibility card, color picker, Chroma row, recent swatches, and preview scale together on short GUI viewports.
- Hid the editor's always-visible vertical scrollbar while preserving wheel scrolling for edit-only content.
- Extended the content viewport almost to the bottom edge so the editor no longer leaves a large unused black strip.
- Added text texture width/height padding to prevent the final glyph from being clipped by font-metric rounding.
- Kept Minecraft's global GUI Scale setting under player control; the mod only adapts its own layout to the available GUI dimensions.

## Version 1.5.5 dashboard layout fixes
- Search field, sort control, view toggle, and their click targets now use one shared calculated layout, preventing the search field from painting under the toolbar buttons.
- Import/Export and search/sort rows have a clearer vertical gap.
- Dimension filters render as separate outlined pills with bounded label widths.
- Waypoint cards now show a visible colored pin beside the title, with the coordinate tiles and dimension footer laid out below it.
- Sidebar storage status copy wraps across two complete title lines instead of truncating the sentence.

## Version 1.5.6 visual fixes
- Replaced stepped, pixel-row rounded rectangles with cached supersampled antialiased shapes for buttons, cards, fields, toggles, and indicators.
- Replaced the hand-drawn sidebar mark with a high-resolution paper-plane brand asset matching the supplied logo reference.
- Increased the text rasterizer's measurement safety margin and based texture bounds on the exact high-resolution font metrics to prevent clipped glyph endings.
- Increased base UI text sizes slightly while preserving the compact graphite workspace layout.
- Dimension/type indicators now use round, color-coded Overworld, Nether, and End markers in filters and the editor selector.
- Small / Medium / Large label size now changes the actual live in-world label text scale, the label plate, and both editor/settings previews; the previous version changed mostly padding only.


## Version 1.5.7 beam and UI refinement
- Beacon beams now keep a constant slim width instead of widening with distance; both inner and outer radii have been reduced.
- Beam opacity now passes through the ARGB alpha channel, keeping the beam shell translucent instead of forcing it fully opaque.
- Reduced the custom workspace modifier by about 4% without changing Minecraft's global GUI Scale.
- Repositioned the sidebar emblem and wordmark to match the supplied Waypoint Manager reference lockup.
- Rebuilt the waypoint emblem as a clean 512px antialiased asset and increased the rounded-control supersampling level.
- Rounded button labels and dashboard/settings/edit descriptions are clipped to their available panel widths to prevent text collisions.


## 1.5.9 — Beam and brand refinement

- Narrowed the beacon beam outer shell further and preserved its native translucent glow pass.
- Reduced the custom workspace UI modifier slightly to 94%.
- Resized the Waypoint emblem to match the supplied compact lime-square logo lockup.
- Standardized buttons with antialiased 6px corners and a subtle border on primary actions.
- Fitted remaining long dashboard, editor, and settings labels to their panel widths to prevent text collisions.


## Final text spacing pass

The 1.5.9 source also increases dashboard title spacing, separates the settings preview copy into short fitted lines, shortens the create-form status note, and centres the 28px waypoint pin icon to match the supplied reference.
