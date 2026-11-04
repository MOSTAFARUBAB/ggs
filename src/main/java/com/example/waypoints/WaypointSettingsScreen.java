package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Preferences workspace matching the dark, lime-accented Waypoint Manager design. */
public final class WaypointSettingsScreen extends Screen {
    private static final int BG = 0xFF090A0C;
    private static final int RAIL = 0xFF101214;
    private static final int PANEL = 0xFF131619;
    private static final int PANEL_ALT = 0xFF0F1214;
    private static final int EDGE = 0xFF30373B;
    private static final int TEXT = 0xFFF2F5F5;
    private static final int MUTED = 0xFF9BA4A8;
    private static final int QUIET = 0xFF687278;
    private static final int ACCENT = 0xFFB7F36B;

    private record HitTarget(int x, int y, int width, int height, Runnable action) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
    private final java.util.List<HitTarget> hitTargets = new java.util.ArrayList<>();

    private int railWidth, contentX, contentRight, contentWidth, leftW;
    private int beamTrackX, beamTrackWidth, beamTrackY;
    private boolean draggingBeamOpacity;
    private EditBox distanceBox;

    public WaypointSettingsScreen() { super(Component.literal("Waypoint Settings")); }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BG);
    }

    @Override
    protected void init() {
        clearWidgets();
        hitTargets.clear();
        railWidth = width >= 800 ? 150 : 54;
        contentX = railWidth + 24;
        contentRight = width - 24;
        contentWidth = Math.max(220, contentRight - contentX);
        if (railWidth > 70) {
            button(10, 140, railWidth - 20, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
            // Settings is the active navigation item; no dummy click target is needed.
        } else {
            button(8, 104, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
            // Settings is the active navigation item; no dummy click target is needed.
        }

        leftW = contentWidth < 520
                ? contentWidth
                : Math.min(570, Math.max(330, (contentWidth - 18) * 2 / 3));
        int y = 92;
        // Waypoint label preferences.
        button(contentX + leftW - 42, y + 38, 28, 18, () -> toggleLabels());
        button(contentX + leftW - 42, y + 63, 28, 18, () -> toggleDistances());
        button(contentX + leftW - 42, y + 88, 28, 18, () -> toggleCoordinates());
        int sizeW = Math.max(42, (leftW - 170) / 3);
        int sizeStart = contentX + leftW - (sizeW * 3 + 28);
        for (int i = 0; i < 3; i++) {
            final int size = i;
            button(sizeStart + i * (sizeW + 4), y + 106, sizeW, 20, () -> {
                WaypointsMod.markerSizeMode = size;
                save();
            });
        }

        // Beacon beam preferences.
        int beamY = 232;
        button(contentX + leftW - 42, beamY + 36, 28, 18, () -> toggleBeams());
        button(contentX + leftW - 42, beamY + 62, 28, 18, () -> toggleBeamColor());
        beamTrackX = contentX + 116;
        beamTrackWidth = Math.max(60, leftW - 140);
        beamTrackY = beamY + 91;

        // Display preferences + editable maximum distance. 0 means unlimited.
        int displayY = 349;
        button(contentX + leftW - 42, displayY + 36, 28, 18, () -> {
            WaypointsMod.currentDimensionOnly = !WaypointsMod.currentDimensionOnly;
            save();
        });
        button(contentX + leftW - 42, displayY + 61, 28, 18, () -> {
            WaypointsMod.hideHudWhenMenuOpen = !WaypointsMod.hideHudWhenMenuOpen;
            save();
        });
        distanceBox = new EditBox(font, contentX + leftW - 110, displayY + 84, 92, 22, Component.literal("Maximum display distance"));
        distanceBox.setMaxLength(9);
        distanceBox.setValue(Integer.toString(WaypointsMod.maxDisplayDistance));
        distanceBox.setBordered(false);
        distanceBox.setResponder(value -> {
            try {
                WaypointsMod.maxDisplayDistance = Math.max(0, Integer.parseInt(value));
                save();
            } catch (NumberFormatException ignored) { }
        });
        addRenderableWidget(distanceBox);

        button(contentX, height - 32, 105, 25, this::resetDefaults);
        button(contentRight - 92, height - 32, 92, 25, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && event.x() >= beamTrackX - 5 && event.x() <= beamTrackX + beamTrackWidth + 5
                && event.y() >= beamTrackY - 5 && event.y() <= beamTrackY + 12) {
            draggingBeamOpacity = true;
            setDragging(true);
            updateBeamOpacity(event.x());
            return true;
        }
        if (event.button() == 1) {
            for (int i = hitTargets.size() - 1; i >= 0; i--) {
                HitTarget target = hitTargets.get(i);
                if (target.contains(event.x(), event.y())) {
                    target.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingBeamOpacity) {
            double mouseX = Minecraft.getInstance().mouseHandler.getScaledXPos(Minecraft.getInstance().getWindow());
            updateBeamOpacity(mouseX);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingBeamOpacity) {
            updateBeamOpacity(event.x());
            draggingBeamOpacity = false;
            setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
    }

    private void updateBeamOpacity(double mouseX) {
        double ratio = Math.max(0.0, Math.min(1.0, (mouseX - beamTrackX) / Math.max(1.0, beamTrackWidth)));
        WaypointsMod.beamOpacity = (int) Math.round(ratio * 100.0);
        save();
    }

    private void button(int x, int y, int w, int h, Runnable action) {
        // Invisible pointer target; custom controls are rendered separately.
        hitTargets.add(new HitTarget(x, y, w, h, action));
    }
    private void toggleLabels() { WaypointsMod.showWaypointLabels = !WaypointsMod.showWaypointLabels; save(); }
    private void toggleDistances() { WaypointsMod.showWaypointDistances = !WaypointsMod.showWaypointDistances; save(); }
    private void toggleCoordinates() { WaypointsMod.showWaypointCoordinates = !WaypointsMod.showWaypointCoordinates; save(); }
    private void toggleBeams() { WaypointsMod.showWaypointBeams = !WaypointsMod.showWaypointBeams; save(); }
    private void toggleBeamColor() { WaypointsMod.matchWaypointColor = !WaypointsMod.matchWaypointColor; save(); }
    private void save() { WaypointsMod.savePreferences(); }

    private void resetDefaults() {
        WaypointsMod.showWaypointLabels = true;
        WaypointsMod.showWaypointDistances = true;
        WaypointsMod.showWaypointCoordinates = false;
        WaypointsMod.markerSizeMode = 1;
        WaypointsMod.showWaypointBeams = true;
        WaypointsMod.matchWaypointColor = true;
        WaypointsMod.beamOpacity = 75;
        WaypointsMod.currentDimensionOnly = true;
        WaypointsMod.hideHudWhenMenuOpen = true;
        WaypointsMod.maxDisplayDistance = 0;
        save();
        Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        drawSidebar(g, mx, my);
        g.fill(railWidth, 0, width, 39, PANEL_ALT);
        g.fill(railWidth, 38, width, 39, EDGE);
        g.text(font, "◇  " + worldTitle() + "   /   Settings", railWidth + 24, 15, MUTED, false);
        g.fill(width - 118, 17, width - 113, 22, ACCENT);
        g.text(font, "Local workspace", width - 106, 15, MUTED, false);
        g.text(font, "Your world. Your view.", contentX, 57, TEXT, true);
        g.text(font, "Make waypoints work the way you explore.", contentX, 76, MUTED, false);
        int leftW = this.leftW;
        int rightX = contentX + leftW + 18;
        int rightW = Math.max(0, contentRight - rightX);
        int y = 92;
        card(g, contentX, y, leftW, 130);
        g.text(font, "⚑  Waypoint labels", contentX + 16, y + 14, TEXT, true);
        settingRow(g, mx, contentX + 16, y + 36, leftW - 32, "Show waypoint labels", "See waypoint names while exploring your world.", WaypointsMod.showWaypointLabels);
        settingRow(g, mx, contentX + 16, y + 61, leftW - 32, "Show distance", "Display your distance to each waypoint in blocks.", WaypointsMod.showWaypointDistances);
        settingRow(g, mx, contentX + 16, y + 86, leftW - 32, "Show coordinates", "Include X, Y and Z beneath each waypoint name.", WaypointsMod.showWaypointCoordinates);
        g.text(font, "Label size", contentX + 16, y + 114, MUTED, false);
        String[] sizes = {"Small", "Medium", "Large"};
        int sizeW = Math.max(42, (leftW - 170) / 3);
        int sizeX = contentX + leftW - (sizeW * 3 + 28);
        for (int i = 0; i < 3; i++) {
            boolean active = WaypointsMod.markerSizeMode == i;
            rounded(g, sizeX, y + 106, sizeX + sizeW, y + 126, active ? 0xFF29361F : PANEL_ALT, 5);
            if (active) border(g, sizeX, y + 106, sizeX + sizeW, y + 126, 0xFF759D48);
            g.text(font, sizes[i], sizeX + (sizeW - font.width(sizes[i])) / 2, y + 112, active ? ACCENT : MUTED, false);
            sizeX += sizeW + 4;
        }

        int beamY = 232;
        card(g, contentX, beamY, leftW, 107);
        g.text(font, "◉  Beacon beams", contentX + 16, beamY + 14, TEXT, true);
        settingRow(g, mx, contentX + 16, beamY + 36, leftW - 32, "Show beacon beams", "Guide your way with a vertical beam at each waypoint.", WaypointsMod.showWaypointBeams);
        settingRow(g, mx, contentX + 16, beamY + 62, leftW - 32, "Match waypoint color", "Use each waypoint’s color for its beacon beam.", WaypointsMod.matchWaypointColor);
        g.text(font, "Beam opacity", contentX + 16, beamY + 91, MUTED, false);
        int trackX = contentX + 116;
        int trackW = Math.max(60, leftW - 140);
        rounded(g, trackX, beamY + 94, trackX + trackW, beamY + 99, EDGE, 3);
        rounded(g, trackX, beamY + 94, trackX + trackW * WaypointsMod.beamOpacity / 100, beamY + 99, ACCENT, 3);
        int knob = trackX + trackW * WaypointsMod.beamOpacity / 100;
        rounded(g, knob - 4, beamY + 91, knob + 4, beamY + 102, ACCENT, 5);
        g.text(font, WaypointsMod.beamOpacity + "%", contentX + leftW - 44, beamY + 89, MUTED, false);

        int displayY = 349;
        card(g, contentX, displayY, leftW, Math.max(108, height - displayY - 42));
        g.text(font, "▣  Display preferences", contentX + 16, displayY + 14, TEXT, true);
        settingRow(g, mx, contentX + 16, displayY + 36, leftW - 32, "Current dimension only", "Hide waypoints from dimensions you’re not in.", WaypointsMod.currentDimensionOnly);
        settingRow(g, mx, contentX + 16, displayY + 61, leftW - 32, "Hide HUD when menu is open", "Keep the view clean while browsing menus.", WaypointsMod.hideHudWhenMenuOpen);
        g.text(font, "Maximum display distance", contentX + 16, displayY + 91, MUTED, false);
        rounded(g, contentX + leftW - 110, displayY + 84, contentX + leftW - 18, displayY + 106, PANEL_ALT, 5);
        border(g, contentX + leftW - 110, displayY + 84, contentX + leftW - 18, displayY + 106, EDGE);
        String maxDistanceText = distanceBox == null ? Integer.toString(WaypointsMod.maxDisplayDistance) : distanceBox.getValue();
        int distanceTextX = contentX + leftW - 102;
        g.text(font, maxDistanceText, distanceTextX, displayY + 91, TEXT, false);
        if (distanceBox != null && distanceBox.isFocused() && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caretX = distanceTextX + font.width(maxDistanceText);
            g.fill(caretX, displayY + 87, caretX + 1, displayY + 103, TEXT);
        }

        // Right preview and explanation column. Hide it when the GUI is too narrow
        // instead of letting the panel escape the screen bounds.
        if (rightW >= 160) {
            g.text(font, "IN-GAME PREVIEW", rightX, y - 14, QUIET, false);
            drawPreview(g, rightX, y, rightW, 180);
            card(g, rightX, y + 193, rightW, 105);
            g.text(font, "ⓘ", rightX + 14, y + 207, MUTED, false);
            g.text(font, "A starting point, not a limit.", rightX + 14, y + 229, TEXT, false);
            g.text(font, fit("Preferences save automatically and apply to all waypoints in this world.", rightW - 28), rightX + 14, y + 248, MUTED, false);
            g.text(font, "Preferences save automatically", rightX + 14, y + 277, ACCENT, false);
        }
        buttonLabel(g, mx, my, contentX, height - 32, 105, 25, "Reset defaults", false);
        buttonLabel(g, mx, my, contentRight - 92, height - 32, 92, 25, "Done", false);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, railWidth, height, RAIL); g.fill(railWidth - 1, 0, railWidth, height, EDGE);
        rounded(g, 14, 15, 43, 44, ACCENT, 8); g.text(font, "W", 23, 25, 0xFF101410, true);
        if (railWidth > 70) {
            g.text(font, "Waypoint", 51, 19, TEXT, false); g.text(font, "M A N A G E R", 51, 31, MUTED, false);
            rounded(g, 14, 62, railWidth - 14, 105, PANEL, 8);
            g.text(font, worldTitle(), 24, 73, TEXT, true); g.fill(24, 91, 28, 95, ACCENT);
            g.text(font, "Java Edition · 26.3", 34, 89, MUTED, false);
            g.text(font, "WORKSPACE", 16, 125, QUIET, false);
            nav(g, mx, my, 10, 140, railWidth - 20, "◎", "Waypoints", false);
            nav(g, mx, my, 10, 179, railWidth - 20, "☷", "Settings", true);
            rounded(g, 14, height - 109, railWidth - 14, height - 18, PANEL_ALT, 8);
            g.text(font, "Your world. Your waypoints.", 23, height - 94, TEXT, false);
            g.text(font, "Stored locally, ready for", 23, height - 76, MUTED, false);
            g.text(font, "your next adventure.", 23, height - 63, MUTED, false);
            g.fill(23, height - 43, 28, height - 38, ACCENT); g.text(font, "All changes saved", 34, height - 44, MUTED, false);
        } else {
            nav(g, mx, my, 8, 104, 38, "◎", "", false); nav(g, mx, my, 8, 150, 38, "☷", "", true);
        }
    }
    private void nav(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String icon, String label, boolean active) {
        if (active || inside(mx, my, x, y, w, 32)) rounded(g, x, y, x + w, y + 32, active ? 0xFF202629 : PANEL, 7);
        g.text(font, icon, x + 10, y + 11, active ? ACCENT : MUTED, false);
        if (!label.isEmpty()) g.text(font, label, x + 30, y + 11, active ? TEXT : MUTED, false);
    }
    private void settingRow(GuiGraphicsExtractor g, int mx, int x, int y, int w, String title, String desc, boolean enabled) {
        g.text(font, title, x, y, TEXT, false); g.text(font, fit(desc, w - 65), x, y + 13, MUTED, false);
        int sx = x + w - 26; toggle(g, sx, y + 1, enabled);
    }
    private void toggle(GuiGraphicsExtractor g, int x, int y, boolean enabled) {
        rounded(g, x, y, x + 26, y + 15, enabled ? 0xFF45652A : 0xFF3B4247, 8);
        rounded(g, enabled ? x + 13 : x + 2, y + 2, enabled ? x + 24 : x + 13, y + 13, enabled ? ACCENT : 0xFF9BA4A8, 6);
    }
    private void card(GuiGraphicsExtractor g, int x, int y, int w, int h) { rounded(g, x, y, x + w, y + h, PANEL, 9); border(g, x, y, x + w, y + h, EDGE); }
    private void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        int centerX = mc.player == null ? 0 : mc.player.blockPosition().getX();
        int centerZ = mc.player == null ? 0 : mc.player.blockPosition().getZ();
        SurfaceMapPreview.draw(g, font, x, y, w, h, centerX, centerZ,
                WaypointsMod.currentDimension(), ACCENT);
    }
    private void buttonLabel(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label, boolean primary) {
        boolean hover = inside(mx, my, x, y, w, h);
        rounded(g, x, y, x + w, y + h, primary ? ACCENT : hover ? 0xFF242B2F : PANEL, 6);
        if (!primary) border(g, x, y, x + w, y + h, EDGE);
        g.text(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, primary ? BG : TEXT, false);
    }
    private String fit(String value, int max) { String s = value; while (!s.isEmpty() && font.width(s) > max) s = s.substring(0, s.length() - 1); return s.equals(value) ? s : s + "…"; }
    private String worldTitle() {
        Minecraft mc = Minecraft.getInstance();
        try { if (mc.getCurrentServer() != null) return mc.getCurrentServer().name;
            if (mc.getSingleplayerServer() != null) return mc.getSingleplayerServer().getWorldData().getLevelName(); }
        catch (Throwable ignored) { }
        return "Survival Realm";
    }
    private static void border(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color) {
        g.fill(x, y, x2, y + 1, color); g.fill(x, y2 - 1, x2, y2, color); g.fill(x, y, x + 1, y2, color); g.fill(x2 - 1, y, x2, y2, color);
    }
    private static void rounded(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color, int radius) {
        int w = x2 - x, h = y2 - y; if (w <= 0 || h <= 0) return; int r = Math.min(radius, Math.min(w / 2, h / 2));
        g.fill(x + r, y, x2 - r, y2, color); g.fill(x, y + r, x2, y2 - r, color);
        for (int row = 0; row < r; row++) { int inset = (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - Math.pow(r - row - .5, 2))));
            g.fill(x + inset, y + row, x2 - inset, y + row + 1, color); g.fill(x + inset, y2 - row - 1, x2 - inset, y2 - row, color); }
    }
    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
}
