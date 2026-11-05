package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Preferences workspace matching the dark, lime-accented Waypoint Manager design. */
public final class WaypointSettingsScreen extends Screen {
    private static final int BG = 0xFF090A0C;
    private static final int RAIL = 0xFF101214;
    private static final int PANEL = 0xFF141618;
    private static final int PANEL_ALT = 0xFF101214;
    private static final int EDGE = 0xFF292D30;
    private static final int TEXT = 0xFFF2F5F5;
    private static final int MUTED = 0xFF9BA4A8;
    private static final int QUIET = 0xFF687278;
    private static final int ACCENT = 0xFFA9D879;

    private record HitTarget(int x, int y, int width, int height, Runnable action) {
        boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
    private final java.util.List<HitTarget> hitTargets = new java.util.ArrayList<>();
    private final java.util.List<HitTarget> scrollHitTargets = new java.util.ArrayList<>();
    private int scrollOffset;
    private int maxScroll;
    private int scrollTop;
    private int scrollBottom;

    private int railWidth, contentX, contentRight, contentWidth, leftW;
    private int headingY, cardsY, labelCardHeight, beamY, beamCardHeight, displayY, displayCardHeight;
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
        scrollHitTargets.clear();
        railWidth = width >= 800 ? Math.max(150, Math.min(190, Math.round(width * 0.16f))) : 54;
        int sideInset = width >= 800 ? 32 : 12;
        contentX = railWidth + sideInset;
        contentRight = width - sideInset;
        contentWidth = Math.max(220, contentRight - contentX);
        headingY = Math.max(58, Math.round(height * 0.095f));
        cardsY = Math.max(112, Math.round(height * 0.172f));
        leftW = contentWidth < 520 ? contentWidth : Math.min(570, Math.max(330, Math.round(contentWidth * 0.68f)));
        labelCardHeight = Math.max(210, Math.round(height * 0.278f));
        beamY = cardsY + labelCardHeight + 14;
        beamCardHeight = Math.max(190, Math.round(height * 0.23f));
        displayY = beamY + beamCardHeight + 15;
        displayCardHeight = 178;
        scrollTop = cardsY;
        scrollBottom = height - 48;
        maxScroll = Math.max(0, displayY + displayCardHeight - scrollBottom);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        if (railWidth > 70) {
            button(10, 153, railWidth - 20, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
        } else {
            button(8, 104, 38, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
        }

        // Waypoint label preferences.
        int toggleX = contentX + leftW - 42;
        scrollButton(toggleX, cardsY + 55, 28, 18, this::toggleLabels);
        scrollButton(toggleX, cardsY + 100, 28, 18, this::toggleDistances);
        scrollButton(toggleX, cardsY + 144, 28, 18, this::toggleCoordinates);
        int sizeW = Math.max(42, (leftW - 190) / 3);
        int sizeStart = contentX + leftW - (sizeW * 3 + 28);
        for (int i = 0; i < 3; i++) {
            final int size = i;
            scrollButton(sizeStart + i * (sizeW + 4), cardsY + 177, sizeW, 26, () -> {
                WaypointsMod.markerSizeMode = size;
                save();
            });
        }

        // Beacon beam preferences.
        scrollButton(toggleX, beamY + 55, 28, 18, this::toggleBeams);
        scrollButton(toggleX, beamY + 100, 28, 18, this::toggleBeamColor);
        beamTrackX = contentX + 116;
        beamTrackWidth = Math.max(60, leftW - 140);
        beamTrackY = beamY + 171;

        // Display preferences + editable maximum distance. 0 means unlimited.
        scrollButton(toggleX, displayY + 55, 28, 18, () -> {
            WaypointsMod.currentDimensionOnly = !WaypointsMod.currentDimensionOnly;
            save();
        });
        scrollButton(toggleX, displayY + 100, 28, 18, () -> {
            WaypointsMod.hideHudWhenMenuOpen = !WaypointsMod.hideHudWhenMenuOpen;
            save();
        });
        distanceBox = new EditBox(font, contentX + leftW - 110, displayY + 140 - scrollOffset, 58, 23, Component.literal("Maximum display distance"));
        distanceBox.setMaxLength(9);
        distanceBox.setValue(Integer.toString(WaypointsMod.maxDisplayDistance));
        distanceBox.setBordered(false);
        distanceBox.setTextColor(0xFFF2F5F5);
        distanceBox.setResponder(value -> {
            try {
                WaypointsMod.maxDisplayDistance = Math.max(0, Integer.parseInt(value));
                save();
            } catch (NumberFormatException ignored) { }
        });
        addRenderableWidget(distanceBox);

        button(contentX, height - 36, 105, 25, this::resetDefaults);
        button(contentRight - 92, height - 36, 92, 25, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && event.x() >= beamTrackX - 5 && event.x() <= beamTrackX + beamTrackWidth + 5
                && event.y() >= beamTrackY - scrollOffset - 5 && event.y() <= beamTrackY - scrollOffset + 12) {
            draggingBeamOpacity = true;
            setDragging(true);
            updateBeamOpacity(event.x());
            return true;
        }
        if (event.button() == 1) {
            if (event.y() >= scrollTop && event.y() <= scrollBottom
                    && event.x() >= contentX && event.x() <= contentX + leftW) {
            for (int i = scrollHitTargets.size() - 1; i >= 0; i--) {
                HitTarget target = scrollHitTargets.get(i);
                if (target.contains(event.x(), event.y() + scrollOffset)) {
                    target.action().run();
                    return true;
                }
            }
            }
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < contentX || mouseX > contentX + leftW || mouseY < scrollTop || mouseY > scrollBottom) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int next = Math.max(0, Math.min(maxScroll, scrollOffset - (int)Math.round(scrollY * 28.0)));
        if (next == scrollOffset) return false;
        scrollOffset = next;
        if (distanceBox != null) {
            distanceBox.setY(displayY + 140 - scrollOffset);
            if (distanceBox.getY() + distanceBox.getHeight() <= scrollTop || distanceBox.getY() >= scrollBottom) {
                distanceBox.setFocused(false);
            }
        }
        return true;
    }

    private void updateBeamOpacity(double mouseX) {
        double ratio = Math.max(0.0, Math.min(1.0, (mouseX - beamTrackX) / Math.max(1.0, beamTrackWidth)));
        WaypointsMod.beamOpacity = (int) Math.round(ratio * 100.0);
        save();
    }

    private void button(int x, int y, int w, int h, Runnable action) {
        hitTargets.add(new HitTarget(x, y, w, h, action));
    }
    private void scrollButton(int x, int y, int w, int h, Runnable action) {
        scrollHitTargets.add(new HitTarget(x, y, w, h, action));
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
        drawSidebar(g, mx, my);
        int topBarHeight = Math.max(42, Math.round(height * 0.055f));
        g.fill(railWidth, 0, width, topBarHeight, PANEL_ALT);
        g.fill(railWidth, topBarHeight - 1, width, topBarHeight, EDGE);
        g.text(font, "" + fit(worldTitle(), 120) + "   /   Settings", railWidth + 24, 15, MUTED, false);
        g.fill(width - 132, 17, width - 127, 22, ACCENT);
        g.text(font, "Local workspace", width - 120, 15, MUTED, false);
        g.text(font, "Your world. Your view.", contentX, headingY, TEXT, false);
        g.text(font, "Make waypoints work the way you explore.", contentX, headingY + 31, MUTED, false);
        g.text(font, "Changes saved", contentRight - 100, headingY + 16, ACCENT, false);

        int rightX = contentX + leftW + 18;
        int rightW = Math.max(0, contentRight - rightX);
        g.enableScissor(contentX, scrollTop, contentX + leftW, scrollBottom);
        g.pose().pushMatrix();
        g.pose().translate(0.0f, -scrollOffset);
        int y = cardsY;

        // Waypoint labels card: four well-spaced rows, no overlapping descriptions.
        card(g, contentX, y, leftW, labelCardHeight);
        g.text(font, "Waypoint labels", contentX + 16, y + 18, TEXT, false);
        settingRow(g, mx, contentX + 16, y + 55, leftW - 32, "Show waypoint labels", "See waypoint names while exploring your world.", WaypointsMod.showWaypointLabels);
        settingRow(g, mx, contentX + 16, y + 100, leftW - 32, "Show distance", "Display your distance to each waypoint in blocks.", WaypointsMod.showWaypointDistances);
        settingRow(g, mx, contentX + 16, y + 144, leftW - 32, "Show coordinates", "Include X, Y and Z beneath each waypoint name.", WaypointsMod.showWaypointCoordinates);
        g.text(font, "Label size", contentX + 16, y + 188, MUTED, false);
        String[] sizes = {"Small", "Medium", "Large"};
        int sizeW = Math.max(42, (leftW - 190) / 3);
        int sizeX = contentX + leftW - (sizeW * 3 + 28);
        for (int i = 0; i < 3; i++) {
            boolean active = WaypointsMod.markerSizeMode == i;
            rounded(g, sizeX, y + 177, sizeX + sizeW, y + 203, active ? 0xFF29361F : PANEL_ALT, 5);
            if (active) border(g, sizeX, y + 177, sizeX + sizeW, y + 203, 0xFF759D48);
            g.text(font, sizes[i], sizeX + (sizeW - font.width(sizes[i])) / 2, y + 186, active ? ACCENT : MUTED, false);
            sizeX += sizeW + 4;
        }

        // Beacon beams card.
        card(g, contentX, beamY, leftW, beamCardHeight);
        g.text(font, "Beacon beams", contentX + 16, beamY + 18, TEXT, false);
        settingRow(g, mx, contentX + 16, beamY + 55, leftW - 32, "Show beacon beams", "Guide your way with a vertical beam at each waypoint.", WaypointsMod.showWaypointBeams);
        settingRow(g, mx, contentX + 16, beamY + 100, leftW - 32, "Match waypoint color", "Use each waypoint's color for its beacon beam.", WaypointsMod.matchWaypointColor);
        g.text(font, "Beam opacity", contentX + 16, beamY + 151, MUTED, false);
        rounded(g, beamTrackX, beamTrackY, beamTrackX + beamTrackWidth, beamTrackY + 5, EDGE, 3);
        rounded(g, beamTrackX, beamTrackY, beamTrackX + beamTrackWidth * WaypointsMod.beamOpacity / 100, beamTrackY + 5, ACCENT, 3);
        int knob = beamTrackX + beamTrackWidth * WaypointsMod.beamOpacity / 100;
        rounded(g, knob - 4, beamTrackY - 3, knob + 4, beamTrackY + 8, ACCENT, 5);
        g.text(font, WaypointsMod.beamOpacity + "%", contentX + leftW - 44, beamY + 149, MUTED, false);

        // Display preferences card.
        card(g, contentX, displayY, leftW, displayCardHeight);
        g.text(font, "Display preferences", contentX + 16, displayY + 18, TEXT, false);
        settingRow(g, mx, contentX + 16, displayY + 55, leftW - 32, "Current dimension only", "Hide waypoints from dimensions you're not in.", WaypointsMod.currentDimensionOnly);
        settingRow(g, mx, contentX + 16, displayY + 100, leftW - 32, "Hide HUD when menu is open", "Keep your view clean while browsing menus.", WaypointsMod.hideHudWhenMenuOpen);
        g.text(font, "Maximum display distance", contentX + 16, displayY + 148, MUTED, false);
        rounded(g, contentX + leftW - 110, displayY + 138, contentX + leftW - 18, displayY + 165, PANEL_ALT, 5);
        border(g, contentX + leftW - 110, displayY + 138, contentX + leftW - 18, displayY + 165, EDGE);
        g.text(font, "blocks", contentX + leftW - 48, displayY + 148, QUIET, false);

        g.pose().popMatrix();
        g.disableScissor();

        // Right preview and compact help card stay fixed while the settings column scrolls.
        if (rightW >= 160) {
            g.text(font, "IN-GAME PREVIEW", rightX, y + 2, QUIET, false);
            drawPreview(g, rightX, y + 22, rightW, Math.min(180, Math.round(height * 0.205f)));
            int infoY = y + 22 + Math.min(180, Math.round(height * 0.205f)) + 53;
            card(g, rightX, infoY, rightW, 130);
            g.text(font, "", rightX + 14, infoY + 14, MUTED, false);
            g.text(font, "A starting point, not a limit.", rightX + 14, infoY + 36, TEXT, false);
            g.text(font, fit("These are your world defaults. You can customize", rightW - 28), rightX + 14, infoY + 56, MUTED, false);
            g.text(font, fit("labels and beams for individual waypoints when", rightW - 28), rightX + 14, infoY + 69, MUTED, false);
            g.text(font, fit("creating or editing them.", rightW - 28), rightX + 14, infoY + 82, MUTED, false);
            g.text(font, "Preferences save automatically", rightX + 14, infoY + 108, ACCENT, false);
        }
        buttonLabel(g, mx, my, contentX, height - 36, 105, 25, "Reset defaults", false);
        buttonLabel(g, mx, my, contentRight - 92, height - 36, 92, 25, "Done", false);
        if (maxScroll > 0) {
            int trackX = contentX + leftW - 4;
            int trackTop = scrollTop + 3;
            int trackHeight = scrollBottom - scrollTop - 6;
            int thumbHeight = Math.max(24, trackHeight * trackHeight / (trackHeight + maxScroll));
            int thumbY = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
            rounded(g, trackX, trackTop, trackX + 2, trackTop + trackHeight, EDGE, 1);
            rounded(g, trackX, thumbY, trackX + 2, thumbY + thumbHeight, MUTED, 1);
        }

        // Keep the native distance input inside the same scroll viewport as its card.
        g.enableScissor(contentX, scrollTop, contentX + leftW, scrollBottom);
        super.extractRenderState(g, mx, my, delta);
        g.disableScissor();
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, railWidth, height, RAIL); g.fill(railWidth - 1, 0, railWidth, height, EDGE);
        rounded(g, 14, 15, 43, 44, ACCENT, 8); g.text(font, "W", 23, 25, 0xFF101410, false);
        if (railWidth > 70) {
            g.text(font, "Waypoint", 51, 19, TEXT, false); g.text(font, "M A N A G E R", 51, 31, MUTED, false);
            rounded(g, 14, 63, railWidth - 14, 108, PANEL, 8);
            g.text(font, fit(worldTitle(), railWidth - 48), 24, 74, TEXT, false); g.fill(24, 94, 28, 98, ACCENT);
            g.text(font, "Java Edition · 26.3", 34, 92, MUTED, false);
            g.text(font, "WORKSPACE", 16, 135, QUIET, false);
            nav(g, mx, my, 10, 153, railWidth - 20, "•", "Waypoints", false);
            nav(g, mx, my, 10, 194, railWidth - 20, "=", "Settings", true);
            rounded(g, 14, height - 109, railWidth - 14, height - 18, PANEL_ALT, 8);
            g.text(font, fit("Your world. Your waypoints.", railWidth - 36), 23, height - 94, TEXT, false);
            g.text(font, fit("Stored locally, ready for", railWidth - 36), 23, height - 76, MUTED, false);
            g.text(font, fit("your next adventure.", railWidth - 36), 23, height - 63, MUTED, false);
            g.fill(23, height - 43, 28, height - 38, ACCENT); g.text(font, "All changes saved", 34, height - 44, MUTED, false);
        } else {
            nav(g, mx, my, 8, 104, 38, "•", "", false); nav(g, mx, my, 8, 150, 38, "=", "", true);
        }
    }
    private void nav(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String icon, String label, boolean active) {
        if (active || inside(mx, my, x, y, w, 32)) rounded(g, x, y, x + w, y + 32, active ? 0xFF202629 : PANEL, 7);
        g.text(font, icon, x + 10, y + 11, active ? ACCENT : MUTED, false);
        if (!label.isEmpty()) g.text(font, label, x + 30, y + 11, active ? TEXT : MUTED, false);
    }
    private void settingRow(GuiGraphicsExtractor g, int mx, int x, int y, int w, String title, String desc, boolean enabled) {
        g.text(font, fit(title, w - 54), x, y, TEXT, false);
        int textWidth = Math.max(70, w - 58);
        var lines = font.split(Component.literal(desc), textWidth);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.text(font, lines.get(i), x, y + 13 + i * 9, MUTED, false);
        }
        int sx = x + w - 26;
        toggle(g, sx, y + 1, enabled);
    }
    private void toggle(GuiGraphicsExtractor g, int x, int y, boolean enabled) {
        rounded(g, x, y, x + 26, y + 15, enabled ? 0xFF45652A : 0xFF3B4247, 8);
        rounded(g, enabled ? x + 13 : x + 2, y + 2, enabled ? x + 24 : x + 13, y + 13, enabled ? ACCENT : 0xFF9BA4A8, 6);
    }
    private void card(GuiGraphicsExtractor g, int x, int y, int w, int h) { rounded(g, x, y, x + w, y + h, PANEL, 9); border(g, x, y, x + w, y + h, EDGE); }
    private void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        List<Waypoint> saved = WaypointsMod.menuWaypoints();
        String currentDimension = WaypointsMod.normalizeDimension(WaypointsMod.currentDimension());
        Waypoint featured = saved.stream()
                .filter(waypoint -> {
                    String d = WaypointsMod.normalizeDimension(waypoint.dimension());
                    return d.equals(currentDimension) || d.equals("all");
                })
                .findFirst()
                .orElse(saved.isEmpty() ? null : saved.get(0));
        int centerX = featured != null ? featured.x() : mc.player == null ? 0 : mc.player.blockPosition().getX();
        int centerZ = featured != null ? featured.z() : mc.player == null ? 0 : mc.player.blockPosition().getZ();
        int centerY = featured != null ? featured.y() : mc.player == null ? 64 : mc.player.blockPosition().getY();
        int markerColor = featured != null ? 0xFF000000 | WaypointsMod.resolveColor(featured) : ACCENT;
        String label = featured != null ? featured.name() : "Your position";
        String details = centerX + ", " + centerY + ", " + centerZ;
        SurfaceMapPreview.draw(g, font, x, y, w, h, centerX, centerZ,
                featured != null ? featured.dimension() : WaypointsMod.currentDimension(),
                markerColor, label, details);
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
