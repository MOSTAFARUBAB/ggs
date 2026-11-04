# Smooth world-space waypoint label

The active label renderer is `beam/Beam3D.java`. Labels are submitted through Minecraft's world-text renderer, not drawn as a 2D HUD overlay.

- **Outline:** one complete waypoint-color rectangle with a dark inset plate. The shared rectangle geometry keeps all four corners connected.
- **Size:** the text remains about 13 screen pixels high per line at every distance. It no longer grows from 13 px to 34 px as the waypoint gets farther away, which made distant labels overwhelm the scene.
- **Plate:** horizontal and vertical padding can vary by the label-size mode without scaling the text itself.
- **Position:** the label floats 2.5 blocks above the waypoint and is offset slightly toward the camera so it does not sit inside its own beam.
- **Depth:** a clear view uses normal scene depth testing. When terrain blocks the waypoint, the label uses see-through rendering so it can still guide the player back.
- **Name length:** long waypoint names are shortened with an ellipsis at about 110 px so the plate cannot grow without limit.
