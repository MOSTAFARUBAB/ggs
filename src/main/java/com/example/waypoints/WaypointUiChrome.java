package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Shared graphite / lime Waypoint Manager frame matching the supplied reference screens. */
final class WaypointUiChrome {
    static final int BG = 0xFF090A0B;
    static final int RAIL = 0xFF101214;
    static final int TOP = 0xFF0B0D0F;
    static final int PANEL = 0xFF131619;
    static final int FIELD = 0xFF0D0F11;
    static final int EDGE = 0xFF2B3034;
    static final int TEXT = 0xFFF2F4F5;
    static final int MUTED = 0xFFA1A9AE;
    static final int QUIET = 0xFF6E777D;
    static final int GREEN = 0xFFB7F36B;
    static final int GREEN_DARK = 0xFF25361B;
    static final int RED = 0xFFFF777F;
    private static final Identifier CONTOUR_WIDE = Identifier.fromNamespaceAndPath(
            "simplewaypoints", "textures/gui/contour_wide.png");
    private static final Identifier CONTOUR_SETTINGS = Identifier.fromNamespaceAndPath(
            "simplewaypoints", "textures/gui/contour_settings.png");
    private static final Identifier BRAND_MARK = Identifier.fromNamespaceAndPath(
            "simplewaypoints", "textures/gui/brand_mark.png");

    private static final float UI_MODIFIER = 0.90f;

    private WaypointUiChrome() {}

    /** A small global reduction keeps the workspace airy without changing Minecraft's GUI scale. */
    static int ui(int value) { return Math.max(1, Math.round(value * UI_MODIFIER)); }

    /** High-density static contour artwork; avoids hundreds of tiny draw calls each frame. */
    static void drawContour(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean settingsAspect) {
        g.fill(x, y, x + w, y + h, FIELD);
        Identifier texture = settingsAspect ? CONTOUR_SETTINGS : CONTOUR_WIDE;
        g.blit(texture, x, y, x + w, y + h, 0.0f, 1.0f, 0.0f, 1.0f);
    }

    static int railWidth(int width, int height) {
        float proportion = height >= 880 ? 0.145f : 0.139f;
        return Math.max(ui(134), Math.round(width * proportion));
    }
    static int topHeight(int height) { return Math.max(ui(36), Math.min(ui(48), Math.round(height * 0.052f))); }
    static int navY(int height) { return topHeight(height) + ui(118); }
    static int navRowY(int height, boolean settings) { return navY(height) + (settings ? ui(41) : 0); }

