# Smooth floating label (connected outline, constant size)

Only WaypointsMod.java changed (the label code). Menus, beam, ring, colors and world separation are untouched.

- Outline: 1 px line in the waypoint color, closed all the way round (corners step diagonally). Drawn at whole
  pixels with no scaling, so it can no longer break into pieces. Checked with a pixel test for all three sizes.
- Size: constant. The label no longer grows or shrinks with distance (the matrix-scaling code is gone).
  "Logo" button: Small / Medium / Large now change the label's padding and height, not a zoom.
  Long names are cut at about 110 px so a label can't get huge.
- Float and smooth: the label hovers 2.5 blocks above the waypoint and bobs gently (about 1.6 px, slow).
  Labels fade in over a quarter second, and when two labels would touch, one eases to a free spot instead of popping.
- Collisions: the nearest waypoint is placed first, so close labels win. A label that still has no free spot is hidden.
  Labels near the screen edge are clipped by the screen instead of disappearing.
