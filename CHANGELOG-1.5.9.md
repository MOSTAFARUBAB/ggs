# Waypoint Manager 1.5.9

## Text and layout correction

- Reduced shared interface text by one step and reduced the common UI modifier to 90%.
- Matched text width calculations to the actual antialiased glyph texture sizes, with a small safety margin for labels and button captions.
- Increased spacing between the waypoint name, coordinates, helper copy, and dimension selector in the create/edit form.
- Moved the settings label-size selector below the third row description so it has its own row.
- Kept the label-size choices connected to the live in-world label renderer.
- Reworked the waypoint-list pin to match the reference: a thin colored outline pin inside a dark rounded tile.

Target: Minecraft 26.3, Java 25, Fabric Loom 1.17.21.

## Final spacing pass

- Increased the gap between the dashboard subtitle and statistics to prevent text touching the cards.
- Split the settings preview description into three complete, individually fitted lines.
- Shortened the create-waypoint local-storage note so it fits without a cramped ellipsis.
- Matched the waypoint-card pin tile to the 28px reference and centred its rounded teardrop outline and inner hole.
