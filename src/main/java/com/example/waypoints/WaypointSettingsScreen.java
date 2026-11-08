package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class WaypointSettingsScreen extends Screen {
    private static final int BG = 0xFF0B0C0E;
    private static final int RAIL = 0xFF101113;
    private static final int PANEL = 0xFF131518;
    private static final int PANEL_ALT = 0xFF0F1113;
    private static final int EDGE = 0xFF292D31;
    private static final int TEXT = 0xFFF2F5F5;
    private static final int MUTED = 0xFFA3ABB0;
    private static final int QUIET = 0xFF687278;
    private static final int ACCENT = 0xFFB7F36B;

    private int railWidth = 190, contentX, contentRight, contentWidth, leftW;
    private EditBox distanceBox;

    private record HitTarget(int x, int y, int width, int height, Runnable action) {
        boolean contains(double px, double py) { return px >= x && px < x + width && py >= y && py < y + height; }
    }
    private final List<HitTarget> hitTargets = new ArrayList<>();

    public WaypointSettingsScreen() { super(Component.literal("Waypoint Settings")); }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, BG);
    }

    @Override
    protected void init() {
        clearWidgets();
        hitTargets.clear();
        contentX = railWidth + 35;
        contentRight = width - 35;
        contentWidth = contentRight - contentX;
        leftW = Math.min(650, contentWidth - 320);

        // Sidebar navigation
        click(20, 153, 150, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointScreen(0)));
        click(20, 194, 150, 32, () -> Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()));

        distanceBox = new EditBox(font, contentX + leftW - 100, 480, 80, 20, Component.literal("Distance"));
        distanceBox.setMaxLength(8);
        distanceBox.setValue("2,000");
        distanceBox.setBordered(false);
        distanceBox.setTextColor(0x00000000);
        addRenderableWidget(distanceBox);

        click(contentX + leftW - 120, height - 50, 120, 36, this::resetDefaults);
    }

    private void click(int x, int y, int w, int h, Runnable action) {
        hitTargets.add(new HitTarget(x, y, w, h, action));
    }

    private void resetDefaults() {
        WaypointSettingsScreen.open();
    }

    static void open() { Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen()); }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1) {
            for (var t : hitTargets) {
                if (t.contains(event.x(), event.y())) { t.action().run(); return true; }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        drawSidebar(g, mx, my);
        g.fill(railWidth, 0, width, 48, BG);
        g.fill(railWidth, 47, width, 48, EDGE);
        WaypointUiText.draw(g, font, "Survival Realm  /  Settings", contentX, 17, MUTED, false);
        rounded(g, contentRight - 130, 14, contentRight, 38, 0xFF151B14, 6);
        WaypointUiText.draw(g, font, "Changes saved", contentRight - 114, 19, ACCENT, false);

        WaypointUiText.drawTitle(g, font, "Your world. Your view.", contentX, 60, TEXT);
        WaypointUiText.draw(g, font, "Make waypoints work the way you explore.", contentX, 90, MUTED, false);

        // Section 1: Waypoint labels
        rounded(g, contentX, 130, contentX + leftW, 310, PANEL, 10);
        border(g, contentX, 130, contentX + leftW, 310, EDGE);
        WaypointUiText.draw(g, font, "Waypoint labels", contentX + 18, 148, TEXT, false);

        // Section 2: Beacon beams
        rounded(g, contentX, 330, contentX + leftW, 510, PANEL, 10);
        border(g, contentX, 330, contentX + leftW, 510, EDGE);
        WaypointUiText.draw(g, font, "Beacon beams", contentX + 18, 348, TEXT, false);

        // Reset button
        actionButton(g, mx, my, contentX + leftW - 120, height - 50, 120, 36, "Reset defaults", false);

        super.extractRenderState(g, mx, my, delta);
        WaypointUiText.drawInput(g, font, distanceBox, contentX + leftW - 90, 484, 60);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, railWidth, height, RAIL);
        g.fill(railWidth - 1, 0, railWidth, height, EDGE);
        rounded(g, 20, 15, 47, 42, ACCENT, 7);
        WaypointUiText.draw(g, font, "Waypoint", 56, 19, TEXT, false);
        WaypointUiText.draw(g, font, "M A N A G E R", 56, 31, MUTED, false);

        int footerBottom = height - 70;
        int footerTop = footerBottom - 82;
        rounded(g, 15, footerTop, railWidth - 15, footerBottom, PANEL_ALT, 8);
        WaypointUiText.draw(g, font, "Your world. Your waypoints.", 25, footerTop + 12, TEXT, false);
        WaypointUiText.draw(g, font, "Stored locally, ready for", 25, footerTop + 28, MUTED, false);
        WaypointUiText.draw(g, font, "your next adventure.", 25, footerTop + 42, MUTED, false);
        g.fill(25, footerBottom - 18, 29, footerBottom - 14, ACCENT);
        WaypointUiText.draw(g, font, "All changes saved", 35, footerBottom - 20, MUTED, false);

        int profileTop = footerBottom + 10;
        rounded(g, 15, profileTop, railWidth - 15, profileTop + 40, 0xFF1B1E21, 8);
        WaypointUiText.draw(g, font, "AL", 25, profileTop + 12, TEXT, false);
        WaypointUiText.draw(g, font, "Alex", 56, profileTop + 8, TEXT, false);
        WaypointUiText.draw(g, font, "Explorer", 56, profileTop + 22, MUTED, false);
    }

    private void actionButton(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, int h, String label, boolean primary) {
        boolean hover = inside(mx, my, x, y, w, h);
        rounded(g, x, y, x + w, y + h, primary ? (hover ? 0xFFBDE39A : ACCENT) : (hover ? 0xFF242B2F : PANEL), 7);
        if (!primary) border(g, x, y, x + w, y + h, EDGE);
        WaypointUiText.draw(g, font, label, x + (w - WaypointUiText.width(font, label)) / 2, y + (h - 8) / 2, primary ? 0xFF101410 : TEXT, false);
    }

    private static void border(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color) {
        g.fill(x, y, x2, y + 1, color); g.fill(x, y2 - 1, x2, y2, color);
        g.fill(x, y, x + 1, y2, color); g.fill(x2 - 1, y, x2, y2, color);
    }
    private static void rounded(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int color, int r) {
        g.fill(x + r, y, x2 - r, y2, color); g.fill(x, y + r, x2, y2 - r, color);
    }
    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
