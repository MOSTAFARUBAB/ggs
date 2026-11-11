package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Settings workspace matching the supplied Waypoint Manager settings reference. */
public final class WaypointSettingsScreen extends Screen {
    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double px, double py) { return WaypointUiChrome.inside(px, py, x, y, w, h); }
    }
    private final List<Hit> hits = new ArrayList<>();
    private int rail, top, left, right, contentW, leftW, rightX, rightW;
    private int labelsY, labelsH, beamY, beamH, displayY, displayH;
    private int beamTrackX, beamTrackW, beamTrackY;
    private boolean draggingBeam;
    private boolean compactLayout() { return height < 760; }
    private int labelRow1() { return compactLayout() ? 43 : 53; }
    private int labelRow2() { return compactLayout() ? 77 : 98; }
    private int labelRow3() { return compactLayout() ? 111 : 143; }
    private int beamRow1() { return compactLayout() ? 43 : 53; }
    private int beamRow2() { return compactLayout() ? 76 : 96; }
    private int displayRow1() { return compactLayout() ? 43 : 53; }
    private int displayRow2() { return compactLayout() ? 76 : 96; }
    private EditBox distanceBox;

    public WaypointSettingsScreen() { super(Component.literal("Settings")); }

    public void extractBlurredBackground(GuiGraphicsExtractor g) { }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, WaypointUiChrome.BG);
    }

    @Override protected void init() {
        clearWidgets(); hits.clear();
        rail = WaypointUiChrome.railWidth(width, height);
        top = WaypointUiChrome.topHeight(height);
        left = rail + 30;
        right = width - 32;
        contentW = Math.max(420, right - left);
        leftW = Math.round(contentW * 0.676f);
        rightX = left + leftW + 18;
        rightW = right - rightX;

        labelsY = top + 92;
        labelsH = compactLayout() ? 178 : Math.max(190, Math.min(236, Math.round(height * 0.275f)));
        beamY = labelsY + labelsH + 14;
        beamH = compactLayout() ? 150 : Math.max(168, Math.min(194, Math.round(height * 0.226f)));
        displayY = beamY + beamH + 14;
        displayH = compactLayout() ? 150 : Math.max(162, Math.min(190, height - displayY - 48));

        int ny = WaypointUiChrome.navY(height);
        hits.add(new Hit(20, ny, rail - 40, 36, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0))));
        hits.add(new Hit(20, ny + 43, rail - 40, 36, () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen())));

        int tx = left + leftW - 46;
        hits.add(new Hit(tx, labelsY + labelRow1() + 2, 34, 22, () -> update(() -> WaypointsMod.showWaypointLabels = !WaypointsMod.showWaypointLabels)));
        hits.add(new Hit(tx, labelsY + labelRow2() + 2, 34, 22, () -> update(() -> WaypointsMod.showWaypointDistances = !WaypointsMod.showWaypointDistances)));
        hits.add(new Hit(tx, labelsY + labelRow3() + 2, 34, 22, () -> update(() -> WaypointsMod.showWaypointCoordinates = !WaypointsMod.showWaypointCoordinates)));
        int segW = Math.max(40, Math.min(54, (leftW - 160) / 3));
        int segX = left + leftW - (segW * 3 + 8);
        for (int i = 0; i < 3; i++) {
            final int mode = i;
            hits.add(new Hit(segX + i * (segW + 2), labelsY + labelsH - 42, segW, 28,
                    () -> update(() -> WaypointsMod.markerSizeMode = mode)));
        }

        hits.add(new Hit(tx, beamY + beamRow1() + 2, 34, 22, () -> update(() -> WaypointsMod.showWaypointBeams = !WaypointsMod.showWaypointBeams)));
        hits.add(new Hit(tx, beamY + beamRow2() + 2, 34, 22, () -> update(() -> WaypointsMod.matchWaypointColor = !WaypointsMod.matchWaypointColor)));
        beamTrackX = left + 18;
        beamTrackW = leftW - 36;
        beamTrackY = beamY + beamH - (compactLayout() ? 20 : 26);
        hits.add(new Hit(left + 12, beamTrackY - 10, leftW - 24, 28, () -> updateBeamOpacity(Minecraft.getInstance().mouseHandler.getScaledXPos(Minecraft.getInstance().getWindow()))));

        int dx = left + leftW - 46;
        hits.add(new Hit(dx, displayY + displayRow1() + 2, 34, 22, () -> update(() -> WaypointsMod.currentDimensionOnly = !WaypointsMod.currentDimensionOnly)));
        hits.add(new Hit(dx, displayY + displayRow2() + 2, 34, 22, () -> update(() -> WaypointsMod.hideHudWhenMenuOpen = !WaypointsMod.hideHudWhenMenuOpen)));

        distanceBox = new EditBox(font, left + leftW - 107, displayY + displayH - (compactLayout() ? 46 : 51), 84, 28, Component.literal("Maximum distance"));
        distanceBox.setMaxLength(7); distanceBox.setBordered(false); distanceBox.setTextColor(0x00000000);
        distanceBox.setValue(Integer.toString(WaypointsMod.maxDisplayDistance));
        distanceBox.setResponder(v -> {
            String digits = v.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                WaypointsMod.maxDisplayDistance = 0;
                save();
                return;
            }
            try {
                WaypointsMod.maxDisplayDistance = Math.max(0, Math.min(100000, Integer.parseInt(digits)));
                save();
            } catch (NumberFormatException ignored) { }
        });
        addRenderableWidget(distanceBox);
        hits.add(new Hit(left + leftW - 89, height - 37, 78, 28, this::resetDefaults));
    }

    private void update(Runnable action) { action.run(); save(); }
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
        WaypointsMod.maxDisplayDistance = 2000;
        save();
        Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen());
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && WaypointUiChrome.inside(event.x(), event.y(), beamTrackX - 8, beamTrackY - 12, beamTrackW + 16, 30)) {
            draggingBeam = true; setDragging(true); updateBeamOpacity(event.x()); return true;
        }
        if (event.button() == 1) {
            for (int i = hits.size() - 1; i >= 0; i--) if (hits.get(i).contains(event.x(), event.y())) { hits.get(i).action().run(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingBeam) {
            double mx = Minecraft.getInstance().mouseHandler.getScaledXPos(Minecraft.getInstance().getWindow());
            updateBeamOpacity(mx); return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingBeam) { updateBeamOpacity(event.x()); draggingBeam = false; setDragging(false); return true; }
        return super.mouseReleased(event);
    }

    private void updateBeamOpacity(double mouseX) {
        double t = Math.max(0, Math.min(1, (mouseX - beamTrackX) / (double)Math.max(1, beamTrackW)));
        WaypointsMod.beamOpacity = (int)Math.round(t * 100);
        save();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        WaypointUiChrome.drawFrame(g, font, width, height, mx, my, true, "Settings");
        WaypointUiText.drawHeading(g, font, "Your world. Your view.", left, top + 32, WaypointUiChrome.TEXT);
        WaypointUiText.draw(g, font, "Make waypoints work the way you explore.", left, top + 63, WaypointUiChrome.MUTED, false);
        WaypointUiText.drawCompact(g, font, "✓  Changes saved", right - 95, top + 45, WaypointUiChrome.MUTED);

        // Left column: waypoint labels.
        WaypointUiChrome.panel(g, left, labelsY, leftW, labelsH);
        WaypointUiText.draw(g, font, "⚑  Waypoint labels", left + 18, labelsY + 20, WaypointUiChrome.TEXT, false);
        settingRowAdaptive(g, left + 18, labelsY + labelRow1(), leftW - 36, "Show waypoint labels", "See waypoint names while exploring your world.", WaypointsMod.showWaypointLabels);
        settingRowAdaptive(g, left + 18, labelsY + labelRow2(), leftW - 36, "Show distance", "Display your distance to each waypoint in blocks.", WaypointsMod.showWaypointDistances);
        settingRowAdaptive(g, left + 18, labelsY + labelRow3(), leftW - 36, "Show coordinates", "Include X, Y and Z beneath each waypoint name.", WaypointsMod.showWaypointCoordinates);
        WaypointUiText.drawCompact(g, font, "Label size", left + 18, labelsY + labelsH - 33, WaypointUiChrome.TEXT);
        int segW = Math.max(40, Math.min(54, (leftW - 160) / 3));
        int segX = left + leftW - (segW * 3 + 8);
        String[] sizes = {"Small", "Medium", "Large"};
        for (int i = 0; i < 3; i++) {
            int sx = segX + i * (segW + 2);
            boolean active = WaypointsMod.markerSizeMode == i;
            WaypointUiChrome.round(g, sx, labelsY + labelsH - 43, sx + segW, labelsY + labelsH - 15,
                    active ? 0xFF252A2E : WaypointUiChrome.FIELD, 5);
            if (active) WaypointUiChrome.border(g, sx, labelsY + labelsH - 43, sx + segW, labelsY + labelsH - 15, WaypointUiChrome.EDGE);
            WaypointUiText.drawCompact(g, font, sizes[i], sx + (segW - WaypointUiText.compactWidth(font, sizes[i])) / 2,
                    labelsY + labelsH - 34, active ? WaypointUiChrome.TEXT : WaypointUiChrome.MUTED);
        }

        // Beacon beam settings.
        WaypointUiChrome.panel(g, left, beamY, leftW, beamH);
        WaypointUiText.draw(g, font, "♧  Beacon beams", left + 18, beamY + 20, WaypointUiChrome.TEXT, false);
        settingRowAdaptive(g, left + 18, beamY + beamRow1(), leftW - 36, "Show beacon beams", "Guide your way with a vertical beam at each waypoint.", WaypointsMod.showWaypointBeams);
        settingRowAdaptive(g, left + 18, beamY + beamRow2(), leftW - 36, "Match waypoint color", "Use each waypoint’s color for its beacon beam.", WaypointsMod.matchWaypointColor);
        int beamLabelY = beamY + beamH - (compactLayout() ? 38 : 44);
        WaypointUiText.drawCompact(g, font, "Beam opacity", left + 18, beamLabelY, WaypointUiChrome.TEXT);
        WaypointUiText.drawCompact(g, font, WaypointsMod.beamOpacity + "%", left + leftW - 42, beamLabelY, WaypointUiChrome.MUTED);
        WaypointUiChrome.round(g, beamTrackX, beamTrackY, beamTrackX + beamTrackW, beamTrackY + 4, 0xFF33393D, 3);
        int fillW = beamTrackW * WaypointsMod.beamOpacity / 100;
        WaypointUiChrome.round(g, beamTrackX, beamTrackY, beamTrackX + fillW, beamTrackY + 4, WaypointUiChrome.GREEN, 3);
        WaypointUiChrome.round(g, beamTrackX + fillW - 5, beamTrackY - 4, beamTrackX + fillW + 5, beamTrackY + 9, WaypointUiChrome.GREEN, 6);

        // Display preferences.
        WaypointUiChrome.panel(g, left, displayY, leftW, displayH);
        WaypointUiText.draw(g, font, "▣  Display preferences", left + 18, displayY + 20, WaypointUiChrome.TEXT, false);
        settingRowAdaptive(g, left + 18, displayY + displayRow1(), leftW - 36, "Current dimension only", "Hide waypoints from dimensions you’re not in.", WaypointsMod.currentDimensionOnly);
        settingRowAdaptive(g, left + 18, displayY + displayRow2(), leftW - 36, "Hide HUD when menu is open", "Keep your view clean while browsing menus.", WaypointsMod.hideHudWhenMenuOpen);
        int distanceRowY = displayY + displayH - (compactLayout() ? 38 : 48);
        WaypointUiText.draw(g, font, "Maximum display distance", left + 18, distanceRowY, WaypointUiChrome.TEXT, false);
        String unit = distanceBox != null && "0".equals(distanceBox.getValue().trim()) ? "unlimited" : "blocks";
        int unitX = left + leftW - 138 - WaypointUiText.compactWidth(font, unit);
        WaypointUiText.drawCompact(g, font, unit, unitX, distanceRowY + 8, WaypointUiChrome.QUIET);
        int distanceFieldY = displayY + displayH - (compactLayout() ? 50 : 55);
        WaypointUiChrome.field(g, left + leftW - 130, distanceFieldY, 108, 31, distanceBox != null && distanceBox.isFocused());

        // Right column: live preview and help card.
        int previewY = top + 118;
        WaypointUiText.drawCompact(g, font, "IN-GAME PREVIEW", rightX, top + 99, WaypointUiChrome.QUIET);
        drawPreview(g, rightX, previewY, rightW, 170);
        WaypointUiText.drawCompact(g, font, "A clear marker, right where you need it. Your color is used for labels", rightX, previewY + 183, WaypointUiChrome.QUIET);
        WaypointUiText.drawCompact(g, font, "and beacon beams.", rightX, previewY + 196, WaypointUiChrome.QUIET);
        int noteY = previewY + 221;
        int noteH = Math.min(133, height - noteY - 30);
        if (noteH > 90) {
            WaypointUiChrome.panel(g, rightX, noteY, rightW, noteH);
            WaypointUiText.draw(g, font, "ⓘ", rightX + 16, noteY + 14, WaypointUiChrome.MUTED, false);
            WaypointUiText.draw(g, font, "A starting point, not a limit.", rightX + 16, noteY + 39, WaypointUiChrome.TEXT, false);
            WaypointUiText.drawCompact(g, font, "These are your world defaults. You can", rightX + 16, noteY + 63, WaypointUiChrome.MUTED);
            WaypointUiText.drawCompact(g, font, "customize labels and beams for individual", rightX + 16, noteY + 77, WaypointUiChrome.MUTED);
            WaypointUiText.drawCompact(g, font, "waypoints when creating or editing them.", rightX + 16, noteY + 91, WaypointUiChrome.MUTED);
            WaypointUiText.drawCompact(g, font, "✓  Preferences save automatically", rightX + 16, noteY + noteH - 18, WaypointUiChrome.QUIET);
        }
        WaypointUiChrome.button(g, font, mx, my, left + leftW - 89, height - 39, 78, 28, "Reset defaults", false, false);

        super.extractRenderState(g, mx, my, delta);
        if (distanceBox != null) WaypointUiText.drawInput(g, font, distanceBox,
                left + leftW - 119, displayY + displayH - (compactLayout() ? 39 : 44), 82);
    }

    private void settingRowAdaptive(GuiGraphicsExtractor g, int x, int y, int w, String label, String description, boolean enabled) {
        WaypointUiText.draw(g, font, label, x, y, WaypointUiChrome.TEXT, false);
        WaypointUiText.drawCompact(g, font, description, x, y + (compactLayout() ? 15 : 18), WaypointUiChrome.MUTED);
        WaypointUiChrome.toggle(g, x + w - 28, y + 5, enabled);
    }

    private void drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        WaypointUiChrome.panel(g, x, y, w, h);
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        WaypointUiChrome.drawContour(g, x + 1, y + 1, w - 2, h - 2, true);

        List<Waypoint> saved = WaypointsMod.menuWaypointsAllDimensions();
        Waypoint sample = saved.isEmpty() ? null : saved.get(0);
        int color = sample == null ? WaypointUiChrome.GREEN : WaypointsMod.resolveColor(sample);
        int cx = x + w / 2, cy = y + h / 2 - 5;
        if (WaypointsMod.showWaypointBeams) g.fill(cx, y + 1, cx + 1, cy - 12, color | 0xFF000000);
        WaypointUiChrome.round(g, cx - 16, cy - 15, cx + 16, cy + 17, 0xFF111715, 8);
        WaypointUiChrome.border(g, cx - 16, cy - 15, cx + 16, cy + 17, color | 0xFF000000);
        WaypointUiText.draw(g, font, "⌖", cx - 6, cy - 5, color | 0xFF000000, false);
        if (WaypointsMod.showWaypointLabels) {
            String name = sample == null ? "No waypoints yet" : WaypointUiChrome.fit(font, sample.name(), w - 30);
            WaypointUiText.draw(g, font, name, cx - WaypointUiText.width(font, name) / 2, cy + 30, color | 0xFF000000, false);
            if (WaypointsMod.showWaypointDistances && sample != null) {
                Minecraft mc = Minecraft.getInstance();
                long blocks = mc.player == null ? 0L : Math.round(Math.sqrt(
                        Math.pow(mc.player.getX() - (sample.x() + 0.5), 2)
                                + Math.pow(mc.player.getY() - sample.y(), 2)
                                + Math.pow(mc.player.getZ() - (sample.z() + 0.5), 2)));
                String distance = blocks + "m";
                WaypointUiText.drawCompact(g, font, distance,
                        cx - WaypointUiText.compactWidth(font, distance) / 2, cy + 47, WaypointUiChrome.MUTED);
            }
        }
        g.disableScissor();
    }
}