    static String worldName() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.getCurrentServer() != null && mc.getCurrentServer().name != null && !mc.getCurrentServer().name.isBlank()) return mc.getCurrentServer().name;
            if (mc.getSingleplayerServer() != null) {
                String n = mc.getSingleplayerServer().getWorldData().getLevelName();
                if (n != null && !n.isBlank()) return n;
            }
        } catch (Throwable ignored) { }
        return "Current World";
    }

    static String playerName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getName() != null ? mc.player.getName().getString() : "Player";
    }

    static void drawFrame(GuiGraphicsExtractor g, Font font, int width, int height,
                          int mx, int my, boolean settingsActive, String breadcrumb) {
        int rail = railWidth(width, height);
        int top = topHeight(height);
        g.fill(0, 0, width, height, BG);
        g.fill(0, 0, rail, height, RAIL);
        g.fill(rail - 1, 0, rail, height, EDGE);
        g.fill(rail, 0, width, top, TOP);
        g.fill(rail, top - 1, width, top, EDGE);

        // Reference brand lockup: small lime rounded waypoint emblem at the rail edge,
        // with a two-line wordmark to its right (not a large text-only header).
        g.blit(BRAND_MARK, ui(10), ui(15), ui(28), ui(28), 0.0f, 1.0f, 0.0f, 1.0f);
        WaypointUiText.draw(g, font, fit(font, "Waypoint", Math.max(52, rail - ui(58))), ui(48), ui(15), TEXT, false);
        WaypointUiText.drawCompact(g, font, fit(font, "M A N A G E R", Math.max(48, rail - ui(58))), ui(48), ui(31), MUTED);

        // World selector.
        int worldY = top + ui(22);
        round(g, ui(18), worldY, rail - ui(14), worldY + ui(53), PANEL, ui(8));
        border(g, ui(18), worldY, rail - ui(14), worldY + ui(53), EDGE);
        WaypointUiText.draw(g, font, fit(font, worldName(), rail - ui(68)), ui(28), worldY + ui(10), TEXT, false);
        WaypointUiText.drawCompact(g, font, "•  Java Edition · 26.3", ui(28), worldY + ui(31), MUTED);
        WaypointUiText.draw(g, font, "⌄", rail - ui(31), worldY + ui(15), MUTED, false);

        WaypointUiText.drawCompact(g, font, "W O R K S P A C E", ui(18), top + ui(106), QUIET);
        int ny = navY(height);
        drawNav(g, font, mx, my, ui(18), ny, rail - ui(36), "Waypoints", !settingsActive, false);
        drawNav(g, font, mx, my, ui(18), ny + ui(41), rail - ui(36), "Settings", settingsActive, true);

        // Local storage status card and current player row.
        int statusH = Math.max(ui(124), Math.min(ui(144), height / 6));
        int statusY = height - statusH - ui(68);
        round(g, ui(18), statusY, rail - ui(14), statusY + statusH, 0xFF0D0F11, ui(8));
        border(g, ui(18), statusY, rail - ui(14), statusY + statusH, EDGE);
        WaypointUiText.draw(g, font, "▱", ui(28), statusY + ui(11), MUTED, false);
        // Two deliberate lines replace fit(...), which truncated the sentence.
        WaypointUiText.draw(g, font, fit(font, "Your world.", rail - ui(54)), ui(28), statusY + ui(31), TEXT, false);
        WaypointUiText.draw(g, font, fit(font, "Your waypoints.", rail - ui(54)), ui(28), statusY + ui(46), TEXT, false);
        WaypointUiText.drawCompact(g, font, fit(font, "Saved on device.", rail - ui(52)), ui(28), statusY + ui(66), MUTED);
        WaypointUiText.drawCompact(g, font, fit(font, "Ready when you are.", rail - ui(52)), ui(28), statusY + ui(81), MUTED);
        g.fill(ui(28), statusY + statusH - ui(17), ui(33), statusY + statusH - ui(12), GREEN);
        WaypointUiText.drawCompact(g, font, fit(font, "All changes saved", rail - ui(64)), ui(39), statusY + statusH - ui(18), MUTED);

        int profileY = height - ui(46);
        round(g, ui(18), profileY, ui(50), profileY + ui(25), 0xFF1B1F22, ui(7));
        String initials = initials(playerName());
        WaypointUiText.drawCompact(g, font, initials, ui(23), profileY + ui(6), TEXT);
        int profileTextX = ui(57);
        int profileTextWidth = Math.max(24, rail - ui(93));
        WaypointUiText.draw(g, font, fit(font, playerName(), profileTextWidth), profileTextX, profileY + ui(1), TEXT, false);
        WaypointUiText.drawCompact(g, font, "Explorer", profileTextX, profileY + ui(14), MUTED);
        WaypointUiText.draw(g, font, "···", rail - ui(29), profileY + ui(7), MUTED, false);

        // Top breadcrumb / local workspace status.
        drawWorldGlyph(g, rail + ui(29), Math.max(ui(14), top / 2 - ui(6)), MUTED);
        int crumbWorldX = rail + ui(48);
        String crumbWorld = fit(font, worldName(), ui(118));
        WaypointUiText.drawCompact(g, font, crumbWorld, crumbWorldX, top / 2 - ui(4), MUTED);
        int slashX = crumbWorldX + Math.min(ui(118), WaypointUiText.compactWidth(font, crumbWorld)) + ui(9);
        WaypointUiText.drawCompact(g, font, "/", slashX, top / 2 - ui(4), QUIET);
        int crumbX = slashX + ui(13);
        int dotX = width - ui(132);
        WaypointUiText.drawCompact(g, font, fit(font, breadcrumb, Math.max(ui(40), dotX - crumbX - ui(82))), crumbX, top / 2 - ui(4), TEXT);
        g.fill(dotX, top / 2 - 2, dotX + 5, top / 2 + 3, GREEN);
        WaypointUiText.drawCompact(g, font, "Local workspace", dotX + 11, top / 2 - 4, MUTED);
        round(g, width - ui(32), top / 2 - ui(7), width - ui(18), top / 2 + ui(7), TOP, ui(7));
        border(g, width - ui(32), top / 2 - ui(7), width - ui(18), top / 2 + ui(7), EDGE);
        WaypointUiText.drawCompact(g, font, "?", width - ui(28), top / 2 - ui(4), MUTED);
    }

    private static void drawLine(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1), sy = y1 < y2 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            g.fill(x1, y1, x1 + 1, y1 + 1, color);
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x1 += sx; }
            if (e2 <= dx) { err += dx; y1 += sy; }
        }
    }

    private static void drawWorldGlyph(GuiGraphicsExtractor g, int x, int y, int color) {
        border(g, x + 1, y, x + 10, y + 10, color);
        g.fill(x + 4, y + 2, x + 8, y + 3, color);
        g.fill(x + 3, y + 5, x + 7, y + 6, color);
    }

    private static void drawNav(GuiGraphicsExtractor g, Font font, int mx, int my, int x, int y, int w,
                                String label, boolean active, boolean settings) {
        int rowH = ui(34);
        if (active || inside(mx, my, x, y, w, rowH)) round(g, x, y, x + w, y + rowH, active ? 0xFF1C2023 : 0xFF171A1D, ui(7));
        int iconColor = active ? GREEN : MUTED;
        if (settings) {
            g.fill(x + ui(10), y + ui(10), x + ui(22), y + ui(11), iconColor);
            g.fill(x + ui(10), y + ui(16), x + ui(22), y + ui(17), iconColor);
            g.fill(x + ui(10), y + ui(22), x + ui(22), y + ui(23), iconColor);
            g.fill(x + ui(14), y + ui(8), x + ui(17), y + ui(13), iconColor);
            g.fill(x + ui(18), y + ui(14), x + ui(21), y + ui(19), iconColor);
        } else {
            round(g, x + ui(10), y + ui(9), x + ui(22), y + ui(24), iconColor, ui(6));
            g.fill(x + ui(14), y + ui(21), x + ui(18), y + ui(27), iconColor);
            round(g, x + ui(14), y + ui(12), x + ui(18), y + ui(17), RAIL, ui(3));
        }
        WaypointUiText.draw(g, font, label, x + ui(32), y + ui(10), active ? TEXT : MUTED, false);
        if (active && !settings) WaypointUiText.drawCompact(g, font, Integer.toString(WaypointsMod.menuWaypointCountAllDimensions()), x + w - ui(19), y + ui(11), MUTED);
    }

    static void round(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color, int radius) {
        int w = x2 - x1, h = y2 - y1;
        if (w <= 0 || h <= 0) return;
        WaypointUiShapes.draw(g, x1, y1, w, h, color, radius, 0x00000000, 0);
    }

    static void border(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        int w = x2 - x1, h = y2 - y1;
        if (w <= 0 || h <= 0) return;
        WaypointUiShapes.draw(g, x1, y1, w, h, 0x00000000, 7, color, 1);
    }

    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        WaypointUiShapes.draw(g, x, y, w, h, PANEL, 12, EDGE, 1);
    }

    static void field(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean focused) {
        WaypointUiShapes.draw(g, x, y, w, h, FIELD, 7, focused ? 0xFF58624E : EDGE, 1);
    }

    static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        round(g, x, y, x + 28, y + 16, on ? GREEN : 0xFF3B4146, 9);
        round(g, on ? x + 14 : x + 2, y + 2, on ? x + 26 : x + 14, y + 14,
                on ? 0xFF15200E : 0xFF9AA0A4, 7);
    }

    static void button(GuiGraphicsExtractor g, Font font, int mx, int my, int x, int y, int w, int h,
                       String label, boolean primary, boolean danger) {
        boolean hover = inside(mx, my, x, y, w, h);
        int fill = primary ? (hover ? 0xFFC6F88C : GREEN) : hover ? 0xFF1C2023 : 0xFF15181B;
        int outline = primary ? 0xFFA3DE58 : danger ? 0xFF67383D : EDGE;
        // Consistent compact radius and a fine one-pixel edge make controls read
        // like the reference instead of oversized, flat Minecraft buttons.
        WaypointUiShapes.draw(g, x, y, w, h, fill, ui(6), outline, 1);
        int c = primary ? 0xFF18200F : danger ? RED : TEXT;
        String shown = fit(font, label, Math.max(12, w - ui(16)));
        WaypointUiText.draw(g, font, shown,
                x + (w - WaypointUiText.width(font, shown)) / 2,
                y + (h - WaypointUiText.LINE_HEIGHT) / 2, c, false);
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    static String fit(Font font, String text, int maxWidth) {
        return fitMeasured(text, maxWidth, s -> WaypointUiText.width(font, s));
    }

    static String fitHeading(Font font, String text, int maxWidth) {
        return fitMeasured(text, maxWidth, s -> WaypointUiText.headingWidth(font, s));
    }

    private static String fitMeasured(String text, int maxWidth, java.util.function.ToIntFunction<String> measure) {
        String original = text == null ? "" : text;
        if (original.isEmpty() || maxWidth <= 0) return "";
        if (measure.applyAsInt(original) <= maxWidth) return original;
        String candidate = original;
        // Measure the final ellipsis as well as the retained text. The older
        // helper stopped when the plain prefix fit and then appended an ellipsis
        // without reserving its width, so strings could still spill into icons,
        // toggles, counts and neighbouring controls.
        while (!candidate.isEmpty() && measure.applyAsInt(candidate + "…") > maxWidth) {
            int end = candidate.offsetByCodePoints(candidate.length(), -1);
            candidate = candidate.substring(0, end);
        }
        String result = candidate + "…";
        return measure.applyAsInt(result) <= maxWidth ? result : "";
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "P";
        String[] p = name.trim().split("\\s+");
        return p.length > 1 ? (p[0].substring(0, 1) + p[1].substring(0, 1)).toUpperCase() : p[0].substring(0, Math.min(2, p[0].length())).toUpperCase();
    }
}
