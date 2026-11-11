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

    private WaypointUiChrome() {}

    /** High-density static contour artwork; avoids hundreds of tiny draw calls each frame. */
    static void drawContour(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean settingsAspect) {
        g.fill(x, y, x + w, y + h, FIELD);
        Identifier texture = settingsAspect ? CONTOUR_SETTINGS : CONTOUR_WIDE;
        g.blit(texture, x, y, x + w, y + h, 0.0f, 1.0f, 0.0f, 1.0f);
    }

    static int railWidth(int width, int height) {
        float proportion = height >= 880 ? 0.151f : 0.145f;
        return Math.max(138, Math.round(width * proportion));
    }
    static int topHeight(int height) { return Math.max(38, Math.min(50, Math.round(height * 0.055f))); }
    static int navY(int height) { return topHeight(height) + 124; }
    static int navRowY(int height, boolean settings) { return navY(height) + (settings ? 43 : 0); }

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

        // Wordmark tile and quiet brand lockup.
        round(g, 20, 20, 48, 48, GREEN, 9);
        // Crisp paper-plane mark, matching the small app tile in the reference.
        int mark = 0xFF182017;
        drawLine(g, 26, 29, 42, 25, mark);
        drawLine(g, 42, 25, 35, 43, mark);
        drawLine(g, 35, 43, 32, 35, mark);
        drawLine(g, 32, 35, 26, 29, mark);
        drawLine(g, 32, 35, 42, 25, mark);
        WaypointUiText.draw(g, font, "Waypoint", 56, 23, TEXT, false);
        WaypointUiText.drawCompact(g, font, "M A N A G E R", 56, 38, MUTED);

        // World selector.
        int worldY = top + 23;
        round(g, 20, worldY, rail - 16, worldY + 55, PANEL, 8);
        border(g, 20, worldY, rail - 16, worldY + 55, EDGE);
        WaypointUiText.draw(g, font, fit(font, worldName(), rail - 70), 30, worldY + 11, TEXT, false);
        WaypointUiText.drawCompact(g, font, "•  Java Edition · 26.3", 30, worldY + 33, MUTED);
        WaypointUiText.draw(g, font, "⌄", rail - 34, worldY + 16, MUTED, false);

        WaypointUiText.drawCompact(g, font, "W O R K S P A C E", 20, top + 111, QUIET);
        int ny = navY(height);
        drawNav(g, font, mx, my, 20, ny, rail - 40, "Waypoints", !settingsActive, false);
        drawNav(g, font, mx, my, 20, ny + 43, rail - 40, "Settings", settingsActive, true);

        // Local storage status card and current player row.
        int statusH = Math.max(100, Math.min(124, height / 7));
        int statusY = height - statusH - 72;
        round(g, 20, statusY, rail - 16, statusY + statusH, 0xFF0D0F11, 8);
        border(g, 20, statusY, rail - 16, statusY + statusH, EDGE);
        WaypointUiText.draw(g, font, "▱", 30, statusY + 13, MUTED, false);
        WaypointUiText.draw(g, font, fit(font, "Your world. Your waypoints.", rail - 56), 30, statusY + 34, TEXT, false);
        WaypointUiText.drawCompact(g, font, "Stored locally and ready", 30, statusY + 52, MUTED);
        WaypointUiText.drawCompact(g, font, "for your next adventure.", 30, statusY + 64, MUTED);
        g.fill(30, statusY + statusH - 18, 35, statusY + statusH - 13, GREEN);
        WaypointUiText.drawCompact(g, font, "All changes saved", 41, statusY + statusH - 19, MUTED);

        int profileY = height - 48;
        round(g, 20, profileY, 46, profileY + 26, 0xFF1B1F22, 7);
        String initials = initials(playerName());
        WaypointUiText.drawCompact(g, font, initials, 25, profileY + 7, TEXT);
        WaypointUiText.draw(g, font, fit(font, playerName(), rail - 88), 55, profileY + 2, TEXT, false);
        WaypointUiText.drawCompact(g, font, "Explorer", 55, profileY + 15, MUTED);
        WaypointUiText.draw(g, font, "···", rail - 31, profileY + 8, MUTED, false);

        // Top breadcrumb / local workspace status.
        drawWorldGlyph(g, rail + 31, Math.max(15, top / 2 - 6), MUTED);
        WaypointUiText.drawCompact(g, font, fit(font, worldName(), 128), rail + 52, top / 2 - 4, MUTED);
        WaypointUiText.drawCompact(g, font, "/", rail + 52 + Math.min(128, WaypointUiText.compactWidth(font, worldName())) + 10, top / 2 - 4, QUIET);
        int crumbX = rail + 52 + Math.min(128, WaypointUiText.compactWidth(font, worldName())) + 23;
        WaypointUiText.drawCompact(g, font, breadcrumb, crumbX, top / 2 - 4, TEXT);
        int dotX = width - 137;
        g.fill(dotX, top / 2 - 2, dotX + 5, top / 2 + 3, GREEN);
        WaypointUiText.drawCompact(g, font, "Local workspace", dotX + 11, top / 2 - 4, MUTED);
        round(g, width - 33, top / 2 - 7, width - 19, top / 2 + 7, TOP, 7);
        border(g, width - 33, top / 2 - 7, width - 19, top / 2 + 7, EDGE);
        WaypointUiText.drawCompact(g, font, "?", width - 29, top / 2 - 4, MUTED);
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
        if (active || inside(mx, my, x, y, w, 36)) round(g, x, y, x + w, y + 36, active ? 0xFF1C2023 : 0xFF171A1D, 7);
        int iconColor = active ? GREEN : MUTED;
        if (settings) {
            g.fill(x + 11, y + 11, x + 23, y + 12, iconColor);
            g.fill(x + 11, y + 17, x + 23, y + 18, iconColor);
            g.fill(x + 11, y + 23, x + 23, y + 24, iconColor);
            g.fill(x + 15, y + 9, x + 18, y + 14, iconColor);
            g.fill(x + 19, y + 15, x + 22, y + 20, iconColor);
        } else {
            round(g, x + 11, y + 10, x + 23, y + 25, iconColor, 6);
            g.fill(x + 15, y + 22, x + 19, y + 28, iconColor);
            round(g, x + 15, y + 13, x + 19, y + 18, RAIL, 3);
        }
        WaypointUiText.draw(g, font, label, x + 34, y + 11, active ? TEXT : MUTED, false);
        if (active && !settings) WaypointUiText.drawCompact(g, font, Integer.toString(WaypointsMod.menuWaypointCountAllDimensions()), x + w - 20, y + 12, MUTED);
    }

    static void round(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color, int radius) {
        int w = x2 - x1, h = y2 - y1;
        if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w / 2, h / 2));
        g.fill(x1 + r, y1, x2 - r, y2, color);
        g.fill(x1, y1 + r, x2, y2 - r, color);
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int inset = (int)Math.ceil(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            g.fill(x1 + inset, y1 + row, x2 - inset, y1 + row + 1, color);
            g.fill(x1 + inset, y2 - row - 1, x2 - inset, y2 - row, color);
        }
    }

    static void border(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        round(g, x, y, x + w, y + h, PANEL, 12);
        border(g, x, y, x + w, y + h, EDGE);
    }

    static void field(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean focused) {
        round(g, x, y, x + w, y + h, FIELD, 7);
        border(g, x, y, x + w, y + h, focused ? 0xFF58624E : EDGE);
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
        round(g, x, y, x + w, y + h, fill, 7);
        if (!primary) border(g, x, y, x + w, y + h, danger ? 0xFF67383D : EDGE);
        int c = primary ? 0xFF18200F : danger ? RED : TEXT;
        WaypointUiText.draw(g, font, label,
                x + (w - WaypointUiText.width(font, label)) / 2,
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
        String s = original;
        while (!s.isEmpty() && measure.applyAsInt(s) > maxWidth) s = s.substring(0, s.length() - 1);
        return s.equals(original) ? s : s + "…";
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "P";
        String[] p = name.trim().split("\\s+");
        return p.length > 1 ? (p[0].substring(0, 1) + p[1].substring(0, 1)).toUpperCase() : p[0].substring(0, Math.min(2, p[0].length())).toUpperCase();
    }
}
